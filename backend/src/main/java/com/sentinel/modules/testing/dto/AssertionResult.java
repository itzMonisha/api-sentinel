package com.sentinel.modules.testing.dto;

public class AssertionResult {

    private String type;
    private String property;
    private String expected;
    private String actual;
    private boolean passed;
    private String message;

    public AssertionResult() {
    }

    public AssertionResult(String type, String property, String expected, String actual, boolean passed, String message) {
        this.type = type;
        this.property = property;
        this.expected = expected;
        this.actual = actual;
        this.passed = passed;
        this.message = message;
    }

    public static AssertionResult pass(String type, String property, String expected, String actual, String message) {
        return new AssertionResult(type, property, expected, actual, true, message);
    }

    public static AssertionResult fail(String type, String property, String expected, String actual, String message) {
        return new AssertionResult(type, property, expected, actual, false, message);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
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

    public String getActual() {
        return actual;
    }

    public void setActual(String actual) {
        this.actual = actual;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
