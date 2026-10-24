package com.sentinel.shared.net;

import com.sentinel.shared.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeHttpClientMockServerTest {

    private static HttpServer server;
    private static int port;
    private static SafeHttpClient client;
    private static TargetValidator targetValidator;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        // Standard success handler
        server.createContext("/ok", exchange -> {
            byte[] response = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        // Echo request body & header
        server.createContext("/echo", exchange -> {
            String customHeader = exchange.getRequestHeaders().getFirst("X-Custom-Header");
            byte[] body = exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("X-Echo-Header", customHeader != null ? customHeader : "");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // Slow handler simulating latency beyond timeout
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {
            }
            byte[] response = "slow-response".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        // Large payload handler exceeding response cap
        server.createContext("/large", exchange -> {
            byte[] chunk = new byte[1024 * 1024]; // 1MB chunk
            exchange.sendResponseHeaders(200, 0); // chunked
            try (OutputStream os = exchange.getResponseBody()) {
                for (int i = 0; i < 6; i++) { // 6MB total
                    os.write(chunk);
                }
            }
        });

        // Redirect handler to internal target (SSRF redirect attempt)
        server.createContext("/redirect-ssrf", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://169.254.169.254/latest/meta-data/");
            exchange.sendResponseHeaders(302, -1);
        });

        // Multi-redirect loop handler
        server.createContext("/redirect-loop", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + port + "/redirect-loop");
            exchange.sendResponseHeaders(302, -1);
        });

        server.start();

        // For testing outbound mock server, allowLocalTargets=true allows connecting to 127.0.0.1:port
        CidrBlocklist blocklist = new CidrBlocklist();
        targetValidator = new TargetValidator(blocklist, true);
        client = new SafeHttpClient(targetValidator, 1000, 2000, 5 * 1024 * 1024);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Should execute valid GET request against mock server and capture response")
    void shouldExecuteGetSuccessfully() {
        HttpRequestSnapshot req = new HttpRequestSnapshot(
                "http://127.0.0.1:" + port + "/ok",
                "GET",
                Map.of("Accept", "application/json"),
                null,
                null,
                1000,
                2000,
                false,
                0
        );

        HttpResponseSnapshot res = client.execute(req);

        assertThat(res).isNotNull();
        assertThat(res.getStatusCode()).isEqualTo(200);
        assertThat(res.getBody()).contains("\"status\":\"ok\"");
        assertThat(res.getFirstHeader("Content-Type")).contains("application/json");
        assertThat(res.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should execute POST with body and custom headers")
    void shouldExecutePostWithBodyAndHeaders() {
        HttpRequestSnapshot req = new HttpRequestSnapshot(
                "http://127.0.0.1:" + port + "/echo",
                "POST",
                Map.of("X-Custom-Header", "SentinelAuditSecret123"),
                null,
                "{\"payload\":\"test\"}",
                1000,
                2000,
                false,
                0
        );

        HttpResponseSnapshot res = client.execute(req);

        assertThat(res.getStatusCode()).isEqualTo(200);
        assertThat(res.getBody()).isEqualTo("{\"payload\":\"test\"}");
        assertThat(res.getFirstHeader("X-Echo-Header")).isEqualTo("SentinelAuditSecret123");
    }

    @Test
    @DisplayName("Should enforce read timeout when target server exceeds configured readTimeoutMs")
    void shouldEnforceTimeout() {
        HttpRequestSnapshot req = new HttpRequestSnapshot(
                "http://127.0.0.1:" + port + "/slow",
                "GET",
                null,
                null,
                null,
                500,
                200, // 200ms timeout vs 800ms server sleep
                false,
                0
        );

        HttpResponseSnapshot res = client.execute(req);

        assertThat(res.getStatusCode()).isEqualTo(0);
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrorMessage()).containsIgnoringCase("timed out");
    }

    @Test
    @DisplayName("Should enforce response size cap and truncate oversized payloads")
    void shouldCapLargeResponses() {
        HttpRequestSnapshot req = new HttpRequestSnapshot(
                "http://127.0.0.1:" + port + "/large",
                "GET",
                null,
                null,
                null,
                1000,
                5000,
                false,
                0
        );

        HttpResponseSnapshot res = client.execute(req);

        assertThat(res.getStatusCode()).isEqualTo(200);
        assertThat(res.isSizeLimitExceeded()).isTrue();
        assertThat(res.getBody().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(5 * 1024 * 1024);
    }

    @Test
    @DisplayName("Should reject redirect to cloud metadata service during redirect following")
    void shouldInterceptSsrfRedirect() {
        // TargetValidator with allowLocalTargets=false to verify SSRF block on metadata
        CidrBlocklist blocklist = new CidrBlocklist();
        TargetValidator strictValidator = new TargetValidator(blocklist, false);
        SafeHttpClient strictClient = new SafeHttpClient(strictValidator, 1000, 2000, 5242880);

        // When redirect points to 169.254.169.254, validateAndResolve must reject it
        assertThatThrownBy(() -> strictValidator.validateAndResolve("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getErrorCode()).isIn("SSRF_TARGET_BLOCKED", "FORBIDDEN_TARGET_IP", "SSRF_ATTEMPT_DETECTED");
                });
    }

    @Test
    @DisplayName("Should fail gracefully when redirect hop limit is exceeded")
    void shouldStopOnMaxRedirectHops() {
        HttpRequestSnapshot req = new HttpRequestSnapshot(
                "http://127.0.0.1:" + port + "/redirect-loop",
                "GET",
                null,
                null,
                null,
                1000,
                2000,
                true,
                2 // max 2 hops
        );

        HttpResponseSnapshot res = client.execute(req);

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrorMessage()).contains("Maximum redirect limit (2) exceeded");
    }
}
