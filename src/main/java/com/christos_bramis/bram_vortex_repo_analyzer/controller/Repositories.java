package com.christos_bramis.bram_vortex_repo_analyzer.controller;

import com.christos_bramis.bram_vortex_repo_analyzer.dto.AnalysisRequest;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.RepoResponse;
import com.christos_bramis.bram_vortex_repo_analyzer.service.RepoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
public class Repositories {

    private final RepoService repoService;

    public Repositories(RepoService repoService) {
        this.repoService = repoService;
    }

    /**
     * Endpoint 1: Φέρνει όλα τα Repositories.
     * Το userId το παίρνουμε αυτόματα από το JWT (μέσω του @AuthenticationPrincipal).
     */
    @GetMapping("/repos")
    public ResponseEntity<List<RepoResponse>> getAllRepositories(
            @AuthenticationPrincipal String userId) { // <--- Η ΑΛΛΑΓΗ ΕΙΝΑΙ ΕΔΩ

        // Αμυντικός έλεγχος (αν για κάποιο λόγο το filter δεν δούλεψε σωστά)
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        System.out.println("Authenticated Request for User ID: " + userId);

        List<RepoResponse> repos = repoService.getUserRepositories(userId);
        return ResponseEntity.ok(repos);
    }

    /**
     * Endpoint 2: Ξεκινάει την ανάλυση.
     * Ομοίως, παίρνουμε το userId από το Token.
     */
    @PostMapping("/analyze")
    public ResponseEntity<String> startRepoAnalysis(
            @AuthenticationPrincipal String userId, // <--- Η ΑΛΛΑΓΗ ΕΙΝΑΙ ΕΔΩ
            @RequestBody AnalysisRequest request) {

        System.out.println("Analysis Request from User ID: " + userId);

        String jobId = repoService.startAnalysis(userId, request);

        return ResponseEntity.accepted().body("Analysis started successfully. Job ID: " + jobId);
    }
}