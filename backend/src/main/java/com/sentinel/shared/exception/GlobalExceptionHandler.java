package com.sentinel.shared.exception;

import com.sentinel.shared.model.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        log.warn("Application exception [{}]: {} at URI: {}", ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        ApiErrorResponse response = ApiErrorResponse.of(
                ex.getErrorType(),
                ex.getStatus().getReasonPhrase(),
                ex.getStatus().value(),
                ex.getMessage(),
                ex.getErrorCode(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiErrorResponse.FieldErrorItem> fieldErrors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new ApiErrorResponse.FieldErrorItem(error.getField(), error.getDefaultMessage()));
        }

        log.warn("Validation failed for URI: {} with {} field error(s)", request.getRequestURI(), fieldErrors.size());
        ApiErrorResponse response = ApiErrorResponse.ofValidation(request.getRequestURI(), fieldErrors);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiErrorResponse.FieldErrorItem> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ApiErrorResponse.FieldErrorItem(v.getPropertyPath().toString(), v.getMessage()))
                .toList();

        ApiErrorResponse response = ApiErrorResponse.ofValidation(request.getRequestURI(), fieldErrors);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied for URI: {}. Reason: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse response = ApiErrorResponse.of(
                "https://api-sentinel.dev/errors/forbidden",
                "Forbidden",
                HttpStatus.FORBIDDEN.value(),
                "You do not have permission to access this resource",
                "FORBIDDEN",
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failure at URI: {}. Reason: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse response = ApiErrorResponse.of(
                "https://api-sentinel.dev/errors/unauthorized",
                "Unauthorized",
                HttpStatus.UNAUTHORIZED.value(),
                "Full authentication is required to access this resource",
                "UNAUTHORIZED",
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        ApiErrorResponse response = ApiErrorResponse.of(
                "https://api-sentinel.dev/errors/not-found",
                "Not Found",
                HttpStatus.NOT_FOUND.value(),
                "The requested resource was not found on this server",
                "RESOURCE_NOT_FOUND",
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ApiErrorResponse response = ApiErrorResponse.of(
                "https://api-sentinel.dev/errors/method-not-allowed",
                "Method Not Allowed",
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod()),
                "METHOD_NOT_ALLOWED",
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled server exception at URI: {}", request.getRequestURI(), ex);
        ApiErrorResponse response = ApiErrorResponse.of(
                "https://api-sentinel.dev/errors/internal-server-error",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected internal error occurred. Please contact the administrator.",
                "INTERNAL_SERVER_ERROR",
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
