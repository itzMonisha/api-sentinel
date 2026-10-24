package com.sentinel.modules.scanner.entity;

import com.sentinel.modules.project.entity.ApiEndpoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_findings")
public class SecurityFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_scan_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private SecurityScan securityScan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private ApiEndpoint endpoint;

    @Column(name = "rule_id", nullable = false, length = 100)
    private String ruleId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Confidence confidence = Confidence.CONFIRMED;

    @Column(name = "owasp_category", length = 100)
    private String owaspCategory;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String remediation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SecurityFinding() {
    }

    public SecurityFinding(
            SecurityScan securityScan,
            ApiEndpoint endpoint,
            String ruleId,
            String title,
            Severity severity,
            Confidence confidence,
            String owaspCategory,
            String description,
            String evidence,
            String remediation) {
        this.securityScan = securityScan;
        this.endpoint = endpoint;
        this.ruleId = ruleId;
        this.title = title;
        this.severity = severity;
        this.confidence = confidence != null ? confidence : Confidence.CONFIRMED;
        this.owaspCategory = owaspCategory;
        this.description = description;
        this.evidence = evidence;
        this.remediation = remediation;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public SecurityScan getSecurityScan() {
        return securityScan;
    }

    public void setSecurityScan(SecurityScan securityScan) {
        this.securityScan = securityScan;
    }

    public ApiEndpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(ApiEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public void setConfidence(Confidence confidence) {
        this.confidence = confidence;
    }

    public String getOwaspCategory() {
        return owaspCategory;
    }

    public void setOwaspCategory(String owaspCategory) {
        this.owaspCategory = owaspCategory;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getRemediation() {
        return remediation;
    }

    public void setRemediation(String remediation) {
        this.remediation = remediation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
