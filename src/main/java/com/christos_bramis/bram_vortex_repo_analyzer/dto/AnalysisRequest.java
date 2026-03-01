package com.christos_bramis.bram_vortex_repo_analyzer.dto;

public class AnalysisRequest {

    private long repoId;
    private String repoName;
    private String repoUrl;
    private String cloudProvider; // <-- ΝΕΟ ΠΕΔΙΟ

    // Απαραίτητος Κενός Constructor
    public AnalysisRequest() {
    }

    // Full Constructor
    public AnalysisRequest(long repoId, String repoName, String repoUrl, String cloudProvider) {
        this.repoId = repoId;
        this.repoName = repoName;
        this.repoUrl = repoUrl;
        this.cloudProvider = cloudProvider;
    }

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

    public String getCloudProvider() {
        return cloudProvider;
    }

    public void setCloudProvider(String cloudProvider) {
        this.cloudProvider = cloudProvider;
    }
}