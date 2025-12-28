package com.christos_bramis.bram_vortex_repo_analyzer.controller;

import com.christos_bramis.bram_vortex_repo_analyzer.dto.AnalysisRequest;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.RepoResponse;
import com.christos_bramis.bram_vortex_repo_analyzer.service.RepoService;
import org.springframework.http.ResponseEntity;
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
     * Endpoint 1: Φέρνει όλα τα Repositories του χρήστη.
     * URL: GET /dashboard/repos
     * Header: X-User-Id (το στέλνει το Gateway ή το Frontend)
     */
    @GetMapping("/repos")
    public ResponseEntity<List<RepoResponse>> getAllRepositories(
            @RequestHeader("X-User-Id") String userId) {

        // Καλεί το service, το οποίο μιλάει με Vault -> GitHub
        List<RepoResponse> repos = repoService.getUserRepositories(userId);

        return ResponseEntity.ok(repos);
    }

    /**
     * Endpoint 2: Ξεκινάει την ανάλυση για το επιλεγμένο Repository.
     * URL: POST /dashboard/analyze
     * Body: { "repoId": 123, "repoName": "...", "repoUrl": "..." }
     */
    @PostMapping("/analyze")
    public ResponseEntity<String> startRepoAnalysis(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody AnalysisRequest request) {

        // Ξεκινάει το workflow
        String jobId = repoService.startAnalysis(userId, request);

        // Επιστρέφει 202 Accepted (σημαίνει "το έλαβα και θα το επεξεργαστώ")
        return ResponseEntity.accepted().body("Analysis started successfully. Job ID: " + jobId);
    }
}