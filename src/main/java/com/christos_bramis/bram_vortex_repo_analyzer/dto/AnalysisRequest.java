package com.christos_bramis.bram_vortex_repo_analyzer.dto;

public class AnalysisRequest {

    private long repoId;
    private String repoName;
    private String repoUrl;
    private String targetCloud;
    private String computeType;
    private String targetRegion;

    // Απαραίτητος Κενός Constructor
    public AnalysisRequest() {
    }

    // Full Constructor
    public AnalysisRequest(long repoId, String repoName, String repoUrl, String cloudProvider, String computeType, String targetRegion) {
        this.repoId = repoId;
        this.repoName = repoName;
        this.repoUrl = repoUrl;
        this.targetCloud = cloudProvider;
        this.computeType = computeType;
        this.targetRegion = targetRegion;
    } //

    // Getters & Setters
    public long getRepoId() {
        return repoId;
    }

    public void setRepoId(long repoId) {
        this.repoId = repoId;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getTargetCloud() {
        return targetCloud;
    }

    public void setTargetCloud(String targetCloud) {
        this.targetCloud = targetCloud;
    }

    public String getComputeType() {
        return computeType;
    }

    public void setComputeType(String computeType) {
        this.computeType = computeType;
    }

    public String getTargetRegion() {
        return targetRegion;
    }

    public void setTargetRegion(String targetRegion) {
        this.targetRegion = targetRegion;
    }
}