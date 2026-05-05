package com.christos_bramis.bram_vortex_repo_analyzer.dto;

import java.util.Map;

public class AnalysisCosts {
    private String jobId;
    private Map<String, Double> costEstimates; // Εδώ θα είναι τα: "Virtual Machine": 25.0, κτλ.

    public AnalysisCosts(String jobId, Map<String, Double> costEstimates) {
        this.jobId = jobId;
        this.costEstimates = costEstimates;
    }

    // Getters
    public String getJobId() { return jobId; }
    public Map<String, Double> getCostEstimates() { return costEstimates; }
}