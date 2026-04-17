package com.christos_bramis.bram_vortex_repo_analyzer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "validator_jobs")
public class ValidatorJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String jobId;

    @Column(name = "analysis_job_id", nullable = false)
    private String analysisJobId;

    // Εδώ βάζουμε τη στήλη για το ZIP που συζητήσαμε
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "validated_master_zip", columnDefinition = "bytea")
    private byte[] validatedMasterZip;

    // --- Constructors ---
    public ValidatorJob() {
    }

    // --- Getters & Setters ---
    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public byte[] getValidatedMasterZip() {
        return validatedMasterZip;
    }

    public void setValidatedMasterZip(byte[] validatedMasterZip) {
        this.validatedMasterZip = validatedMasterZip;
    }

    public String getAnalysisJobId() {
        return analysisJobId;
    }

    public void setAnalysisJobId(String analysisJobId) {
        this.analysisJobId = analysisJobId;
    }
}