package com.christos_bramis.bram_vortex_repo_analyzer.controller;

import com.christos_bramis.bram_vortex_repo_analyzer.dto.AnalysisRequest;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.RepoResponse;
import com.christos_bramis.bram_vortex_repo_analyzer.entity.AnalysisJob;
import com.christos_bramis.bram_vortex_repo_analyzer.service.RepoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; // <--- Σωστό Import
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

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
     * */
     @PostMapping("/analyze")
     public ResponseEntity<String> startRepoAnalysis(
     Authentication auth,
     @RequestBody AnalysisRequest request) {

     String userId = auth.getName();

     // Πάρε ΜΟΝΟ το raw token string
     String token = auth.getCredentials().toString();

     System.out.println("🧠 [DASHBOARD] Analysis Request from User ID: " + userId);

     // Στείλε το ΚΑΘΑΡΟ token στο service
     String jobId = repoService.startAnalysis(userId, token, request);
     return ResponseEntity.ok(jobId);
     }
    /**
     * Endpoint 3: Επιστρέφει τις λεπτομέρειες ενός Job.
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<AnalysisJob> getAnalysisJob(@PathVariable String jobId, Authentication auth) {

        String currentUserId = auth.getName(); // Το ID από το JWT
        Optional<AnalysisJob> jobOpt = repoService.getAnalysisJob(jobId);

        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AnalysisJob job = jobOpt.get();

        // ΣΩΣΤΗ ΣΥΓΚΡΙΣΗ: String με String
        // Υποθέτω ότι το AnalysisJob έχει μέθοδο getUserId() ή getOwnerId()
        if (!job.getUserId().equals(currentUserId)) {
            System.err.println("⛔ Security Breach: User " + currentUserId + " tried to access job of " + job.getUserId());
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

    @GetMapping("/download/{jobId}")
    public ResponseEntity<byte[]> downloadMasterZip(@PathVariable String jobId, Authentication auth) {
        // 1. Πάρε το ID του συνδεδεμένου χρήστη
        String currentUserId = auth.getName();

        return repoService.getAnalysisJob(jobId)
                .map(job -> {
                    // 2. SECURITY CHECK: Είναι ο ιδιοκτήτης;
                    if (!job.getUserId().equals(currentUserId)) {
                        System.err.println("⛔ [DOWNLOAD REJECTED] User " + currentUserId + " tried to steal job " + jobId);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<byte[]>build();
                    }

                    byte[] zipContent = job.getMasterZip();

                    // 3. Έλεγχος αν το BLOB είναι άδειο
                    if (zipContent == null || zipContent.length == 0) {
                        System.err.println("⚠️ [DOWNLOAD] Job found but Master ZIP is empty for ID: " + jobId);
                        return ResponseEntity.status(HttpStatus.NO_CONTENT).<byte[]>build();
                    }

                    System.out.println("✅ [DOWNLOAD] Serving ZIP for Job: " + jobId + " (Size: " + zipContent.length + " bytes)");

                    // 4. Επιστροφή αρχείου
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vortex-package-" + jobId + ".zip\"")
                            .contentType(MediaType.parseMediaType("application/zip"))
                            .contentLength(zipContent.length)
                            .body(zipContent);
                })
                .orElseGet(() -> {
                    System.err.println("❌ [DOWNLOAD] Job ID not found: " + jobId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                });
    }
}