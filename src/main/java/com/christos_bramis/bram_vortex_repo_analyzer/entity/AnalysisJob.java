package com.christos_bramis.bram_vortex_repo_analyzer.entity;

import jakarta.persistence.*;
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

    // Εδώ είναι η μαγεία της PostgreSQL! Αποθηκεύουμε το JSON σε μορφή jsonb ή text
    @Column(name = "blueprint_json", columnDefinition = "jsonb")
    private String blueprintJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    private String promptMessage;

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

    public String getBlueprintJson() { return blueprintJson; }
    public void setBlueprintJson(String blueprintJson) { this.blueprintJson = blueprintJson; }

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
}