package com.sentinel.shared.net;

import com.sentinel.shared.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Validates outbound request targets against SSRF vulnerabilities, DNS rebinding, and cloud metadata targets.
 */
@Component
public class TargetValidator {

    private final CidrBlocklist cidrBlocklist;
    private final boolean allowLocalTargets;

    public TargetValidator(
            CidrBlocklist cidrBlocklist,
            @Value("${api-sentinel.security.allow-local-targets:false}") boolean allowLocalTargets) {
        this.cidrBlocklist = cidrBlocklist;
        this.allowLocalTargets = allowLocalTargets;
    }

    public boolean isAllowLocalTargets() {
        return allowLocalTargets;
    }

    /**
     * Validates a target URL and resolves all associated IP addresses to ensure zero-trust compliance.
     *
     * @param urlString Target URL string
     * @return List of validated IP addresses for the target
     */
    public List<InetAddress> validateAndResolve(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new ApiException("Target URL must not be blank", HttpStatus.BAD_REQUEST, "INVALID_TARGET_URL");
        }

        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (Exception e) {
            throw new ApiException("Malformed target URL: " + e.getMessage(), HttpStatus.BAD_REQUEST, "INVALID_TARGET_URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new ApiException("Only 'http' and 'https' protocols are permitted for outbound requests",
                    HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROTOCOL");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException("Target URL host cannot be resolved", HttpStatus.BAD_REQUEST, "INVALID_TARGET_HOST");
        }

        String lowerHost = host.toLowerCase(Locale.ROOT);

        // Explicit cloud metadata domain checks
        if (lowerHost.equals("metadata.google.internal") || lowerHost.endsWith(".metadata.google.internal") || lowerHost.equals("metadata")) {
            throw new ApiException("Access to cloud metadata endpoints is strictly blocked",
                    HttpStatus.BAD_REQUEST, "SSRF_TARGET_BLOCKED");
        }

        if (!allowLocalTargets && (lowerHost.equals("localhost") || lowerHost.endsWith(".localhost") || lowerHost.endsWith(".local") || lowerHost.endsWith(".internal"))) {
            throw new ApiException("Access to localhost and internal hostnames is prohibited",
                    HttpStatus.BAD_REQUEST, "SSRF_TARGET_BLOCKED");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new ApiException("Failed to resolve host: " + host, HttpStatus.BAD_REQUEST, "DNS_RESOLUTION_FAILED");
        }

        if (addresses == null || addresses.length == 0) {
            throw new ApiException("DNS query returned no IP addresses for host: " + host,
                    HttpStatus.BAD_REQUEST, "DNS_RESOLUTION_FAILED");
        }

        for (InetAddress address : addresses) {
            if (cidrBlocklist.isBlocked(address, allowLocalTargets)) {
                throw new ApiException("Target resolved to blocked internal/metadata address: " + address.getHostAddress(),
                        HttpStatus.BAD_REQUEST, "SSRF_TARGET_BLOCKED");
            }
        }

        return Arrays.asList(addresses);
    }
}
