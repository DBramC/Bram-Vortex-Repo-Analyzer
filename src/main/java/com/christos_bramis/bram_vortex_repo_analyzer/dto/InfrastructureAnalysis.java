package com.christos_bramis.bram_vortex_repo_analyzer.dto;

import java.util.List;

public class InfrastructureAnalysis {

    // Βασικά Χαρακτηριστικά Εφαρμογής
    private String applicationType;
    private String primaryLanguage;
    private String framework;

    // Εξαρτήσεις
    private List<String> requiredDatabasesAndCaches;

    // Προδιαγραφές Υποδομής (Terraform / K8s)
    private String targetCloud;
    private String recommendedCompute;
    private Integer recommendedContainerPort;

    // Προδιαγραφές CI/CD (Pipelines)
    private List<String> ciCdBuildSteps;

    // Προδιαγραφές Παρακολούθησης (Monitoring)
    private List<String> monitoringMetrics;

    // Απαραίτητος κενός Constructor για το Spring AI / JSON Parsing
    public InfrastructureAnalysis() {
    }

    // --- Getters & Setters ---

    public String getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(String applicationType) {
        this.applicationType = applicationType;
    }

    public String getPrimaryLanguage() {
        return primaryLanguage;
    }

    public void setPrimaryLanguage(String primaryLanguage) {
        this.primaryLanguage = primaryLanguage;
    }

    public String getFramework() {
        return framework;
    }

    public void setFramework(String framework) {
        this.framework = framework;
    }

    public List<String> getRequiredDatabasesAndCaches() {
        return requiredDatabasesAndCaches;
    }

    public void setRequiredDatabasesAndCaches(List<String> requiredDatabasesAndCaches) {
        this.requiredDatabasesAndCaches = requiredDatabasesAndCaches;
    }

    public String getTargetCloud() {
        return targetCloud;
    }

    public void setTargetCloud(String targetCloud) {
        this.targetCloud = targetCloud;
    }

    public String getRecommendedCompute() {
        return recommendedCompute;
    }

    public void setRecommendedCompute(String recommendedCompute) {
        this.recommendedCompute = recommendedCompute;
    }

    public Integer getRecommendedContainerPort() {
        return recommendedContainerPort;
    }

    public void setRecommendedContainerPort(Integer recommendedContainerPort) {
        this.recommendedContainerPort = recommendedContainerPort;
    }

    public List<String> getCiCdBuildSteps() {
        return ciCdBuildSteps;
    }

    public void setCiCdBuildSteps(List<String> ciCdBuildSteps) {
        this.ciCdBuildSteps = ciCdBuildSteps;
    }

    public List<String> getMonitoringMetrics() {
        return monitoringMetrics;
    }

    public void setMonitoringMetrics(List<String> monitoringMetrics) {
        this.monitoringMetrics = monitoringMetrics;
    }
}