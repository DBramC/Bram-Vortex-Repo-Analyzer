package com.christos_bramis.bram_vortex_repo_analyzer.repository;

import com.christos_bramis.bram_vortex_repo_analyzer.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {

    // Φέρνει το ιστορικό αναλύσεων ενός χρήστη
    List<AnalysisJob> findByUserIdOrderByCreatedAtDesc(String userId);

    // --- ATOMIC STATUS UPDATES (Για αποφυγή Race Conditions) ---

    @Modifying
    @Transactional
    @Query("UPDATE AnalysisJob j SET j.terraformStatus = :status WHERE j.jobId = :jobId")
    void updateTerraformStatus(@Param("jobId") String jobId, @Param("status") String status);

    @Modifying
    @Transactional
    @Query("UPDATE AnalysisJob j SET j.ansibleStatus = :status WHERE j.jobId = :jobId")
    void updateAnsibleStatus(@Param("jobId") String jobId, @Param("status") String status);

    @Modifying
    @Transactional
    @Query("UPDATE AnalysisJob j SET j.pipelineStatus = :status WHERE j.jobId = :jobId")
    void updatePipelineStatus(@Param("jobId") String jobId, @Param("status") String status);

    @Modifying
    @Transactional
    @Query("UPDATE AnalysisJob j SET j.validatorStatus = :status WHERE j.jobId = :jobId")
    void updateValidatorStatus(@Param("jobId") String jobId, @Param("status") String status);

    // --- ZIP FETCHING QUERIES (Native) ---

    @Query(value = "SELECT terraform_zip FROM terraform_jobs WHERE analysis_job_id = :jobId", nativeQuery = true)
    byte[] findTerraformZip(@Param("jobId") String jobId);

    @Query(value = "SELECT ansible_zip FROM ansible_jobs WHERE analysis_job_id = :jobId", nativeQuery = true)
    byte[] findAnsibleZip(@Param("jobId") String jobId);

    @Query(value = "SELECT pipeline_zip FROM pipeline_jobs WHERE analysis_job_id = :jobId", nativeQuery = true)
    byte[] findPipelineZip(@Param("jobId") String jobId);
}