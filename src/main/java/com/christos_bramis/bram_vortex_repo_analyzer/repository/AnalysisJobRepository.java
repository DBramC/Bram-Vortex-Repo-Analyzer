package com.christos_bramis.bram_vortex_repo_analyzer.repository;

import com.christos_bramis.bram_vortex_repo_analyzer.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {

    // Έτοιμη μέθοδος για να φέρνεις το ιστορικό αναλύσεων ενός χρήστη!
    List<AnalysisJob> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query(value = "SELECT terraform_zip FROM terraform_jobs WHERE analysis_job_id = :jobId", nativeQuery = true)
    byte[] findTerraformZip(@Param("jobId") String jobId);

    @Query(value = "SELECT ansible_zip FROM ansible_jobs WHERE analysis_job_id = :jobId", nativeQuery = true)
    byte[] findAnsibleZip(@Param("jobId") String jobId);

    @Query(value = "SELECT pipeline_zip FROM pipeline_jobs WHERE analysis_job_id = :jobId", nativeQuery = true)
    byte[] findPipelineZip(@Param("jobId") String jobId);
}