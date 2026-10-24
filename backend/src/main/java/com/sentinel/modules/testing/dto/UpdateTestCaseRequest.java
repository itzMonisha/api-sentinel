package com.sentinel.modules.testing.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UpdateTestCaseRequest {

    private UUID endpointId;

    @Size(max = 150, message = "Case name must not exceed 150 characters")
    private String name;

    private Integer executionOrder;

    private String httpMethod;

    @Size(max = 500, message = "Target path must not exceed 500 characters")
    private String targetPath;

    private Map<String, String> headers;

    private Map<String, String> queryParams;

    private String requestBody;

    private List<AssertionDefinition> assertions;

    public UpdateTestCaseRequest() {
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

    public Integer getExecutionOrder() {
        return executionOrder;
    }

    public void setExecutionOrder(Integer executionOrder) {
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
