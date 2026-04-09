package com.christos_bramis.bram_vortex_repo_analyzer.controller;

import com.christos_bramis.bram_vortex_repo_analyzer.dto.AnalysisRequest;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.RepoResponse;
import com.christos_bramis.bram_vortex_repo_analyzer.entity.AnalysisJob;
import com.christos_bramis.bram_vortex_repo_analyzer.service.RepoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; // <--- Σωστό Import
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
     */
    @GetMapping("/repos")
    public ResponseEntity<List<RepoResponse>> getAllRepositories(Authentication auth) {

        if (auth == null) {
            return ResponseEntity.status(401).build();
        }

        String userId = auth.getName();
        System.out.println("🚀 [DASHBOARD] Fetching repos for User ID: " + userId);

        List<RepoResponse> repos = repoService.getUserRepositories(userId);
        return ResponseEntity.ok(repos);
    }

    /**
     * Endpoint 2: Ξεκινάει την ανάλυση.
     * Εδώ παίρνουμε το Token από τα credentials για το Token Propagation.
     */
    @PostMapping("/analyze")
    public ResponseEntity<String> startRepoAnalysis(
            Authentication auth, // <--- Χρησιμοποιούμε το γενικό Authentication object
            @RequestBody AnalysisRequest request) {

        String userId = auth.getName();

        /* * Παίρνουμε το token που αποθηκεύσαμε στο JwtAuthenticationFilter.
         * Πλέον δεν θα φας IllegalStateException!
         */
        String token = "Bearer " + auth.getCredentials().toString();

        System.out.println("🧠 [DASHBOARD] Analysis Request from User ID: " + userId);

        // Στέλνουμε το userId και το token στο service
        String jobId = repoService.startAnalysis(userId, token, request);
        return ResponseEntity.ok(jobId);
    }

    /**
     * Endpoint 3: Επιστρέφει τις λεπτομέρειες ενός Job.
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<AnalysisJob> getAnalysisJob(@PathVariable String jobId, Authentication auth) {

        String userId = auth.getName();
        AnalysisJob job = repoService.getAnalysisJob(jobId);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        // Security Check: Μόνο ο ιδιοκτήτης βλέπει το Job του
        if (!job.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(job);
    }

    @PostMapping("/internal/callback/{analysisJobId}")
    public ResponseEntity<Void> receiveServiceCallback(
            @PathVariable String analysisJobId,
            @RequestParam String service,
            @RequestParam String status) {

        System.out.println("📥 [WEBHOOK RECEIVED] Job: " + analysisJobId + " | Service: " + service + " | Status: " + status);

        try {
            // Καλούμε τη μέθοδο του Service που αναλαμβάνει το Orchestration
            repoService.handleServiceCallback(analysisJobId, service, status);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("❌ [WEBHOOK ERROR] Failed to process callback: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}