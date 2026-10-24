package com.sentinel.modules.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthConfigDto(
        String type,
        String keyName,
        String headerName,
        String paramLocation,
        String tokenOrPassword,
        String maskedValue
) {
    public static AuthConfigDto none() {
        return new AuthConfigDto("NONE", null, null, null, null, null);
    }

    public static AuthConfigDto of(String type, String keyName, String headerName, String paramLocation, String tokenOrPassword) {
        String masked = maskSecret(tokenOrPassword);
        return new AuthConfigDto(
                type != null ? type.toUpperCase() : "NONE",
                keyName,
                headerName,
                paramLocation != null ? paramLocation.toUpperCase() : "HEADER",
                null, // Never retain raw secret in returned/persisted DTO!
                masked
        );
    }

    public static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        if (secret.length() <= 4) {
            return "****";
        }
        if (secret.length() <= 8) {
            return secret.substring(0, 2) + "****";
        }
        return secret.substring(0, 4) + "...." + secret.substring(secret.length() - 2);
    }
}
