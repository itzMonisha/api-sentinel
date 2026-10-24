package com.sentinel.modules.monitoring.dto;

public class ResolveIncidentRequest {

    private String rootCause;

    public ResolveIncidentRequest() {
    }

    public ResolveIncidentRequest(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }
}
