package com.sentinel.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        boolean success,
        ErrorDetails error,
        Instant timestamp
) {
    public static ApiErrorResponse of(String type, String title, int status, String detail, String errorCode, String instance) {
        return new ApiErrorResponse(
                false,
                new ErrorDetails(type, title, status, detail, errorCode, instance, null),
                Instant.now()
        );
    }

    public static ApiErrorResponse ofValidation(String instance, List<FieldErrorItem> fieldErrors) {
        return new ApiErrorResponse(
                false,
                new ErrorDetails(
                        "https://api-sentinel.dev/errors/validation-failed",
                        "Validation Failed",
                        400,
                        "One or more request parameters failed validation",
                        "VALIDATION_ERROR",
                        instance,
                        fieldErrors
                ),
                Instant.now()
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetails(
            String type,
            String title,
            int status,
            String detail,
            String errorCode,
            String instance,
            List<FieldErrorItem> fieldErrors
    ) {}

    public record FieldErrorItem(
            String field,
            String message
    ) {}
}
