package com.sentinel.shared.net;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of an outbound HTTP response.
 */
public class HttpResponseSnapshot {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final String body;
    private final long responseTimeMs;
    private final boolean sizeLimitExceeded;
    private final String errorMessage;
    private final boolean success;

    public HttpResponseSnapshot(
            int statusCode,
            Map<String, List<String>> headers,
            String body,
            long responseTimeMs,
            boolean sizeLimitExceeded,
            String errorMessage,
            boolean success) {
        this.statusCode = statusCode;
        this.headers = headers != null ? headers : Collections.emptyMap();
        this.body = body;
        this.responseTimeMs = responseTimeMs;
        this.sizeLimitExceeded = sizeLimitExceeded;
        this.errorMessage = errorMessage;
        this.success = success;
    }

    public static HttpResponseSnapshot failure(String errorMessage, long responseTimeMs) {
        return new HttpResponseSnapshot(0, Collections.emptyMap(), null, responseTimeMs, false, errorMessage, false);
    }

    public static HttpResponseSnapshot failureWithStatus(int statusCode, String errorMessage, long responseTimeMs) {
        return new HttpResponseSnapshot(statusCode, Collections.emptyMap(), null, responseTimeMs, false, errorMessage, false);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public String getFirstHeader(String headerName) {
        if (headers == null || headerName == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName)) {
                List<String> values = entry.getValue();
                return (values != null && !values.isEmpty()) ? values.get(0) : null;
            }
        }
        return null;
    }

    public String getBody() {
        return body;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public boolean isSizeLimitExceeded() {
        return sizeLimitExceeded;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }
}
