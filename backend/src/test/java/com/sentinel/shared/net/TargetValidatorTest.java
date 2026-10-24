package com.sentinel.shared.net;

import com.sentinel.shared.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetValidatorTest {

    private final CidrBlocklist cidrBlocklist = new CidrBlocklist();

    @Test
    @DisplayName("Should reject invalid or unsupported protocols (file://, ftp://, gopher://)")
    void shouldRejectUnsupportedProtocols() {
        TargetValidator validator = new TargetValidator(cidrBlocklist, false);

        assertThatThrownBy(() -> validator.validateAndResolve("ftp://example.com/file.txt"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only 'http' and 'https' protocols are permitted");

        assertThatThrownBy(() -> validator.validateAndResolve("file:///etc/passwd"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only 'http' and 'https' protocols are permitted");
    }

    @Test
    @DisplayName("Should reject cloud metadata hostname metadata.google.internal")
    void shouldRejectGoogleMetadataHostname() {
        TargetValidator validator = new TargetValidator(cidrBlocklist, false);

        assertThatThrownBy(() -> validator.validateAndResolve("http://metadata.google.internal/computeMetadata/v1/"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Access to cloud metadata endpoints is strictly blocked");
    }

    @Test
    @DisplayName("Should reject localhost when allowLocalTargets is false")
    void shouldRejectLocalhostWhenDisabled() {
        TargetValidator validator = new TargetValidator(cidrBlocklist, false);

        assertThatThrownBy(() -> validator.validateAndResolve("http://localhost:8080/api"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Access to localhost and internal hostnames is prohibited");
    }

    @Test
    @DisplayName("Should permit localhost when allowLocalTargets is true")
    void shouldPermitLocalhostWhenEnabled() {
        TargetValidator validator = new TargetValidator(cidrBlocklist, true);

        assertThat(validator.validateAndResolve("http://127.0.0.1:8080/api")).isNotEmpty();
    }
}
