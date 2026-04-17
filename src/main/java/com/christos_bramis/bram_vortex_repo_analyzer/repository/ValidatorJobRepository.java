package com.christos_bramis.bram_vortex_repo_analyzer.repository;

import com.christos_bramis.bram_vortex_repo_analyzer.entity.ValidatorJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ValidatorJobRepository extends JpaRepository<ValidatorJob, String> {
}
