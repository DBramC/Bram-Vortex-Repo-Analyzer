package com.christos_bramis.bram_vortex_repo_analyzer.controller;

import com.christos_bramis.bram_vortex_repo_analyzer.dto.AnalysisRequest;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.FileDiffResponse;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.InfrastructureAnalysis;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.RepoResponse;
import com.christos_bramis.bram_vortex_repo_analyzer.entity.AnalysisJob;
import com.christos_bramis.bram_vortex_repo_analyzer.service.RepoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; // <--- Σωστό Import
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
            Authentication auth,
            @RequestParam String status) {

        String userId = auth.getName();

        // Πάρε ΜΟΝΟ το raw token string
        String token = auth.getCredentials().toString();

        System.out.println("📥 [WEBHOOK RECEIVED] Job: " + analysisJobId + " | Service: " + service + " | Status: " + status);

        try {
            // Καλούμε τη μέθοδο του Service που αναλαμβάνει το Orchestration
            repoService.handleServiceCallback(analysisJobId, service, status, token);
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

    @GetMapping("/analysis/{jobId}/review")
    public ResponseEntity<FileDiffResponse> getAnalysisReview(@PathVariable String jobId, Authentication auth) {
        // 1. Παίρνουμε το ID του χρήστη που κάνει την κλήση από το JWT
        String currentUserId = auth.getName();

        // 2. Το στέλνουμε στο service για έλεγχο ιδιοκτησίας
        FileDiffResponse response = repoService.getAnalysisReviewDetails(jobId, currentUserId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/download-comparison/{jobId}")
    public ResponseEntity<byte[]> downloadComparison(@PathVariable String jobId, Authentication auth) {
        byte[] zip = repoService.createComparisonZip(jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"comparison-" + jobId + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }

    @PostMapping("/confirm-deployment/{jobId}")
    public ResponseEntity<?> confirmDeployment(
            @PathVariable String jobId,
            @RequestBody Map<String, String> payload,
            HttpServletRequest request) {

        // 1. Εξαγωγή του JWT από το τρέχον request[cite: 6]
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String jwt = authHeader.substring(7);

        // 2. Λήψη του repoUrl από το payload
        String repoUrl = payload.get("repoUrl");

        // 3. Εσωτερική κλήση μέσω του service[cite: 6]
        repoService.triggerExecutionInCluster(jobId, repoUrl, jwt);

        return ResponseEntity.ok(Map.of(
                "status", "IN_PROGRESS",
                "message", "Deployment triggered in-cluster. Check your GitHub Actions."
        ));
    }

    // Στο Repositories.java (Analyzer Controller)

    @PostMapping("/jobs/{jobId}/select-compute")
    public ResponseEntity<?> submitUserSelection(
            @PathVariable String jobId,
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String cleanToken = (token != null) ? token.replace("Bearer ", "").trim() : null;
        String selectedCompute = payload.get("selectedCompute");

        // 1. Βρίσκεις το Job
        AnalysisJob job = repoService.findAnalysisJob(jobId);

        if (!"PENDING_USER_SELECTION".equals(job.getStatus())) {
            return ResponseEntity.badRequest().body("Job is not waiting for selection.");
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            // 2. Μετατροπή σε POJO και "Καθαρισμός"
            InfrastructureAnalysis blueprint = mapper.treeToValue(job.getBlueprintJson(), InfrastructureAnalysis.class);

            // Ορίζουμε ρητά την κατηγορία
            blueprint.setComputeCategory(selectedCompute);

            // ΚΡΙΣΙΜΟ: Εδώ "κλειδώνεις" το targetCompute για να ξέρει ο Terraform Generator τι να φτιάξει
            // Αν θες π.χ. στο AWS το "Container" να μεταφράζεται πάντα σε "Fargate"

            blueprint.setTargetCompute(selectedCompute);


            // 3. Ενημέρωση και αποθήκευση στη Βάση
            job.setBlueprintJson(mapper.valueToTree(blueprint));
            job.setStatus("EXECUTING"); // Αλλάζουμε σε EXECUTING για να ξέρει το UI ότι ξεκίνησε η παραγωγή
            repoService.saveAnalysisJob(job);

            // 4. ASYNC TRIGGER (Για να μην φάει timeout το Frontend)
            // Επιστρέφουμε αμέσως 200 OK και οι generators τρέχουν στο παρασκήνιο
            CompletableFuture.runAsync(() -> {
                try {
                    System.out.println("🚀 [RESUME ASYNC] Triggering generators for Job: " + jobId);
                    repoService.triggerDownstreamServices(jobId, job.getUserId(), cleanToken);
                } catch (Exception e) {
                    System.err.println("❌ [ASYNC TRIGGER ERROR] " + e.getMessage());
                }
            });

            return ResponseEntity.ok(Map.of(
                    "message", "Selection saved",
                    "status", "EXECUTING"
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}