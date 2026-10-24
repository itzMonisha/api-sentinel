package com.sentinel.modules.testing.assertion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.testing.dto.AssertionDefinition;
import com.sentinel.modules.testing.dto.AssertionResult;
import com.sentinel.modules.testing.entity.AssertionType;
import com.sentinel.shared.net.HttpResponseSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssertionEngineTest {

    private AssertionEngine assertionEngine;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        OpenApiSchemaValidator schemaValidator = new OpenApiSchemaValidator(objectMapper);
        assertionEngine = new AssertionEngine(objectMapper, schemaValidator);
    }

    @Test
    @DisplayName("Evaluate STATUS_CODE_EQUALS assertion")
    void shouldEvaluateStatusCodeEquals() {
        HttpResponseSnapshot response = new HttpResponseSnapshot(
                200, Map.of(), "{}", 120, false, null, true
        );

        List<AssertionResult> results = assertionEngine.evaluate(
                List.of(AssertionDefinition.of(AssertionType.STATUS_CODE_EQUALS, "200")),
                response,
                null
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isTrue();

        // Mismatched status code
        List<AssertionResult> failedResults = assertionEngine.evaluate(
                List.of(AssertionDefinition.of(AssertionType.STATUS_CODE_EQUALS, "201")),
                response,
                null
        );
        assertThat(failedResults.get(0).isPassed()).isFalse();
        assertThat(failedResults.get(0).getMessage()).contains("Expected HTTP status 201 but got 200");
    }

    @Test
    @DisplayName("Evaluate RESPONSE_TIME_BELOW assertion")
    void shouldEvaluateResponseTimeBelow() {
        HttpResponseSnapshot response = new HttpResponseSnapshot(
                200, Map.of(), "{}", 250, false, null, true
        );

        List<AssertionResult> passResult = assertionEngine.evaluate(
                List.of(AssertionDefinition.of(AssertionType.RESPONSE_TIME_BELOW, "500")),
                response,
                null
        );
        assertThat(passResult.get(0).isPassed()).isTrue();

        List<AssertionResult> failResult = assertionEngine.evaluate(
                List.of(AssertionDefinition.of(AssertionType.RESPONSE_TIME_BELOW, "100")),
                response,
                null
        );
        assertThat(failResult.get(0).isPassed()).isFalse();
        assertThat(failResult.get(0).getMessage()).contains("exceeded threshold");
    }

    @Test
    @DisplayName("Evaluate HEADER_EXISTS and CONTENT_TYPE_MATCHES assertions")
    void shouldEvaluateHeaders() {
        HttpResponseSnapshot response = new HttpResponseSnapshot(
                200,
                Map.of(
                        "Content-Type", List.of("application/json; charset=UTF-8"),
                        "X-Request-Id", List.of("req-999")
                ),
                "{}",
                100,
                false,
                null,
                true
        );

        List<AssertionResult> results = assertionEngine.evaluate(
                List.of(
                        AssertionDefinition.of(AssertionType.HEADER_EXISTS, "X-Request-Id", "Present"),
                        AssertionDefinition.of(AssertionType.CONTENT_TYPE_MATCHES, "application/json")
                ),
                response,
                null
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isPassed()).isTrue();
        assertThat(results.get(1).isPassed()).isTrue();

        // Missing header
        List<AssertionResult> failResults = assertionEngine.evaluate(
                List.of(AssertionDefinition.of(AssertionType.HEADER_EXISTS, "X-Missing-Token", "Present")),
                response,
                null
        );
        assertThat(failResults.get(0).isPassed()).isFalse();
        assertThat(failResults.get(0).getMessage()).contains("Required header 'X-Missing-Token' is missing");
    }

    @Test
    @DisplayName("Evaluate JSON_FIELD_EXISTS and JSON_FIELD_EQUALS assertions")
    void shouldEvaluateJsonFields() {
        String body = """
                {
                  "status": "UP",
                  "code": 200,
                  "data": {
                    "user": {
                      "id": 123,
                      "role": "ADMIN",
                      "verified": true
                    }
                  }
                }
                """;

        HttpResponseSnapshot response = new HttpResponseSnapshot(
                200, Map.of("Content-Type", List.of("application/json")), body, 80, false, null, true
        );

        List<AssertionResult> results = assertionEngine.evaluate(
                List.of(
                        AssertionDefinition.of(AssertionType.JSON_FIELD_EXISTS, "$.data.user.role", null),
                        AssertionDefinition.of(AssertionType.JSON_FIELD_EQUALS, "$.status", "UP"),
                        AssertionDefinition.of(AssertionType.JSON_FIELD_EQUALS, "data.user.id", "123"),
                        AssertionDefinition.of(AssertionType.JSON_FIELD_EQUALS, "data.user.role", "ADMIN")
                ),
                response,
                null
        );

        assertThat(results).hasSize(4);
        assertThat(results).allMatch(AssertionResult::isPassed);

        // Non-existent or mismatched values
        List<AssertionResult> failResults = assertionEngine.evaluate(
                List.of(
                        AssertionDefinition.of(AssertionType.JSON_FIELD_EXISTS, "data.unknown.field", null),
                        AssertionDefinition.of(AssertionType.JSON_FIELD_EQUALS, "data.user.role", "VIEWER")
                ),
                response,
                null
        );

        assertThat(failResults).hasSize(2);
        assertThat(failResults.get(0).isPassed()).isFalse();
        assertThat(failResults.get(1).isPassed()).isFalse();
    }

    @Test
    @DisplayName("Evaluate OPENAPI_SCHEMA_MATCH assertion")
    void shouldEvaluateOpenApiSchemaMatch() {
        String validBody = """
                {
                  "id": 101,
                  "name": "Sentinel",
                  "enabled": true
                }
                """;

        String invalidBody = """
                {
                  "id": "not-an-integer",
                  "enabled": true
                }
                """;

        String schema = """
                {
                  "type": "object",
                  "required": ["id", "name"],
                  "properties": {
                    "id": { "type": "integer" },
                    "name": { "type": "string" },
                    "enabled": { "type": "boolean" }
                  }
                }
                """;

        HttpResponseSnapshot validResponse = new HttpResponseSnapshot(
                200, Map.of(), validBody, 50, false, null, true
        );
        List<AssertionResult> validResult = assertionEngine.evaluate(
                List.of(AssertionDefinition.of(AssertionType.OPENAPI_SCHEMA_MATCH, schema)),
                validResponse,
                null
        );
        assertThat(validResult.get(0).isPassed()).isTrue();

        HttpResponseSnapshot invalidResponse = new HttpResponseSnapshot(
                200, Map.of(), invalidBody, 50, false, null, true
        );
        List<AssertionResult> invalidResult = assertionEngine.evaluate(
                List.of(AssertionDefinition.of(AssertionType.OPENAPI_SCHEMA_MATCH, schema)),
                invalidResponse,
                null
        );
        assertThat(invalidResult.get(0).isPassed()).isFalse();
        assertThat(invalidResult.get(0).getMessage()).contains("Missing required field 'name'");
    }

    @Test
    @DisplayName("Evaluate network failure or timeout response")
    void shouldHandleFailureSnapshotsGracefully() {
        HttpResponseSnapshot errorResponse = HttpResponseSnapshot.failure(
                "Request timed out after 5000ms", 5005
        );

        List<AssertionResult> results = assertionEngine.evaluate(
                List.of(
                        AssertionDefinition.of(AssertionType.STATUS_CODE_EQUALS, "200"),
                        AssertionDefinition.of(AssertionType.JSON_FIELD_EXISTS, "$.id", null)
                ),
                errorResponse,
                null
        );

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> !r.isPassed());
        assertThat(results.get(0).getMessage()).contains("Request timed out after 5000ms");
    }
}
