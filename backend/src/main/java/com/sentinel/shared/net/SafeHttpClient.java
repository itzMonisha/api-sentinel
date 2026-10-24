package com.sentinel.shared.net;

import com.sentinel.shared.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Zero-trust HTTP client executing outbound requests with strict SSRF defense,
 * hard byte quotas, anti-DNS rebinding pre-checks, redirect validation, and header sanitization.
 */
@Component
public class SafeHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SafeHttpClient.class);

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "apikey", "secret"
    );

    private static final int MAX_REQUEST_BODY_BYTES = 2 * 1024 * 1024; // 2 MB

    private final TargetValidator targetValidator;
    private final int defaultConnectTimeoutMs;
    private final int defaultReadTimeoutMs;
    private final int defaultMaxResponseBytes;

    public SafeHttpClient(
            TargetValidator targetValidator,
            @Value("${api-sentinel.outbound.connect-timeout-ms:5000}") int defaultConnectTimeoutMs,
            @Value("${api-sentinel.outbound.read-timeout-ms:10000}") int defaultReadTimeoutMs,
            @Value("${api-sentinel.outbound.max-response-bytes:5242880}") int defaultMaxResponseBytes) {
        this.targetValidator = targetValidator;
        this.defaultConnectTimeoutMs = defaultConnectTimeoutMs;
        this.defaultReadTimeoutMs = defaultReadTimeoutMs;
        this.defaultMaxResponseBytes = defaultMaxResponseBytes;
    }

    /**
     * Executes an HTTP request against a validated target.
     *
     * @param request the request snapshot
     * @return the response snapshot
     */
    public HttpResponseSnapshot execute(HttpRequestSnapshot request) {
        String currentUrl = buildFullUrl(request.getUrl(), request.getQueryParams());
        int hops = 0;
        int maxHops = request.getMaxRedirectHops();

        while (true) {
            long startTime = System.currentTimeMillis();

            // Validate target host and DNS resolution against SSRF policy
            targetValidator.validateAndResolve(currentUrl);

            HttpURLConnection connection = null;
            try {
                URL url = URI.create(currentUrl).toURL();
                connection = (HttpURLConnection) url.openConnection();

                int connectTimeout = request.getConnectTimeoutMs() != null
                        ? request.getConnectTimeoutMs() : defaultConnectTimeoutMs;
                int readTimeout = request.getReadTimeoutMs() != null
                        ? request.getReadTimeoutMs() : defaultReadTimeoutMs;

                connection.setConnectTimeout(connectTimeout);
                connection.setReadTimeout(readTimeout);
                connection.setInstanceFollowRedirects(false); // redirects handled explicitly with re-validation
                connection.setRequestMethod(request.getHttpMethod());
                connection.setUseCaches(false);

                // Set headers
                if (request.getHeaders() != null) {
                    for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            connection.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }
                }

                // Send request body if applicable
                boolean hasBody = request.getBody() != null && !request.getBody().isEmpty();
                boolean methodAllowsBody = "POST".equalsIgnoreCase(request.getHttpMethod())
                        || "PUT".equalsIgnoreCase(request.getHttpMethod())
                        || "PATCH".equalsIgnoreCase(request.getHttpMethod());

                if (hasBody && methodAllowsBody) {
                    byte[] bodyBytes = request.getBody().getBytes(StandardCharsets.UTF_8);
                    if (bodyBytes.length > MAX_REQUEST_BODY_BYTES) {
                        throw new ApiException("Request body size exceeds maximum allowed 2MB",
                                HttpStatus.BAD_REQUEST, "REQUEST_SIZE_EXCEEDED");
                    }
                    connection.setDoOutput(true);
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(bodyBytes);
                        os.flush();
                    }
                }

                int statusCode = connection.getResponseCode();
                long durationMs = System.currentTimeMillis() - startTime;

                // Handle Redirects if enabled
                if (isRedirect(statusCode) && request.isFollowRedirects()) {
                    if (hops >= maxHops) {
                        return HttpResponseSnapshot.failureWithStatus(
                                statusCode, "Maximum redirect limit (" + maxHops + ") exceeded", durationMs);
                    }

                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        return HttpResponseSnapshot.failureWithStatus(
                                statusCode, "Redirect missing Location header", durationMs);
                    }

                    // Resolve relative redirect URL
                    URI redirectUri = URI.create(currentUrl).resolve(location.trim());
                    currentUrl = redirectUri.toString();
                    hops++;
                    log.info("Following validated redirect hop {} to {}", hops, currentUrl);
                    continue; // Loop and re-validate new target
                }

                // Extract response headers
                Map<String, List<String>> responseHeaders = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
                    if (entry.getKey() != null) {
                        responseHeaders.put(entry.getKey(), entry.getValue());
                    }
                }

                // Read response body with byte cap
                InputStream is = null;
                try {
                    is = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
                } catch (Exception ignored) {
                }

                String responseBody = "";
                boolean sizeLimitExceeded = false;
                if (is != null) {
                    try (InputStream input = is; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                        byte[] data = new byte[8192];
                        int nRead;
                        int totalRead = 0;
                        while ((nRead = input.read(data, 0, data.length)) != -1) {
                            if (totalRead + nRead > defaultMaxResponseBytes) {
                                int allowed = defaultMaxResponseBytes - totalRead;
                                if (allowed > 0) {
                                    buffer.write(data, 0, allowed);
                                }
                                sizeLimitExceeded = true;
                                break;
                            }
                            buffer.write(data, 0, nRead);
                            totalRead += nRead;
                        }
                        responseBody = buffer.toString(StandardCharsets.UTF_8);
                    }
                }

                return new HttpResponseSnapshot(
                        statusCode,
                        responseHeaders,
                        responseBody,
                        durationMs,
                        sizeLimitExceeded,
                        sizeLimitExceeded ? "Response body truncated at " + defaultMaxResponseBytes + " bytes" : null,
                        true
                );

            } catch (SocketTimeoutException e) {
                long durationMs = System.currentTimeMillis() - startTime;
                log.warn("Target request timed out after {} ms: {}", durationMs, e.getMessage());
                return HttpResponseSnapshot.failure("Request timed out: " + e.getMessage(), durationMs);
            } catch (ApiException e) {
                long durationMs = System.currentTimeMillis() - startTime;
                log.warn("Outbound target rejected by policy: {}", e.getMessage());
                return HttpResponseSnapshot.failure(e.getMessage(), durationMs);
            } catch (Exception e) {
                long durationMs = System.currentTimeMillis() - startTime;
                log.warn("Outbound connection failed: {}", e.getMessage());
                String sanitized = sanitizeErrorMessage(e.getMessage());
                return HttpResponseSnapshot.failure("Connection failure: " + sanitized, durationMs);
            } finally {
                if (connection != null) {
                    try {
                        connection.disconnect();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private String buildFullUrl(String baseUrl, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return baseUrl;
        }
        StringBuilder sb = new StringBuilder(baseUrl);
        sb.append(baseUrl.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue() != null ? entry.getValue() : "");
            first = false;
        }
        return sb.toString();
    }

    public static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null) {
            return Collections.emptyMap();
        }
        Map<String, String> sanitized = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String lower = entry.getKey().toLowerCase(Locale.ROOT);
            if (SENSITIVE_HEADERS.contains(lower)) {
                sanitized.put(entry.getKey(), "[REDACTED]");
            } else {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        return sanitized;
    }

    private String sanitizeErrorMessage(String rawMessage) {
        if (rawMessage == null) {
            return "Internal connection error";
        }
        // Ensure no internal file paths, secrets or stack details leak
        return rawMessage.replaceAll("([a-zA-Z]:\\\\[^\\s]+)", "[PATH]")
                .replaceAll("(/[^\\s]+)", "[PATH]");
    }
}
