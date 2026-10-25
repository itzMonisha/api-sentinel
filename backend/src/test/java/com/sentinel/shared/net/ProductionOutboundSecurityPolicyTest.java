package com.sentinel.shared.net;

import com.sentinel.shared.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pre-deployment verification of the zero-trust outbound scanning security policy.
 * Verifies that under production configuration (allow-local-targets=false), the scanner
 * and probing engines CANNOT access any internal infrastructure, loopback networks,
 * or cloud metadata APIs.
 */
class ProductionOutboundSecurityPolicyTest {

    private TargetValidator productionValidator;
    private CidrBlocklist cidrBlocklist;

    @BeforeEach
    void setUp() {
        cidrBlocklist = new CidrBlocklist();
        // Production setting: allowLocalTargets MUST be false
        productionValidator = new TargetValidator(cidrBlocklist, false);
    }

    @Test
    @DisplayName("Production validator strictly disallows local targets")
    void shouldConfirmProductionPolicyFlag() {
        assertThat(productionValidator.isAllowLocalTargets()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8080/actuator/env",
            "http://127.0.0.2:9000",
            "http://localhost:8080/api/v1/health",
            "http://app.localhost:3000",
            "http://0.0.0.0:8080",
            "http://[::1]:8080"
    })
    @DisplayName("Blocks all loopback addresses and localhost aliases in production")
    void shouldBlockLoopbackTargets(String targetUrl) {
        assertThatThrownBy(() -> productionValidator.validateAndResolve(targetUrl))
                .isInstanceOf(ApiException.class)
                .hasMessageMatching(".*(Access to localhost|Target resolved to blocked internal|SSRF).*");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://10.0.0.1/admin",
            "http://10.254.1.50:8080/actuator",
            "http://172.16.0.1/status",
            "http://172.31.255.255:5432",
            "http://192.168.1.1/setup",
            "http://192.168.100.200:8080",
            "http://100.64.0.1/cgnat"
    })
    @DisplayName("Blocks RFC 1918 private subnets and carrier-grade NAT in production")
    void shouldBlockPrivateSubnetTargets(String targetUrl) {
        assertThatThrownBy(() -> productionValidator.validateAndResolve(targetUrl))
                .isInstanceOf(ApiException.class)
                .hasMessageMatching(".*(Target resolved to blocked internal|SSRF).*");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data/",
            "http://169.254.169.254/computeMetadata/v1/",
            "http://metadata.google.internal/computeMetadata/v1/",
            "http://metadata/v1"
    })
    @DisplayName("Blocks all cloud metadata IP and domain endpoints (AWS, GCP, Azure)")
    void shouldBlockCloudMetadataEndpoints(String targetUrl) {
        assertThatThrownBy(() -> productionValidator.validateAndResolve(targetUrl))
                .isInstanceOf(ApiException.class)
                .hasMessageMatching(".*(cloud metadata|Target resolved to blocked internal|SSRF).*");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://api.example.com",
            "file:///etc/passwd",
            "gopher://127.0.0.1:70",
            "dict://127.0.0.1:2628"
    })
    @DisplayName("Rejects non-HTTP/HTTPS protocols with clear validation error")
    void shouldRejectNonHttpProtocols(String targetUrl) {
        assertThatThrownBy(() -> productionValidator.validateAndResolve(targetUrl))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only 'http' and 'https' protocols are permitted");
    }

    @Test
    @DisplayName("Permits public IP addresses in production")
    void shouldAllowPublicTargets() {
        // 8.8.8.8 and 1.1.1.1 are known public resolver addresses
        assertThat(productionValidator.validateAndResolve("http://8.8.8.8/health")).isNotEmpty();
        assertThat(productionValidator.validateAndResolve("https://1.1.1.1/dns-query")).isNotEmpty();
    }
}
