package com.sentinel.modules.scanner.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreateScanRequest {

    private UUID projectId;
    private List<UUID> endpointIds;
    private Map<String, String> testFixtureAuth;

    public CreateScanRequest() {
    }

    public CreateScanRequest(UUID projectId, List<UUID> endpointIds, Map<String, String> testFixtureAuth) {
        this.projectId = projectId;
        this.endpointIds = endpointIds;
        this.testFixtureAuth = testFixtureAuth;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public List<UUID> getEndpointIds() {
        return endpointIds;
    }

    public void setEndpointIds(List<UUID> endpointIds) {
        this.endpointIds = endpointIds;
    }

    public Map<String, String> getTestFixtureAuth() {
        return testFixtureAuth;
    }

    public void setTestFixtureAuth(Map<String, String> testFixtureAuth) {
        this.testFixtureAuth = testFixtureAuth;
    }
}
