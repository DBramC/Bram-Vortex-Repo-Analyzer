package com.christos_bramis.bram_vortex_repo_analyzer.service;

import com.christos_bramis.bram_vortex_repo_analyzer.dto.AnalysisRequest;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.InfrastructureAnalysis;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.RepoResponse;
import com.christos_bramis.bram_vortex_repo_analyzer.entity.AnalysisJob;
import com.christos_bramis.bram_vortex_repo_analyzer.repository.AnalysisJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RepoService {

    private final RestClient restClient;
    private final VaultService vaultService;
    private final ChatModel chatModel;
    private final AnalysisJobRepository jobRepository;
    private final ObjectMapper objectMapper;

    // Constructor Injection
    public RepoService(RestClient.Builder builder, VaultService vaultService, ChatModel chatModel,
                       AnalysisJobRepository jobRepository, ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl("https://api.github.com").build();
        this.vaultService = vaultService;
        this.chatModel = chatModel;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Βήμα 1: Φέρνει όλα τα repositories του χρήστη.
     */
    public List<RepoResponse> getUserRepositories(String userId) {
        String accessToken = vaultService.getGithubToken(userId);

        if (accessToken == null || accessToken.isEmpty()) {
            throw new RuntimeException("No GitHub token found for user: " + userId);
        }

        return restClient.get()
                .uri("/user/repos?sort=updated&per_page=100&type=all")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(new ParameterizedTypeReference<List<RepoResponse>>() {});
    }

    /**
     * Βήμα 2: Ξεκινάει τη διαδικασία ανάλυσης για ένα συγκεκριμένο repo.
     */
    public String startAnalysis(String userId, AnalysisRequest request) {

        String accessToken = vaultService.getGithubToken(userId);

        System.out.println("Starting analysis for Repo ID: " + request.getRepoId());
        System.out.println("Target URL: " + request.getRepoUrl());

        // 1. Παίρνουμε τις παραμέτρους (με default τιμές αν λείπουν)
        String targetCloud = request.getTargetCloud() != null && !request.getTargetCloud().isEmpty()
                ? request.getTargetCloud() : "AWS";

        String computeType = request.getComputeType() != null && !request.getComputeType().isEmpty()
                ? request.getComputeType() : "managed container";

        String targetRegion = request.getTargetRegion() != null && !request.getTargetRegion().isEmpty()
                ? request.getTargetRegion() : "eu-central-1";

        // 2. Εξάγουμε το "owner/repo" από το URL - ΑΣΦΑΛΗΣ ΕΛΕΓΧΟΣ ΓΙΑ NULL POINTER EXCEPTION
        String safeRepoUrl = request.getRepoUrl() != null ? request.getRepoUrl() : "";
        String ownerAndRepo = safeRepoUrl
                .replace("https://github.com/", "")
                .replace(".git", "");

        // Fallback αν το URL είναι κενό
        if (ownerAndRepo.isEmpty() && request.getRepoName() != null) {
            ownerAndRepo = request.getRepoName();
        }

        // 3. ΔΥΝΑΜΙΚΗ ΚΑΙ ΑΝΑΔΡΟΜΙΚΗ ΑΝΑΖΗΤΗΣΗ MANIFEST ΚΑΙ CONFIG FILE
        String realManifestContent = null;
        String realConfigContent = null;
        String foundManifestPath = null;
        String foundConfigPath = null;

        System.out.println("🔍 Searching recursively for manifest and config files in: " + ownerAndRepo);

        try {
            Map<String, Object> treeResponse = restClient.get()
                    .uri("/repos/" + ownerAndRepo + "/git/trees/main?recursive=1")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            List<Map<String, String>> tree = (List<Map<String, String>>) treeResponse.get("tree");

            List<String> priorityManifests = List.of("pom.xml", "package.json", "Dockerfile", "requirements.txt");
            List<String> priorityConfigs = List.of("application.properties", "application.yml", "application.yaml", ".env");

            for (String target : priorityManifests) {
                foundManifestPath = tree.stream()
                        .map(item -> item.get("path"))
                        .filter(path -> path.endsWith(target))
                        .findFirst()
                        .orElse(null);
                if (foundManifestPath != null) break;
            }

            for (String target : priorityConfigs) {
                foundConfigPath = tree.stream()
                        .map(item -> item.get("path"))
                        .filter(path -> path.endsWith(target))
                        .findFirst()
                        .orElse(null);
                if (foundConfigPath != null) break;
            }

            if (foundManifestPath != null) {
                realManifestContent = restClient.get()
                        .uri("/repos/" + ownerAndRepo + "/contents/" + foundManifestPath)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/vnd.github.v3.raw")
                        .retrieve()
                        .body(String.class);

                System.out.println("✅ Found manifest at path: " + foundManifestPath);
            }

            if (foundConfigPath != null) {
                realConfigContent = restClient.get()
                        .uri("/repos/" + ownerAndRepo + "/contents/" + foundConfigPath)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/vnd.github.v3.raw")
                        .retrieve()
                        .body(String.class);

                System.out.println("✅ Found config at path: " + foundConfigPath);
            }

        } catch (Exception e) {
            System.err.println("⚠️ Error during recursive search: " + e.getMessage());
        }

        if (realManifestContent == null) {
            throw new RuntimeException("No supported manifest file found in the repository (checked all subdirectories).");
        }

        // 4. Ετοιμάζουμε τον Converter του Spring AI
        var outputConverter = new BeanOutputConverter<>(InfrastructureAnalysis.class);
        String formatInstructions = outputConverter.getFormat();

        // 5. Φτιάχνουμε το Δυναμικό System Prompt χρησιμοποιώντας το ΠΡΑΓΜΑΤΙΚΟ ΠΕΡΙΕΧΟΜΕΝΟ και ARGUMENT INDICES
        String configSection = (realConfigContent != null && !realConfigContent.isEmpty())
                ? realConfigContent
                : "No explicit configuration file found. Assume framework defaults.";

        String promptMessage = String.format("""
        You are an expert Cloud Software Architect and AI Agent representing the 'Bram Vortex' platform.
        Your task is to analyze the following repository files and generate an "Architectural Blueprint" (Single Source of Truth).
        
        This blueprint will be routed to specialized microservices to automatically provision the infrastructure. 
        DO NOT GENERATE CODE. Generate only the specifications.
        
        TARGET CLOUD PROVIDER: %1$s
        TARGET COMPUTE TYPE: %2$s
        TARGET REGION: %3$s
        MANIFEST FILE PATH: %4$s
        CONFIG FILE PATH: %5$s
        
        --- MANIFEST FILE CONTENT ---
        %6$s
        
        --- CONFIGURATION FILE CONTENT ---
        %7$s
        
        TASKS:
        1. Analyze the tech stack to identify the primary language, framework, and application type.
        2. Scan dependencies to detect required external infrastructure (e.g., PostgreSQL, Redis).
        3. The user has explicitly requested to deploy on a "%2$s" located in the "%3$s" region. Generate the infrastructure specifications tailored EXACTLY for a %2$s environment in %1$s (%3$s). Do not recommend alternatives. Ensure any region-specific configurations match "%3$s".
        4. Identify the expected exposed port. CRITICAL: Check the CONFIGURATION FILE CONTENT above for custom ports (e.g., server.port=9090). If none is specified, output the framework's default port (e.g., 8080 for Spring).
        5. Outline the required CI/CD build steps and necessary monitoring metrics.
        
        CRITICAL INSTRUCTIONS FOR JSON OUTPUT:
        - You must respond EXCLUSIVELY with a valid, raw JSON object representing the blueprint.
        - DO NOT include ANY markdown formatting around the JSON object.
        - Provide ONLY raw JSON that strictly matches the schema provided below.
        
        SCHEMA INSTRUCTIONS:
        %8$s
        """,
                targetCloud,                                          // %1$s
                computeType,                                          // %2$s
                targetRegion,                                         // %3$s
                foundManifestPath,                                    // %4$s
                (foundConfigPath != null ? foundConfigPath : "None"), // %5$s
                realManifestContent,                                  // %6$s
                configSection,                                        // %7$s
                formatInstructions                                    // %8$s
        );

        // 6. Δημιουργούμε το μοναδικό Job ID
        String jobId = UUID.randomUUID().toString();

        // 7. ΑΡΧΙΚΗ ΑΠΟΘΗΚΕΥΣΗ (ANALYZING)
        AnalysisJob job = new AnalysisJob();
        job.setJobId(jobId);
        job.setUserId(userId);
        job.setRepoId(request.getRepoId());

        String repoName = request.getRepoName() != null ? request.getRepoName() : ownerAndRepo;
        job.setRepoName(repoName);

        job.setComputeType(computeType);
        job.setTargetCloud(targetCloud);
        job.setTargetRegion(targetRegion); // <-- Αποθηκεύουμε το Region στη βάση!
        job.setStatus("ANALYZING");
        job.setPromptMessage(promptMessage);

        jobRepository.save(job);

        // 8. Κάνουμε την κλήση στον Gemini Agent ΑΣΥΓΧΡΟΝΑ (Στο παρασκήνιο)
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                System.out.println("🤖 Asking Gemini to analyze and generate Blueprint for cloud: " + targetCloud + " in " + targetRegion + "...");
                String aiResponse = chatModel.call(promptMessage);

                // Μετατροπή της απάντησης σε Java Object (DTO)
                InfrastructureAnalysis analysisResult = outputConverter.convert(aiResponse);

                System.out.println("✅ Gemini Analysis Complete!");

                // 9. ΤΕΛΙΚΗ ΑΠΟΘΗΚΕΥΣΗ (COMPLETED)
                String jsonBlueprint = objectMapper.writeValueAsString(analysisResult);
                job.setBlueprintJson(jsonBlueprint);
                job.setStatus("COMPLETED");
                jobRepository.save(job);

                System.out.println("💾 Blueprint successfully saved! Job ID: " + jobId);

            } catch (Exception e) {
                System.err.println("❌ Error during AI Analysis: " + e.getMessage());
                job.setStatus("FAILED");
                jobRepository.save(job);
            }
        });

        // 10. Επιστρέφουμε το Job ID στο Frontend ΑΜΕΣΩΣ (πριν τελειώσει το AI)
        return jobId;
    }

    /**
     * Επιστρέφει το Blueprint (JSON String) από τη βάση δεδομένων με βάση το Job ID.
     */
    public AnalysisJob getAnalysisJob(String jobId) {
        return jobRepository.findById(jobId).orElse(null);
    }
}