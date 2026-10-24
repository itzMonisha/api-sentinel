package com.sentinel.modules.testing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreateTestCaseRequest {

    private UUID endpointId;

    @NotBlank(message = "Case name is required")
    @Size(max = 150, message = "Case name must not exceed 150 characters")
    private String name;

    private int executionOrder = 0;

    private String httpMethod = "GET";

    @NotBlank(message = "Target path or URL is required")
    @Size(max = 500, message = "Target path must not exceed 500 characters")
    private String targetPath;

    private Map<String, String> headers = new HashMap<>();

    private Map<String, String> queryParams = new HashMap<>();

    private String requestBody;

    private List<AssertionDefinition> assertions = new ArrayList<>();

    public CreateTestCaseRequest() {
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(UUID endpointId) {
        this.endpointId = endpointId;
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
}
