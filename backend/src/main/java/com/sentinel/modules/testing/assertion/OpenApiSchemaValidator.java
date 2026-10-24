package com.sentinel.modules.testing.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Validates JSON response bodies against OpenAPI 3.0/3.1 schema definitions.
 * Validates root types, required properties, and property types.
 */
@Component
public class OpenApiSchemaValidator {

    private final ObjectMapper objectMapper;

    public OpenApiSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> validate(String jsonBody, String schemaJsonOrYaml) {
        List<String> errors = new ArrayList<>();

        if (jsonBody == null || jsonBody.isBlank()) {
            errors.add("Response body is empty");
            return errors;
        }

        if (schemaJsonOrYaml == null || schemaJsonOrYaml.isBlank()) {
            errors.add("No schema provided for validation");
            return errors;
        }

        JsonNode bodyNode;
        try {
            bodyNode = objectMapper.readTree(jsonBody);
        } catch (Exception e) {
            errors.add("Response body is not valid JSON: " + e.getMessage());
            return errors;
        }

        JsonNode schemaNode;
        try {
            schemaNode = objectMapper.readTree(schemaJsonOrYaml);
        } catch (Exception e) {
            // Might be loosely defined schema, check if it has properties/type
            errors.add("Schema definition is not valid JSON: " + e.getMessage());
            return errors;
        }

        validateNode("", bodyNode, schemaNode, errors);
        return errors;
    }

    private void validateNode(String path, JsonNode dataNode, JsonNode schemaNode, List<String> errors) {
        if (schemaNode == null || dataNode == null) {
            return;
        }

        // 1. Type validation
        if (schemaNode.has("type")) {
            String expectedType = schemaNode.get("type").asText();
            if (!isTypeMatch(dataNode, expectedType)) {
                errors.add(String.format("Field '%s' expected type '%s' but found '%s'",
                        path.isEmpty() ? "root" : path, expectedType, getActualType(dataNode)));
                return;
            }
        }

        // 2. Object validation: required fields and property types
        if (dataNode.isObject()) {
            if (schemaNode.has("required") && schemaNode.get("required").isArray()) {
                for (JsonNode reqField : schemaNode.get("required")) {
                    String fieldName = reqField.asText();
                    if (!dataNode.has(fieldName) || dataNode.get(fieldName).isNull()) {
                        String fieldPath = path.isEmpty() ? fieldName : path + "." + fieldName;
                        errors.add(String.format("Missing required field '%s'", fieldPath));
                    }
                }
            }

            if (schemaNode.has("properties") && schemaNode.get("properties").isObject()) {
                JsonNode propertiesNode = schemaNode.get("properties");
                Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> prop = fields.next();
                    String propName = prop.getKey();
                    JsonNode propSchema = prop.getValue();

                    if (dataNode.has(propName)) {
                        String childPath = path.isEmpty() ? propName : path + "." + propName;
                        validateNode(childPath, dataNode.get(propName), propSchema, errors);
                    }
                }
            }
        }

        // 3. Array validation: items schema
        if (dataNode.isArray() && schemaNode.has("items") && schemaNode.get("items").isObject()) {
            JsonNode itemSchema = schemaNode.get("items");
            int index = 0;
            for (JsonNode item : dataNode) {
                String itemPath = path + "[" + index + "]";
                validateNode(itemPath, item, itemSchema, errors);
                index++;
            }
        }
    }

    private boolean isTypeMatch(JsonNode node, String expectedType) {
        return switch (expectedType.toLowerCase()) {
            case "string" -> node.isTextual();
            case "integer" -> node.isInt() || node.isLong() || node.isBigInteger();
            case "number" -> node.isNumber();
            case "boolean" -> node.isBoolean();
            case "array" -> node.isArray();
            case "object" -> node.isObject();
            default -> true;
        };
    }

    private String getActualType(JsonNode node) {
        if (node.isTextual()) return "string";
        if (node.isInt() || node.isLong() || node.isBigInteger()) return "integer";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isArray()) return "array";
        if (node.isObject()) return "object";
        if (node.isNull()) return "null";
        return "unknown";
    }
}
