package com.christos_bramis.bram_vortex_repo_analyzer.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob {

    @Id
    @Column(name = "job_id", nullable = false, updatable = false)
    private String jobId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "repo_id")
    private Long repoId;

    @Column(name = "repo_name")
    private String repoName;

    @Column(name = "target_cloud")
    private String targetCloud;

    @Column(name = "compute_type")
    private String computeType;

    public String getTargetRegion() {
        return targetRegion;
    }

    public void setTargetRegion(String targetRegion) {
        this.targetRegion = targetRegion;
    }

    @Column(name = "targetRegion")
    private String targetRegion;

    @Column(name = "status", nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blueprint_json", columnDefinition = "jsonb")
    private JsonNode blueprintJson;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    private String promptMessage;

    @Column(name = "terraform_status")
    private String terraformStatus = "PENDING";

    @Column(name = "ansible_status")
    private String ansibleStatus = "PENDING";

    @Column(name = "pipeline_status")
    private String pipelineStatus = "PENDING";

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "master_zip", columnDefinition = "bytea")
    private byte[] masterZip;

    // --- Constructors ---
    public AnalysisJob() {}

    // --- Getters & Setters ---
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Long getRepoId() { return repoId; }
    public void setRepoId(Long repoId) { this.repoId = repoId; }

    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }

    public String getTargetCloud() { return targetCloud; }
    public void setTargetCloud(String targetCloud) { this.targetCloud = targetCloud; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public JsonNode getBlueprintJson() { return blueprintJson; }
    public void setBlueprintJson(JsonNode blueprintJson) { this.blueprintJson = blueprintJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public String getPromptMessage() {
        return promptMessage;
    }

    public void setPromptMessage(String promptMessage) {
        this.promptMessage = promptMessage;
    }

    public String getComputeType() {
        return computeType;
    }

    public void setComputeType(String computeType) {
        this.computeType = computeType;
    }

    public String getTerraformStatus() {
        return terraformStatus;
    }

    public void setTerraformStatus(String terraformStatus) {
        this.terraformStatus = terraformStatus;
    }

    public String getAnsibleStatus() {
        return ansibleStatus;
    }

    public void setAnsibleStatus(String ansibleStatus) {
        this.ansibleStatus = ansibleStatus;
    }

    public String getPipelineStatus() {
        return pipelineStatus;
    }

    public void setPipelineStatus(String pipelineStatus) {
        this.pipelineStatus = pipelineStatus;
    }

    public byte[] getMasterZip() {
        return masterZip;
    }

    public void setMasterZip(byte[] masterZip) {
        this.masterZip = masterZip;
    }
}