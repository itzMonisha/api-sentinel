package com.sentinel.modules.testing.dto;

import com.sentinel.modules.testing.entity.AssertionType;

public class AssertionDefinition {

    private AssertionType type;
    private String property;
    private String expected;
    private String message;

    public AssertionDefinition() {
    }

    public AssertionDefinition(AssertionType type, String property, String expected, String message) {
        this.type = type;
        this.property = property;
        this.expected = expected;
        this.message = message;
    }

    public static AssertionDefinition of(AssertionType type, String property, String expected) {
        return new AssertionDefinition(type, property, expected, null);
    }

    public static AssertionDefinition of(AssertionType type, String expected) {
        return new AssertionDefinition(type, null, expected, null);
    }

    public AssertionType getType() {
        return type;
    }

    public void setType(AssertionType type) {
        this.type = type;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
