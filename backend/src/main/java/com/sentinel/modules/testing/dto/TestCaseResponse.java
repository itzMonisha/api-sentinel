package com.sentinel.modules.testing.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TestCaseResponse {

    private UUID id;
    private UUID testSuiteId;
    private UUID endpointId;
    private String endpointName;
    private String name;
    private int executionOrder;
    private String httpMethod;
    private String targetPath;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private String requestBody;
    private List<AssertionDefinition> assertions;
    private Instant createdAt;
    private Instant updatedAt;

    public TestCaseResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTestSuiteId() {
        return testSuiteId;
    }

    public void setTestSuiteId(UUID testSuiteId) {
        this.testSuiteId = testSuiteId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(UUID endpointId) {
        this.endpointId = endpointId;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public void setEndpointName(String endpointName) {
        this.endpointName = endpointName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getExecutionOrder() {
        return executionOrder;
    }

    public void setExecutionOrder(int executionOrder) {
        this.executionOrder = executionOrder;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(Map<String, String> queryParams) {
        this.queryParams = queryParams;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public List<AssertionDefinition> getAssertions() {
        return assertions;
    }

    public void setAssertions(List<AssertionDefinition> assertions) {
        this.assertions = assertions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
