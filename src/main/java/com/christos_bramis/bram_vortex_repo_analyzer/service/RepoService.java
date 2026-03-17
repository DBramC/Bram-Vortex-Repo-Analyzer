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

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RepoService {

    private final RestClient restClient;
    private final VaultService vaultService;
    private final ChatModel chatModel;
    private final AnalysisJobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public RepoService(RestClient.Builder builder, VaultService vaultService, ChatModel chatModel,
                       AnalysisJobRepository jobRepository, ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl("https://api.github.com").build();
        this.vaultService = vaultService;
        this.chatModel = chatModel;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

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

    public String startAnalysis(String userId, AnalysisRequest request) {
        String accessToken = vaultService.getGithubToken(userId);

        // 1. Παράμετροι από Request
        String targetCloud = (request.getTargetCloud() != null && !request.getTargetCloud().isEmpty()) ? request.getTargetCloud() : "AWS";
        String computeType = (request.getComputeType() != null && !request.getComputeType().isEmpty()) ? request.getComputeType() : "managed container";
        String targetRegion = (request.getTargetRegion() != null && !request.getTargetRegion().isEmpty()) ? request.getTargetRegion() : "eu-central-1";

        // 2. Ασφαλής επεξεργασία URL
        String safeRepoUrl = request.getRepoUrl() != null ? request.getRepoUrl() : "";
        String ownerAndRepo = safeRepoUrl.replace("https://github.com/", "").replace(".git", "");
        if (ownerAndRepo.isEmpty() && request.getRepoName() != null) ownerAndRepo = request.getRepoName();

        // 3. Συλλογή αρχείων
        String realManifestContent = null;
        String foundManifestPath = null;

        // Χρησιμοποιούμε Map για να αποθηκεύσουμε πολλά configs: Path -> Content
        Map<String, String> allConfigsFound = new HashMap<>();

        try {
            Map<String, Object> treeResponse = restClient.get()
                    .uri("/repos/" + ownerAndRepo + "/git/trees/main?recursive=1")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            List<Map<String, String>> tree = (List<Map<String, String>>) treeResponse.get("tree");

            // Λίστες αναζήτησης
            List<String> manifestPatterns = List.of("pom.xml", "package.json", "Dockerfile", "requirements.txt");
            List<String> configPatterns = List.of("application.properties", "application.yml", "application.yaml", ".env");

            // 3α. Εύρεση Manifest (παίρνουμε το πρώτο που ταιριάζει βάσει προτεραιότητας)
            for (String pattern : manifestPatterns) {
                foundManifestPath = tree.stream().map(i -> i.get("path")).filter(p -> p.endsWith(pattern)).findFirst().orElse(null);
                if (foundManifestPath != null) break;
            }

            if (foundManifestPath != null) {
                realManifestContent = fetchFileContent(ownerAndRepo, foundManifestPath, accessToken);
            }

            // 3β. Εύρεση ΠΟΛΛΑΠΛΩΝ Configs (δεν κάνουμε break, τα παίρνουμε όλα)
            for (String pattern : configPatterns) {
                List<String> matches = tree.stream()
                        .map(i -> i.get("path"))
                        .filter(p -> p.endsWith(pattern))
                        .collect(Collectors.toList());

                for (String path : matches) {
                    String content = fetchFileContent(ownerAndRepo, path, accessToken);
                    if (content != null) allConfigsFound.put(path, content);
                }
            }

        } catch (Exception e) {
            System.err.println("⚠️ Search Error: " + e.getMessage());
        }

        if (realManifestContent == null) throw new RuntimeException("No manifest file found.");

        // 4. Προετοιμασία Prompt
        var outputConverter = new BeanOutputConverter<>(InfrastructureAnalysis.class);

        // Δημιουργούμε ένα ενιαίο String με όλα τα configs και headers για το AI
        StringBuilder configsBuilder = new StringBuilder();
        if (allConfigsFound.isEmpty()) {
            configsBuilder.append("No explicit configuration files found. Assume framework defaults.");
        } else {
            allConfigsFound.forEach((path, content) -> {
                configsBuilder.append(String.format("\n--- CONFIG FILE: %s ---\n%s\n", path, content));
            });
        }

        String promptMessage = String.format("""
        You are an expert Cloud Software Architect representing 'Bram Vortex'.
        Analyze the repository files and generate a detailed "Architectural Blueprint".
        
        DO NOT GENERATE CODE. Generate only the specifications.
        
        TARGET CLOUD PROVIDER: %1$s
        TARGET COMPUTE TYPE: %2$s
        TARGET REGION: %3$s
        MANIFEST FILE PATH: %4$s
        
        --- PRIMARY MANIFEST CONTENT ---
        %5$s
        
        --- ALL DETECTED CONFIGURATIONS ---
        %6$s
        
        TASKS:
        1. Identify application type, primary language, and framework.
        2. Detect infrastructure dependencies (Databases, Caches, MQ).
        3. Define 'targetCompute' based on "%2$s" for region "%3$s" (e.g. AWS EKS, GCP Cloud Run).
        4. CRITICAL: Extract ALL configuration keys and values from ALL provided files in the "ALL DETECTED CONFIGURATIONS" section. 
           - Map them into the 'configurationSettings' field.
           - If a key exists in both a .properties and a .env file, prioritize the .env value.
        5. Identify the container port ('targetContainerPort'). Look into configs for 'server.port' or similar.
        6. Define build steps and monitoring metrics.
        
        OUTPUT RULES:
        - Respond EXCLUSIVELY with raw JSON. No markdown.
        - 'targetCloud' must be exactly "%1$s".
        
        SCHEMA INSTRUCTIONS:
        %7$s
        """,
                targetCloud,                    //%1$s
                computeType,                    //%2$s
                targetRegion,
                foundManifestPath,
                realManifestContent,
                configsBuilder,
                outputConverter.getFormat());

        // 5. Job Creation & Async Execution
        String jobId = UUID.randomUUID().toString();
        AnalysisJob job = new AnalysisJob();
        job.setJobId(jobId);
        job.setUserId(userId);
        job.setRepoId(request.getRepoId());
        job.setRepoName(request.getRepoName() != null ? request.getRepoName() : ownerAndRepo);
        job.setComputeType(computeType);
        job.setTargetCloud(targetCloud);
        job.setTargetRegion(targetRegion);
        job.setStatus("ANALYZING");
        job.setPromptMessage(promptMessage);
        jobRepository.save(job);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String aiResponse = chatModel.call(promptMessage);
                InfrastructureAnalysis result = outputConverter.convert(aiResponse);
                job.setBlueprintJson(objectMapper.writeValueAsString(result));
                job.setStatus("COMPLETED");
                jobRepository.save(job);
            } catch (Exception e) {
                job.setStatus("FAILED");
                jobRepository.save(job);
            }
        });

        return jobId;
    }

    private String fetchFileContent(String repo, String path, String token) {
        try {
            return restClient.get()
                    .uri("/repos/" + repo + "/contents/" + path)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3.raw")
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) { return null; }
    }

    public AnalysisJob getAnalysisJob(String jobId) {
        return jobRepository.findById(jobId).orElse(null);
    }
}