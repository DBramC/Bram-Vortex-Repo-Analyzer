package com.christos_bramis.bram_vortex_repo_analyzer.dto;

import java.util.List;
import java.util.Map;

public class InfrastructureAnalysis {
    private String applicationType;
    private String primaryLanguage;
    private String framework;
    private List<String> requiredDatabasesAndCaches;
    private String targetCloud;

    // Μετονομασία σύμφωνα με τις οδηγίες σου
    private String targetCompute;
    private int targetContainerPort;

    // Νέο πεδίο για να κρατάει τα settings (application.properties / .env)
    private Map<String, String> configurationSettings;

    private List<String> buildSteps;
    private List<String> monitoringMetrics;

    // --- Getters & Setters ---
    public String getApplicationType() { return applicationType; }
    public void setApplicationType(String applicationType) { this.applicationType = applicationType; }

    public String getPrimaryLanguage() { return primaryLanguage; }
    public void setPrimaryLanguage(String primaryLanguage) { this.primaryLanguage = primaryLanguage; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public List<String> getRequiredDatabasesAndCaches() { return requiredDatabasesAndCaches; }
    public void setRequiredDatabasesAndCaches(List<String> requiredDatabasesAndCaches) { this.requiredDatabasesAndCaches = requiredDatabasesAndCaches; }

    public String getTargetCloud() { return targetCloud; }
    public void setTargetCloud(String targetCloud) { this.targetCloud = targetCloud; }

    public String getTargetCompute() { return targetCompute; }
    public void setTargetCompute(String targetCompute) { this.targetCompute = targetCompute; }

    public int getTargetContainerPort() { return targetContainerPort; }
    public void setTargetContainerPort(int targetContainerPort) { this.targetContainerPort = targetContainerPort; }

    public Map<String, String> getConfigurationSettings() { return configurationSettings; }
    public void setConfigurationSettings(Map<String, String> configurationSettings) { this.configurationSettings = configurationSettings; }

    public List<String> getBuildSteps() { return buildSteps; }
    public void setBuildSteps(List<String> buildSteps) { this.buildSteps = buildSteps; }

    public List<String> getMonitoringMetrics() { return monitoringMetrics; }
    public void setMonitoringMetrics(List<String> monitoringMetrics) { this.monitoringMetrics = monitoringMetrics; }
}