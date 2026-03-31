package com.christos_bramis.bram_vortex_repo_analyzer.dto;

import java.util.List;
import java.util.Map;

public class InfrastructureAnalysis {
    private String applicationType;
    private String primaryLanguage;
    private String framework;
    private List<String> requiredDatabasesAndCaches;
    private Map<String, String> computeSpecs;
    private String targetCloud;
    private String targetCompute;
    private String computeCategory;
    private int targetContainerPort;
    private Map<String, String> configurationSettings;
    private List<String> buildSteps;
    private List<String> monitoringMetrics;

    // Το νέο πεδίο για την Ansible υποστήριξη
    private DeploymentMetadata deploymentMetadata;

    // --- Inner Class για το Metadata ---
    public static class DeploymentMetadata {
        private String osDistro;
        private String javaVersion;
        private String executionUser;
        private String jvmArgs;
        private String healthCheckEndpoint;

        // Getters & Setters για την εσωτερική κλάση
        public String getOsDistro() { return osDistro; }
        public void setOsDistro(String osDistro) { this.osDistro = osDistro; }

        public String getJavaVersion() { return javaVersion; }
        public void setJavaVersion(String javaVersion) { this.javaVersion = javaVersion; }

        public String getExecutionUser() { return executionUser; }
        public void setExecutionUser(String executionUser) { this.executionUser = executionUser; }

        public String getJvmArgs() { return jvmArgs; }
        public void setJvmArgs(String jvmArgs) { this.jvmArgs = jvmArgs; }

        public String getHealthCheckEndpoint() { return healthCheckEndpoint; }
        public void setHealthCheckEndpoint(String healthCheckEndpoint) { this.healthCheckEndpoint = healthCheckEndpoint; }
    }

    // --- Getters & Setters για την κεντρική κλάση ---
    public String getApplicationType() { return applicationType; }
    public void setApplicationType(String applicationType) { this.applicationType = applicationType; }

    public String getPrimaryLanguage() { return primaryLanguage; }
    public void setPrimaryLanguage(String primaryLanguage) { this.primaryLanguage = primaryLanguage; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public List<String> getRequiredDatabasesAndCaches() { return requiredDatabasesAndCaches; }
    public void setRequiredDatabasesAndCaches(List<String> requiredDatabasesAndCaches) { this.requiredDatabasesAndCaches = requiredDatabasesAndCaches; }

    public Map<String, String> getComputeSpecs() { return computeSpecs; }
    public void setComputeSpecs(Map<String, String> computeSpecs) { this.computeSpecs = computeSpecs; }

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

    public DeploymentMetadata getDeploymentMetadata() { return deploymentMetadata; }
    public void setDeploymentMetadata(DeploymentMetadata deploymentMetadata) { this.deploymentMetadata = deploymentMetadata; }

    public String getComputeCategory() {
        return computeCategory;
    }

    public void setComputeCategory(String computeCategory) {
        this.computeCategory = computeCategory;
    }
}