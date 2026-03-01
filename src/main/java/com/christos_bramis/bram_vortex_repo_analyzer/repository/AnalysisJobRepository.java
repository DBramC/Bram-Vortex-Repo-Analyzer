package com.christos_bramis.bram_vortex_repo_analyzer.repository;

import com.christos_bramis.bram_vortex_repo_analyzer.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {

    // Έτοιμη μέθοδος για να φέρνεις το ιστορικό αναλύσεων ενός χρήστη!
    List<AnalysisJob> findByUserIdOrderByCreatedAtDesc(String userId);
}