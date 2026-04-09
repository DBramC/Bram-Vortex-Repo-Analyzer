package com.christos_bramis.bram_vortex_repo_analyzer.dto;

import java.util.List;
import java.util.Map;

public class InfrastructureAnalysis {

    // --- Βασικά Χαρακτηριστικά Εφαρμογής ---
    private String applicationType;
    private String primaryLanguage;
    private String framework;
    private List<String> requiredDatabasesAndCaches;

    // --- Προδιαγραφές Υποδομής ---
    private Map<String, String> computeSpecs;
    private String targetCloud;
    private String targetCompute;
    private String computeCategory;
    private int targetContainerPort;
    private Map<String, String> configurationSettings;
    private List<String> buildSteps;
    private List<String> monitoringMetrics;

    // --- ΕΞΥΠΝΗ ΕΠΙΛΟΓΗ ΑΡΧΙΤΕΚΤΟΝΙΚΗΣ (ΝΕΟ) ---
    private List<String> validComputeTypes;
    private String computeReasoning;

    // --- Εξειδικευμένα Metadata ---
    private DeploymentMetadata deploymentMetadata;
    private CiCdMetadata ciCdMetadata;

    // ==========================================
    // INNER CLASSES
    // ==========================================

    /**
     * Δεδομένα που απαιτούνται από το Pipeline Generator
     * για να στήσει το GitHub Actions CI/CD.
     */
    public static class CiCdMetadata {
        private String buildTool;
        private String languageVersion;
        private List<String> buildCommands;
        private List<String> testCommands;
        private String artifactPath;
        private boolean hasDockerfile;

        // Getters & Setters
        public String getBuildTool() { return buildTool; }
        public void setBuildTool(String buildTool) { this.buildTool = buildTool; }

        public String getLanguageVersion() { return languageVersion; }
        public void setLanguageVersion(String languageVersion) { this.languageVersion = languageVersion; }

        public List<String> getBuildCommands() { return buildCommands; }
        public void setBuildCommands(List<String> buildCommands) { this.buildCommands = buildCommands; }

        public List<String> getTestCommands() { return testCommands; }
        public void setTestCommands(List<String> testCommands) { this.testCommands = testCommands; }

        public String getArtifactPath() { return artifactPath; }
        public void setArtifactPath(String artifactPath) { this.artifactPath = artifactPath; }

        public boolean isHasDockerfile() { return hasDockerfile; }
        public void setHasDockerfile(boolean hasDockerfile) { this.hasDockerfile = hasDockerfile; }
    }

    /**
     * Δεδομένα που απαιτούνται από το Ansible Generator
     * αποκλειστικά για την περίπτωση Virtual Machine (OS Configuration).
     */
    public static class DeploymentMetadata {
        private String osDistro;
        private String executionUser;
        private String jvmArgs;
        private String healthCheckEndpoint;

        // Getters & Setters
        public String getOsDistro() { return osDistro; }
        public void setOsDistro(String osDistro) { this.osDistro = osDistro; }

        public String getExecutionUser() { return executionUser; }
        public void setExecutionUser(String executionUser) { this.executionUser = executionUser; }

        public String getJvmArgs() { return jvmArgs; }
        public void setJvmArgs(String jvmArgs) { this.jvmArgs = jvmArgs; }

        public String getHealthCheckEndpoint() { return healthCheckEndpoint; }
        public void setHealthCheckEndpoint(String healthCheckEndpoint) { this.healthCheckEndpoint = healthCheckEndpoint; }
    }

    // ==========================================
    // GETTERS & SETTERS (Main Class)
    // ==========================================

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

    public String getComputeCategory() { return computeCategory; }
    public void setComputeCategory(String computeCategory) { this.computeCategory = computeCategory; }

    public int getTargetContainerPort() { return targetContainerPort; }
    public void setTargetContainerPort(int targetContainerPort) { this.targetContainerPort = targetContainerPort; }

    public Map<String, String> getConfigurationSettings() { return configurationSettings; }
    public void setConfigurationSettings(Map<String, String> configurationSettings) { this.configurationSettings = configurationSettings; }

    public List<String> getBuildSteps() { return buildSteps; }
    public void setBuildSteps(List<String> buildSteps) { this.buildSteps = buildSteps; }

    public List<String> getMonitoringMetrics() { return monitoringMetrics; }
    public void setMonitoringMetrics(List<String> monitoringMetrics) { this.monitoringMetrics = monitoringMetrics; }

    // --- Νέα Getters/Setters ---

    public List<String> getValidComputeTypes() { return validComputeTypes; }
    public void setValidComputeTypes(List<String> validComputeTypes) { this.validComputeTypes = validComputeTypes; }

    public String getComputeReasoning() { return computeReasoning; }
    public void setComputeReasoning(String computeReasoning) { this.computeReasoning = computeReasoning; }

    public DeploymentMetadata getDeploymentMetadata() { return deploymentMetadata; }
    public void setDeploymentMetadata(DeploymentMetadata deploymentMetadata) { this.deploymentMetadata = deploymentMetadata; }

    public CiCdMetadata getCiCdMetadata() { return ciCdMetadata; }
    public void setCiCdMetadata(CiCdMetadata ciCdMetadata) { this.ciCdMetadata = ciCdMetadata; }
}