package com.sentinel.modules.scanner.rule;

import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.scanner.entity.Confidence;
import com.sentinel.modules.scanner.entity.SecurityFinding;
import com.sentinel.modules.scanner.entity.SecurityScan;
import com.sentinel.modules.scanner.entity.Severity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Check 2: Insecure transport configuration (verifies if endpoint transmits over unencrypted cleartext HTTP).
 */
@Component
public class InsecureTransportCheck implements ScannerCheck {

    @Override
    public String getRuleId() {
        return "INSECURE_TRANSPORT";
    }

    @Override
    public String getRuleName() {
        return "Unencrypted Cleartext Transport Audit";
    }

    @Override
    public String getOwaspCategory() {
        return "API2:2023 Broken Authentication";
    }

    @Override
    public List<SecurityFinding> execute(ApiEndpoint endpoint, ScanContext context, SecurityScan scan) {
        List<SecurityFinding> findings = new ArrayList<>();
        if (endpoint.getUrl() == null || endpoint.getUrl().isBlank()) {
            return findings;
        }

        String url = endpoint.getUrl().trim();
        String lower = url.toLowerCase(Locale.ROOT);

        if (lower.startsWith("http://")) {
            // Check if target is a known local testing fixture (e.g., localhost or 127.0.0.1)
            boolean isLocalTarget = false;
            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                if (host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1"))) {
                    isLocalTarget = true;
                }
            } catch (Exception ignored) {
            }

            Severity severity = isLocalTarget ? Severity.MEDIUM : Severity.HIGH;
            Confidence confidence = Confidence.CONFIRMED;

            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "CLEARTEXT_HTTP_TRANSPORT",
                    "Unencrypted Cleartext HTTP Protocol in Use",
                    severity,
                    confidence,
                    getOwaspCategory(),
                    "The API endpoint URL is configured with the 'http://' scheme rather than secure HTTPS. Transmission over cleartext HTTP exposes credentials, request bodies, and tokens to network eavesdropping and adversary-in-the-middle (AITM) attacks.",
                    "Configured URL: " + context.getSanitizer().redact(url),
                    "Upgrade target URL scheme to 'https://' and enforce modern TLS (TLS 1.2 or TLS 1.3) with mandatory HSTS."
            ));
        }

        return findings;
    }
}
