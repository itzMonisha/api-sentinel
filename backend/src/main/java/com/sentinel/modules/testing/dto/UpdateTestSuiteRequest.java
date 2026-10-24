package com.sentinel.modules.testing.dto;

import jakarta.validation.constraints.Size;

public class UpdateTestSuiteRequest {

    @Size(max = 150, message = "Suite name must not exceed 150 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    public UpdateTestSuiteRequest() {
    }

    public UpdateTestSuiteRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
