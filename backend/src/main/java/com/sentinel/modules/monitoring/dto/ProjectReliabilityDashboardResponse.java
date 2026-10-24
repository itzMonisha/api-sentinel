package com.sentinel.modules.monitoring.dto;

import java.util.UUID;

public class ProjectReliabilityDashboardResponse {

    private UUID projectId;
    private String projectName;
    private int totalMonitoredEndpoints;
    private int healthyEndpoints;
    private int degradedEndpoints;
    private int downEndpoints;
    private Double overallUptimePercentage;
    private int activeIncidentsCount;
    private int totalIncidents;
    private int unreadAlertsCount;

    public ProjectReliabilityDashboardResponse() {
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public int getTotalMonitoredEndpoints() {
        return totalMonitoredEndpoints;
    }

    public void setTotalMonitoredEndpoints(int totalMonitoredEndpoints) {
        this.totalMonitoredEndpoints = totalMonitoredEndpoints;
    }

    public int getHealthyEndpoints() {
        return healthyEndpoints;
    }

    public void setHealthyEndpoints(int healthyEndpoints) {
        this.healthyEndpoints = healthyEndpoints;
    }

    public int getDegradedEndpoints() {
        return degradedEndpoints;
    }

    public void setDegradedEndpoints(int degradedEndpoints) {
        this.degradedEndpoints = degradedEndpoints;
    }

    public int getDownEndpoints() {
        return downEndpoints;
    }

    public void setDownEndpoints(int downEndpoints) {
        this.downEndpoints = downEndpoints;
    }

    public Double getOverallUptimePercentage() {
        return overallUptimePercentage;
    }

    public void setOverallUptimePercentage(Double overallUptimePercentage) {
        this.overallUptimePercentage = overallUptimePercentage;
    }

    public int getActiveIncidentsCount() {
        return activeIncidentsCount;
    }

    public void setActiveIncidentsCount(int activeIncidentsCount) {
        this.activeIncidentsCount = activeIncidentsCount;
    }

    public int getTotalIncidents() {
        return totalIncidents;
    }

    public void setTotalIncidents(int totalIncidents) {
        this.totalIncidents = totalIncidents;
    }

    public int getUnreadAlertsCount() {
        return unreadAlertsCount;
    }

    public void setUnreadAlertsCount(int unreadAlertsCount) {
        this.unreadAlertsCount = unreadAlertsCount;
    }
}
