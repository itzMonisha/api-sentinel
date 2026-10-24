package com.sentinel.modules.scanner.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.scanner.entity.Confidence;
import com.sentinel.modules.scanner.entity.SecurityFinding;
import com.sentinel.modules.scanner.entity.SecurityScan;
import com.sentinel.modules.scanner.entity.Severity;
import com.sentinel.modules.scanner.service.Sanitizer;
import com.sentinel.shared.net.HttpRequestSnapshot;
import com.sentinel.shared.net.HttpResponseSnapshot;
import com.sentinel.shared.net.SafeHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ScannerRulesTest {

    private SafeHttpClient mockHttpClient;
    private Sanitizer sanitizer;
    private ObjectMapper objectMapper;
    private ScanContext context;
    private SecurityScan scan;
    private Project project;

    @BeforeEach
    void setUp() {
        mockHttpClient = Mockito.mock(SafeHttpClient.class);
        sanitizer = new Sanitizer();
        objectMapper = new ObjectMapper();
        project = new Project();
        context = new ScanContext(project, mockHttpClient, null, Map.of("Authorization", "Bearer viewer_token"), sanitizer);
        scan = new SecurityScan();
    }

    // ==========================================
    // Check 1: Security Headers
    // ==========================================
    @Test
    @DisplayName("SecurityHeadersCheck: Detects missing headers and banner leakage")
    void shouldDetectMissingSecurityHeadersAndBannerLeakage() {
        SecurityHeadersCheck check = new SecurityHeadersCheck();
        ApiEndpoint endpoint = new ApiEndpoint(project, "HTTP Endpoint", "https://api.example.com/items", "GET");

        // Response missing HSTS, X-Content-Type-Options, X-Frame-Options, but leaking Server version
        HttpResponseSnapshot response = new HttpResponseSnapshot(
                200,
                Map.of("Server", List.of("Apache/2.4.41 (Ubuntu)"), "X-Powered-By", List.of("Express")),
                "{}", 100, false, null, true
        );
        when(mockHttpClient.execute(any(HttpRequestSnapshot.class))).thenReturn(response);

        List<SecurityFinding> findings = check.execute(endpoint, context, scan);

        assertThat(findings).isNotEmpty();
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("MISSING_HSTS"));
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("MISSING_X_CONTENT_TYPE_OPTIONS"));
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("MISSING_CLICKJACKING_PROTECTION"));
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("SERVER_BANNER_LEAKAGE"));
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("X_POWERED_BY_LEAKAGE"));
    }

    @Test
    @DisplayName("SecurityHeadersCheck: False-positive resistance when all headers present")
    void shouldNotFlagWhenSecurityHeadersProperlyConfigured() {
        SecurityHeadersCheck check = new SecurityHeadersCheck();
        ApiEndpoint endpoint = new ApiEndpoint(project, "Hardened Endpoint", "https://api.example.com/items", "GET");

        HttpResponseSnapshot response = new HttpResponseSnapshot(
                200,
                Map.of(
                        "Strict-Transport-Security", List.of("max-age=31536000; includeSubDomains"),
                        "X-Content-Type-Options", List.of("nosniff"),
                        "X-Frame-Options", List.of("DENY"),
                        "Content-Security-Policy", List.of("frame-ancestors 'none'")
                ),
                "{}", 100, false, null, true
        );
        when(mockHttpClient.execute(any(HttpRequestSnapshot.class))).thenReturn(response);

        List<SecurityFinding> findings = check.execute(endpoint, context, scan);
        assertThat(findings).isEmpty();
    }

    // ==========================================
    // Check 2: Insecure Transport
    // ==========================================
    @Test
    @DisplayName("InsecureTransportCheck: Detects cleartext HTTP and ignores HTTPS")
    void shouldDetectCleartextHttpTransport() {
        InsecureTransportCheck check = new InsecureTransportCheck();

        ApiEndpoint httpEp = new ApiEndpoint(project, "Plain HTTP", "http://api.example.com/users", "GET");
        List<SecurityFinding> findings = check.execute(httpEp, context, scan);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleId()).isEqualTo("CLEARTEXT_HTTP_TRANSPORT");
        assertThat(findings.get(0).getSeverity()).isEqualTo(Severity.HIGH);

        ApiEndpoint httpsEp = new ApiEndpoint(project, "Secure HTTPS", "https://api.example.com/users", "GET");
        List<SecurityFinding> noFindings = check.execute(httpsEp, context, scan);
        assertThat(noFindings).isEmpty();
    }

    // ==========================================
    // Check 3: Unauthenticated Access to Expected Protected Endpoint
    // ==========================================
    @Test
    @DisplayName("UnauthenticatedAccessCheck: Detects when protected route responds 200 without auth")
    void shouldDetectUnauthenticatedAccessToProtectedEndpoint() {
        UnauthenticatedAccessCheck check = new UnauthenticatedAccessCheck(objectMapper);

        ApiEndpoint endpoint = new ApiEndpoint(project, "Protected Endpoint", "https://api.example.com/admin/settings", "GET");
        endpoint.setAuthConfig("{\"type\":\"BEARER\"}");

        HttpResponseSnapshot response = new HttpResponseSnapshot(
                200, Map.of(), "{\"secretSettings\": true}", 100, false, null, true
        );
        when(mockHttpClient.execute(any(HttpRequestSnapshot.class))).thenReturn(response);

        List<SecurityFinding> findings = check.execute(endpoint, context, scan);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleId()).isEqualTo("PUBLIC_SENSITIVE_ENDPOINT");
        assertThat(findings.get(0).getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    @DisplayName("UnauthenticatedAccessCheck: False-positive resistance when endpoint rejects unauthenticated request")
    void shouldNotFlagWhenEndpointRejectsWith401() {
        UnauthenticatedAccessCheck check = new UnauthenticatedAccessCheck(objectMapper);

        ApiEndpoint endpoint = new ApiEndpoint(project, "Protected Endpoint", "https://api.example.com/admin/settings", "GET");
        endpoint.setAuthConfig("{\"type\":\"BEARER\"}");

        HttpResponseSnapshot response = new HttpResponseSnapshot(
                401, Map.of(), "{\"error\": \"Unauthorized\"}", 100, false, null, true
        );
        when(mockHttpClient.execute(any(HttpRequestSnapshot.class))).thenReturn(response);

        List<SecurityFinding> findings = check.execute(endpoint, context, scan);
        assertThat(findings).isEmpty();
    }

    // ==========================================
    // Check 4: Authentication Consistency
    // ==========================================
    @Test
    @DisplayName("AuthenticationConsistencyCheck: Flags 500 server crash on malformed token probe")
    void shouldFlagUnhandledExceptionOnMalformedToken() {
        AuthenticationConsistencyCheck check = new AuthenticationConsistencyCheck(objectMapper);
        ApiEndpoint endpoint = new ApiEndpoint(project, "API", "https://api.example.com/items", "GET");

        HttpResponseSnapshot response = new HttpResponseSnapshot(
                500, Map.of(), "{\"error\": \"NullPointerException\"}", 100, false, null, true
        );
        when(mockHttpClient.execute(any(HttpRequestSnapshot.class))).thenReturn(response);

        List<SecurityFinding> findings = check.execute(endpoint, context, scan);
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("MALFORMED_TOKEN_UNHANDLED_EXCEPTION"));
    }

    // ==========================================
    // Check 5: OpenAPI Security Requirements
    // ==========================================
    @Test
    @DisplayName("OpenApiSecurityRequirementCheck: Flags sensitive mutating route without security scheme")
    void shouldFlagMutatingOperationWithoutSecurity() {
        OpenApiSecurityRequirementCheck check = new OpenApiSecurityRequirementCheck(objectMapper);

        ApiEndpoint deleteUser = new ApiEndpoint(project, "Delete User", "https://api.example.com/admin/users/1", "DELETE");
        deleteUser.setAuthConfig("{\"type\":\"NONE\"}");

        List<SecurityFinding> findings = check.execute(deleteUser, context, scan);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleId()).isEqualTo("OPENAPI_UNSECURED_MUTATING_OPERATION");
        assertThat(findings.get(0).getConfidence()).isEqualTo(Confidence.POTENTIAL_RISK);

        // Safe read-only public endpoint should NOT be flagged
        ApiEndpoint health = new ApiEndpoint(project, "Health Probe", "https://api.example.com/health", "GET");
        List<SecurityFinding> healthFindings = check.execute(health, context, scan);
        assertThat(healthFindings).isEmpty();
    }

    // ==========================================
    // Check 6: Information Disclosure & Leakage
    // ==========================================
    @Test
    @DisplayName("SensitiveDataExposureCheck: Detects stack traces and database errors with redaction")
    void shouldDetectStackTraceAndDatabaseLeakage() {
        SensitiveDataExposureCheck check = new SensitiveDataExposureCheck();
        ApiEndpoint endpoint = new ApiEndpoint(project, "Crash Endpoint", "https://api.example.com/crash", "GET");

        String errorBody = """
                {
                  "error": "Unhandled error",
                  "trace": "java.lang.NullPointerException at com.sentinel.dao.UserDao.query(UserDao.java:88)",
                  "query": "syntax error at or near 'SELECT * FROM users WHERE email = 'alice@example.com''"
                }
                """;

        HttpResponseSnapshot response = new HttpResponseSnapshot(
                500, Map.of(), errorBody, 100, false, null, true
        );
        when(mockHttpClient.execute(any(HttpRequestSnapshot.class))).thenReturn(response);

        List<SecurityFinding> findings = check.execute(endpoint, context, scan);
        assertThat(findings).isNotEmpty();
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("STACK_TRACE_LEAKAGE"));
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("DATABASE_ERROR_EXPOSURE"));

        // Verify evidence is redacted of personal data (email)
        for (SecurityFinding finding : findings) {
            assertThat(finding.getEvidence()).doesNotContain("alice@example.com");
        }
    }

    @Test
    @DisplayName("SensitiveDataExposureCheck: False-positive resistance on clean RFC 7807 error responses")
    void shouldNotFlagCleanProblemDetailsErrorResponse() {
        SensitiveDataExposureCheck check = new SensitiveDataExposureCheck();
        ApiEndpoint endpoint = new ApiEndpoint(project, "Clean Error Endpoint", "https://api.example.com/clean", "GET");

        String cleanBody = """
                {
                  "type": "https://api-sentinel.dev/errors/validation-error",
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Invalid parameter provided"
                }
                """;

        HttpResponseSnapshot response = new HttpResponseSnapshot(
                400, Map.of(), cleanBody, 100, false, null, true
        );
        when(mockHttpClient.execute(any(HttpRequestSnapshot.class))).thenReturn(response);

        List<SecurityFinding> findings = check.execute(endpoint, context, scan);
        assertThat(findings).isEmpty();
    }

    // ==========================================
    // Check 7: Inconsistent Authorization Behavior (Safe Test Account Fixture)
    // ==========================================
    @Test
    @DisplayName("AuthorizationConsistencyCheck: Flags BFLA when elevated admin route responds 200 to low-privilege test account")
    void shouldFlagBrokenFunctionLevelAuthWhenFixtureSupplied() {
        AuthorizationConsistencyCheck check = new AuthorizationConsistencyCheck();
        ApiEndpoint adminEp = new ApiEndpoint(project, "Admin Settings", "https://api.example.com/admin/system", "GET");

        // When low-privilege test account sends request, admin endpoint returns 200 OK!
        HttpResponseSnapshot response = new HttpResponseSnapshot(
                200, Map.of(), "{\"systemStatus\": \"active\"}", 100, false, null, true
        );
        when(mockHttpClient.execute(any(HttpRequestSnapshot.class))).thenReturn(response);

        List<SecurityFinding> findings = check.execute(adminEp, context, scan);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleId()).isEqualTo("BROKEN_FUNCTION_LEVEL_AUTH");
        assertThat(findings.get(0).getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    @DisplayName("AuthorizationConsistencyCheck: Safely skips when no test fixture is provided")
    void shouldSkipSafelyWhenNoFixtureProvided() {
        AuthorizationConsistencyCheck check = new AuthorizationConsistencyCheck();
        ApiEndpoint adminEp = new ApiEndpoint(project, "Admin Settings", "https://api.example.com/admin/system", "GET");

        ScanContext noFixtureContext = new ScanContext(project, mockHttpClient, null, Map.of(), sanitizer);
        List<SecurityFinding> findings = check.execute(adminEp, noFixtureContext, scan);
        assertThat(findings).isEmpty();
    }
}
