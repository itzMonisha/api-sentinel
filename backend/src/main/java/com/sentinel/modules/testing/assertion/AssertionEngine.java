package com.sentinel.modules.testing.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.testing.dto.AssertionDefinition;
import com.sentinel.modules.testing.dto.AssertionResult;
import com.sentinel.modules.testing.entity.AssertionType;
import com.sentinel.shared.net.HttpResponseSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates test case assertions against an HTTP response snapshot.
 * Separated cleanly from the HTTP execution layer.
 */
@Component
public class AssertionEngine {

    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("(.+)\\[(\\d+)\\]$");

    private final ObjectMapper objectMapper;
    private final OpenApiSchemaValidator schemaValidator;

    public AssertionEngine(ObjectMapper objectMapper, OpenApiSchemaValidator schemaValidator) {
        this.objectMapper = objectMapper;
        this.schemaValidator = schemaValidator;
    }

    /**
     * Evaluates a set of assertions against an HTTP response.
     *
     * @param assertions                   configured assertions
     * @param response                     HTTP response snapshot
     * @param endpointResponseSchema       optional OpenAPI response schema configured on the endpoint
     * @return list of assertion results
     */
    public List<AssertionResult> evaluate(
            List<AssertionDefinition> assertions,
            HttpResponseSnapshot response,
            String endpointResponseSchema) {

        List<AssertionResult> results = new ArrayList<>();

        if (assertions == null || assertions.isEmpty()) {
            // Default check: verify status 2xx or 3xx if no assertions configured
            boolean is2xxOr3xx = response.getStatusCode() >= 200 && response.getStatusCode() < 400;
            if (is2xxOr3xx) {
                results.add(AssertionResult.pass(
                        AssertionType.STATUS_CODE_EQUALS.name(),
                        null,
                        "2xx/3xx",
                        String.valueOf(response.getStatusCode()),
                        "Response status " + response.getStatusCode() + " indicates success"
                ));
            } else {
                results.add(AssertionResult.fail(
                        AssertionType.STATUS_CODE_EQUALS.name(),
                        null,
                        "2xx/3xx",
                        String.valueOf(response.getStatusCode()),
                        response.getErrorMessage() != null ? response.getErrorMessage() : "Response status " + response.getStatusCode() + " was not successful"
                ));
            }
            return results;
        }

        // Parse JSON tree lazily once if needed
        JsonNode rootJsonNode = null;
        boolean jsonParsed = false;

        for (AssertionDefinition def : assertions) {
            AssertionType type = def.getType();
            if (type == null) {
                continue;
            }

            switch (type) {
                case STATUS_CODE_EQUALS -> {
                    String expected = def.getExpected();
                    int expectedCode = parseStatusCode(expected);
                    int actualCode = response.getStatusCode();
                    if (actualCode == expectedCode) {
                        results.add(AssertionResult.pass(
                                type.name(),
                                null,
                                String.valueOf(expectedCode),
                                String.valueOf(actualCode),
                                "HTTP status equals " + expectedCode
                        ));
                    } else {
                        String msg = response.getErrorMessage() != null
                                ? "Expected HTTP status " + expectedCode + " but got " + actualCode + ": " + response.getErrorMessage()
                                : "Expected HTTP status " + expectedCode + " but got " + actualCode;
                        results.add(AssertionResult.fail(
                                type.name(),
                                null,
                                String.valueOf(expectedCode),
                                String.valueOf(actualCode),
                                msg
                        ));
                    }
                }

                case RESPONSE_TIME_BELOW -> {
                    long thresholdMs = parseLong(def.getExpected(), 10000L);
                    long actualMs = response.getResponseTimeMs();
                    if (actualMs <= thresholdMs) {
                        results.add(AssertionResult.pass(
                                type.name(),
                                null,
                                thresholdMs + "ms",
                                actualMs + "ms",
                                "Response time (" + actualMs + "ms) is below threshold of " + thresholdMs + "ms"
                        ));
                    } else {
                        results.add(AssertionResult.fail(
                                type.name(),
                                null,
                                thresholdMs + "ms",
                                actualMs + "ms",
                                "Response time (" + actualMs + "ms) exceeded threshold of " + thresholdMs + "ms"
                        ));
                    }
                }

                case HEADER_EXISTS -> {
                    String headerName = def.getProperty();
                    String headerValue = response.getFirstHeader(headerName);
                    if (headerValue != null) {
                        results.add(AssertionResult.pass(
                                type.name(),
                                headerName,
                                "Present",
                                "Present (" + headerValue + ")",
                                "Required header '" + headerName + "' is present"
                        ));
                    } else {
                        results.add(AssertionResult.fail(
                                type.name(),
                                headerName,
                                "Present",
                                "Missing",
                                "Required header '" + headerName + "' is missing"
                        ));
                    }
                }

                case CONTENT_TYPE_MATCHES -> {
                    String expected = def.getExpected() != null ? def.getExpected().trim().toLowerCase() : "";
                    String actual = response.getFirstHeader("Content-Type");
                    if (actual != null && actual.toLowerCase().contains(expected)) {
                        results.add(AssertionResult.pass(
                                type.name(),
                                "Content-Type",
                                expected,
                                actual,
                                "Content-Type '" + actual + "' matches expected '" + expected + "'"
                        ));
                    } else {
                        results.add(AssertionResult.fail(
                                type.name(),
                                "Content-Type",
                                expected,
                                actual != null ? actual : "null",
                                "Content-Type '" + (actual != null ? actual : "null") + "' does not match expected '" + expected + "'"
                        ));
                    }
                }

                case JSON_FIELD_EXISTS -> {
                    if (!jsonParsed) {
                        rootJsonNode = parseJson(response.getBody());
                        jsonParsed = true;
                    }
                    if (rootJsonNode == null) {
                        results.add(AssertionResult.fail(
                                type.name(),
                                def.getProperty(),
                                "Field exists",
                                "Invalid JSON",
                                "Response body is not valid JSON"
                        ));
                    } else {
                        JsonNode target = findNode(rootJsonNode, def.getProperty());
                        if (target != null && !target.isMissingNode() && !target.isNull()) {
                            results.add(AssertionResult.pass(
                                    type.name(),
                                    def.getProperty(),
                                    "Field exists",
                                    "Found: " + target.asText(),
                                    "JSON field '" + def.getProperty() + "' exists"
                            ));
                        } else {
                            results.add(AssertionResult.fail(
                                    type.name(),
                                    def.getProperty(),
                                    "Field exists",
                                    "Missing",
                                    "JSON field '" + def.getProperty() + "' does not exist in response"
                            ));
                        }
                    }
                }

                case JSON_FIELD_EQUALS -> {
                    if (!jsonParsed) {
                        rootJsonNode = parseJson(response.getBody());
                        jsonParsed = true;
                    }
                    if (rootJsonNode == null) {
                        results.add(AssertionResult.fail(
                                type.name(),
                                def.getProperty(),
                                def.getExpected(),
                                "Invalid JSON",
                                "Response body is not valid JSON"
                        ));
                    } else {
                        JsonNode target = findNode(rootJsonNode, def.getProperty());
                        if (target == null || target.isMissingNode()) {
                            results.add(AssertionResult.fail(
                                    type.name(),
                                    def.getProperty(),
                                    def.getExpected(),
                                    "Missing",
                                    "JSON field '" + def.getProperty() + "' does not exist in response"
                            ));
                        } else {
                            String actualValue = target.isValueNode() ? target.asText() : target.toString();
                            String expectedValue = def.getExpected() != null ? def.getExpected() : "";
                            boolean matches = actualValue.equalsIgnoreCase(expectedValue)
                                    || isNumericEqual(target, expectedValue);

                            if (matches) {
                                results.add(AssertionResult.pass(
                                        type.name(),
                                        def.getProperty(),
                                        expectedValue,
                                        actualValue,
                                        "JSON field '" + def.getProperty() + "' equals expected value '" + expectedValue + "'"
                                ));
                            } else {
                                results.add(AssertionResult.fail(
                                        type.name(),
                                        def.getProperty(),
                                        expectedValue,
                                        actualValue,
                                        "JSON field '" + def.getProperty() + "' expected '" + expectedValue + "' but got '" + actualValue + "'"
                                ));
                            }
                        }
                    }
                }

                case OPENAPI_SCHEMA_MATCH -> {
                    String schema = (def.getExpected() != null && !def.getExpected().isBlank())
                            ? def.getExpected()
                            : endpointResponseSchema;

                    if (schema == null || schema.isBlank()) {
                        results.add(AssertionResult.fail(
                                type.name(),
                                def.getProperty(),
                                "OpenAPI Schema",
                                "No Schema Defined",
                                "No OpenAPI response schema configured for validation"
                        ));
                    } else {
                        List<String> schemaErrors = schemaValidator.validate(response.getBody(), schema);
                        if (schemaErrors.isEmpty()) {
                            results.add(AssertionResult.pass(
                                    type.name(),
                                    def.getProperty(),
                                    "OpenAPI Schema Match",
                                    "Compliant",
                                    "Response conforms to OpenAPI response schema"
                            ));
                        } else {
                            results.add(AssertionResult.fail(
                                    type.name(),
                                    def.getProperty(),
                                    "OpenAPI Schema Match",
                                    String.join("; ", schemaErrors),
                                    "Response does not conform to OpenAPI schema: " + String.join("; ", schemaErrors)
                            ));
                        }
                    }
                }
            }
        }

        return results;
    }

    private JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode findNode(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        // Normalize path: remove leading $. or /
        String cleanPath = path.trim();
        if (cleanPath.startsWith("$.")) {
            cleanPath = cleanPath.substring(2);
        } else if (cleanPath.startsWith("$") || cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }

        String[] parts = cleanPath.split("[./]");
        JsonNode current = root;

        for (String part : parts) {
            if (part.isBlank()) continue;

            Matcher matcher = ARRAY_INDEX_PATTERN.matcher(part);
            if (matcher.matches()) {
                String fieldName = matcher.group(1);
                int index = Integer.parseInt(matcher.group(2));
                current = current.get(fieldName);
                if (current == null || !current.isArray() || current.size() <= index) {
                    return null;
                }
                current = current.get(index);
            } else {
                current = current.get(part);
            }

            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private boolean isNumericEqual(JsonNode node, String expected) {
        if (!node.isNumber()) {
            return false;
        }
        try {
            double actualDouble = node.asDouble();
            double expectedDouble = Double.parseDouble(expected);
            return Double.compare(actualDouble, expectedDouble) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private int parseStatusCode(String val) {
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return 200;
        }
    }

    private long parseLong(String val, long defaultVal) {
        try {
            return Long.parseLong(val.trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
