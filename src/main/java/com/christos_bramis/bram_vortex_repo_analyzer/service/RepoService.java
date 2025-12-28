package com.christos_bramis.bram_vortex_repo_analyzer.service;

import com.christos_bramis.bram_vortex_repo_analyzer.dto.AnalysisRequest;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.RepoResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Service
public class RepoService {

    private final RestClient restClient;
    // Υποθέτουμε ότι έχεις φτιάξει ένα απλό service/interface που μιλάει με το Vault
    private final VaultService vaultService;

    // Constructor Injection
    public RepoService(RestClient.Builder builder, VaultService vaultService) {
        // Χτίζουμε τον client με το base URL του GitHub
        this.restClient = builder.baseUrl("https://api.github.com").build();
        this.vaultService = vaultService;
    }

    /**
     * Βήμα 1: Φέρνει όλα τα repositories του χρήστη.
     */
    public List<RepoResponse> getUserRepositories(String userId) {

        // 1. Παίρνουμε το "κλειδί" (Token) από το Vault χρησιμοποιώντας το ID του χρήστη
        String accessToken = vaultService.getGithubToken(userId);

        if (accessToken == null || accessToken.isEmpty()) {
            throw new RuntimeException("No GitHub token found for user: " + userId);
        }

        // 2. Καλούμε το GitHub API
        return restClient.get()
                .uri("/user/repos?sort=updated&per_page=100&type=all") // Ταξινόμηση και όριο
                .header("Authorization", "Bearer " + accessToken)       // Δυναμικό Token
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                // 3. Μετατρέπουμε το JSON κατευθείαν σε List<RepoResponse>
                .body(new ParameterizedTypeReference<List<RepoResponse>>() {});
    }

    /**
     * Βήμα 2: Ξεκινάει τη διαδικασία ανάλυσης για ένα συγκεκριμένο repo.
     */
    public String startAnalysis(String userId, AnalysisRequest request) {

        // Επαλήθευση (προαιρετικά): Ξαναπαίρνουμε το token για να τσεκάρουμε αν έχουμε πρόσβαση στο repo
        String accessToken = vaultService.getGithubToken(userId);

        System.out.println("Starting analysis for Repo ID: " + request.getRepoId());
        System.out.println("Cloning URL: " + request.getRepoUrl());

        // ΕΔΩ ΜΠΑΙΝΕΙ Η ΛΟΓΙΚΗ ΤΟΥ ANALYZER ΣΟΥ:
        // π.χ.
        // 1. Git Clone (χρησιμοποιώντας το accessToken)
        // 2. Run Static Analysis (Sonar, custom scripts)
        // 3. Save results to DB

        // Για τώρα, επιστρέφουμε ένα τυχαίο Job ID
        return UUID.randomUUID().toString();
    }
}