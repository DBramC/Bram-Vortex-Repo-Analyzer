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
    private final RestClient internalClient = RestClient.create();

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

    public String startAnalysis(String userId, String token, AnalysisRequest request) {
        System.out.println("\n🚀 [VORTEX-ANALYZER] --- STARTING NEW ANALYSIS ---");
        System.out.println("👤 User ID: " + userId);

        // 1. Vault Token Retrieval
        System.out.println("🔑 [STAGE 1] Accessing Vault for GitHub Token...");
        String accessToken = vaultService.getGithubToken(userId);
        System.out.println("✅ Token retrieved successfully.");

        // 2. Parameters
        String targetCloud = (request.getTargetCloud() != null && !request.getTargetCloud().isEmpty()) ? request.getTargetCloud() : "AWS";
        String computeType = (request.getComputeType() != null && !request.getComputeType().isEmpty()) ? request.getComputeType() : "Container";
        String targetRegion = (request.getTargetRegion() != null && !request.getTargetRegion().isEmpty()) ? request.getTargetRegion() : "eu-central-1";

        System.out.println("📋 [STAGE 2] Setting Infrastructure Parameters:");
        System.out.println("   -> Cloud: " + targetCloud + " | Compute: " + computeType + " | Region: " + targetRegion);

        // 3. Repo URL Sanitization
        String safeRepoUrl = request.getRepoUrl() != null ? request.getRepoUrl() : "";
        String ownerAndRepo = safeRepoUrl.replace("https://github.com/", "").replace(".git", "");
        if (ownerAndRepo.isEmpty() && request.getRepoName() != null) ownerAndRepo = request.getRepoName();
        System.out.println("📦 [STAGE 3] Target Repository: " + ownerAndRepo);

        // 4. GitHub Tree Fetching
        System.out.println("🔍 [STAGE 4] Fetching GitHub Repository Tree (Recursive)...");
        String realManifestContent = null;
        String foundManifestPath = null;
        Map<String, String> allConfigsFound = new HashMap<>();

        try {
            Map<String, Object> treeResponse = restClient.get()
                    .uri("/repos/" + ownerAndRepo + "/git/trees/main?recursive=1")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            List<Map<String, String>> tree = (List<Map<String, String>>) treeResponse.get("tree");
            System.out.println("📂 Total files scanned in tree: " + (tree != null ? tree.size() : 0));

            List<String> manifestPatterns = List.of("pom.xml", "package.json", "Dockerfile", "requirements.txt");
            List<String> configPatterns = List.of("application.properties", "application.yml", "application.yaml", ".env");

            for (String pattern : manifestPatterns) {
                foundManifestPath = tree.stream().map(i -> i.get("path")).filter(p -> p.endsWith(pattern)).findFirst().orElse(null);
                if (foundManifestPath != null) {
                    System.out.println("📄 Found primary manifest: " + foundManifestPath);
                    break;
                }
            }

            if (foundManifestPath != null) {
                realManifestContent = fetchFileContent(ownerAndRepo, foundManifestPath, accessToken);
                System.out.println("✅ Manifest content fetched.");
            }

            System.out.println("⚙️ Searching for configuration files...");
            for (String pattern : configPatterns) {
                List<String> matches = tree.stream().map(i -> i.get("path")).filter(p -> p.endsWith(pattern)).collect(Collectors.toList());
                for (String path : matches) {
                    String content = fetchFileContent(ownerAndRepo, path, accessToken);
                    if (content != null) {
                        allConfigsFound.put(path, content);
                        System.out.println("   [+] Config collected: " + path);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ [STAGE 4 ERROR] GitHub Search Failure: " + e.getMessage());
        }

        if (realManifestContent == null) throw new RuntimeException("No manifest file found.");

        // 5. AI Prompt Preparation
        System.out.println("🧠 [STAGE 5] Formatting AI Prompt & Output Schema...");
        var outputConverter = new BeanOutputConverter<>(InfrastructureAnalysis.class);
        StringBuilder configsBuilder = new StringBuilder();
        if (allConfigsFound.isEmpty()) {
            configsBuilder.append("No explicit configuration files found. Assume framework defaults.");
        } else {
            allConfigsFound.forEach((path, content) -> configsBuilder.append(String.format("\n--- FILE: %s ---\n%s\n", path, content)));
        }

        // --- PROMPT (UNTOUCHED) ---
        String promptMessage = String.format("""
            You are an expert Cloud Architect for the 'Bram Vortex' platform.
            Generate a detailed "Architectural Blueprint" JSON for the following repository.

            DO NOT GENERATE CODE. Generate only infrastructure specifications.

            CONTEXT:
            - Target Cloud: %1$s
            - Requested Compute Type: %2$s
            - Target Region: %3$s
            - Manifest: %4$s

            --- MANIFEST CONTENT ---
            %5$s

            --- ALL DETECTED CONFIGURATIONS ---
            %6$s

            REQUIRED TASKS:
            1. Identify tech stack (Language, Framework, App Type).
            2. Define 'computeCategory' as exactly '%2$s'.
            3. Define 'targetCompute' based on '%2$s' (e.g., 'AWS ECS Fargate', 'Azure AKS').
            4. CRITICAL: Fill 'computeSpecs' with technical hardware requirements based on the tech stack:
               - For Containers: include 'cpu_units', 'memory_mb', 'min_max_replicas'.
               - For Kubernetes: include 'node_type' (instance size), 'autoscaling_range', 'min_nodes'.
                - For VMs: include 'instance_family' (e.g., 't3.micro').
            5. Extract ALL keys/values from the CONFIGURATIONS section into 'configurationSettings'. 
                - If a key exists in both a .properties/.yml and a .env, prioritize the .env value.
            6. Detect 'targetContainerPort' (e.g., 8080, 3000). Check configs for 'server.port' or 'PORT'.
            7. Define necessary build steps and monitoring metrics.
            8. Generate 'deploymentMetadata' ONLY if compute type is 'Virtual Machine':
               - 'osDistro': Recommend the best Linux distribution (e.g., 'Ubuntu 22.04 LTS').
               - 'javaVersion': Recommended JDK version detected from the manifest (e.g., '17', '21').
               - 'executionUser': A secure system username for the application.
               - 'jvmArgs': Recommended memory/performance settings based on 'computeSpecs'.
               - 'healthCheckEndpoint': Identify the application health or actuator path.

            RULES:
            - Respond ONLY with raw JSON. No markdown backticks.
            - 'targetCloud' must be exactly "%1$s".

            SCHEMA:
            %7$s
            """,
                targetCloud, computeType, targetRegion, foundManifestPath,
                realManifestContent, configsBuilder.toString(), outputConverter.getFormat());

        // 6. Database Job Creation
        String jobId = UUID.randomUUID().toString();
        System.out.println("💾 [STAGE 6] Saving Analysis Job to Database. Job ID: " + jobId);
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

        // 7. Async AI Execution
        System.out.println("🤖 [STAGE 7] Dispatching request to Gemini AI (Asynchronous)...");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                System.out.println("📡 [ASYNC] AI call started for Job ID: " + jobId);
                String aiResponse = chatModel.call(promptMessage);
                System.out.println("📥 [ASYNC] AI Response received. Converting to Blueprint...");

                InfrastructureAnalysis result = outputConverter.convert(aiResponse);
                job.setBlueprintJson(objectMapper.valueToTree(result));
                job.setStatus("COMPLETED");
                jobRepository.save(job);
                System.out.println("🏁 [FINISH] Job " + jobId + " completed successfully! Blueprint is ready.");

                // Triggering downstream
                triggerDownstreamServices(jobId, userId, token);

            } catch (Exception e) {
                System.err.println("❌ [ASYNC ERROR] Processing failed for Job " + jobId + ": " + e.getMessage());
                job.setStatus("FAILED");
                jobRepository.save(job);
            }
        });

        System.out.println("📡 [RETURN] Job created. Returning Job ID: " + jobId + "\n");
        return jobId;
    }

    private void triggerDownstreamServices(String jobId, String userId, String token) {
        System.out.println("📢 [WEBHOOK] Triggering downstream generators for Job: " + jobId);

        // 1. Φέρνουμε το Job από τη βάση για να ελέγξουμε το computeType
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            System.err.println("⚠️ [WEBHOOK ERROR] Job not found in DB: " + jobId);
            return;
        }

        String computeType = job.getComputeType(); // Παίρνουμε το compute type από το entity

        // 2. Terraform Generator - Τρέχει ΠΑΝΤΑ
        // (Χρειάζεται είτε για το provisioning του VM είτε για το Managed Container infra)
        // 1. Terraform Generator - ΠΡΟΣΘΕΣΕ ΤΟ TOKEN
        try {
            String terraformUrl = "http://terraform-generator-svc:80/terraform/generate/" + jobId + "?userId=" + userId;
            internalClient.post()
                    .uri(terraformUrl)
                    .header("Authorization", token) // <--- ΤΩΡΑ ΤΟ ΣΤΕΛΝΕΙΣ ΚΑΙ ΕΔΩ
                    .retrieve()
                    .toBodilessEntity();
            System.out.println("✅ Terraform Generator triggered.");
        } catch (Exception e) {
            System.err.println("⚠️ [WEBHOOK ERROR] Terraform Trigger Failed: " + e.getMessage());
        }

        // 2. Ansible Generator - ΠΡΟΣΘΕΣΕ ΤΟ TOKEN
        if ("VM".equalsIgnoreCase(computeType)) {
            try {
                String ansibleUrl = "http://ansible-generator-svc:80/ansible/generate/" + jobId + "?userId=" + userId;
                internalClient.post()
                        .uri(ansibleUrl)
                        .header("Authorization", token) // <--- ΤΩΡΑ ΤΟ ΣΤΕΛΝΕΙΣ ΚΑΙ ΕΔΩ
                        .retrieve()
                        .toBodilessEntity();
                System.out.println("✅ Ansible Generator triggered.");
            } catch (Exception e) {
                // Εδώ έπαιρνες το 403 γιατί έλειπε το παραπάνω header!
                System.err.println("⚠️ [WEBHOOK ERROR] Ansible Trigger Failed: " + e.getMessage());
            }
        }
        // 4. Pipelines (Future)
    /*
    try {
        String pipelineUrl = "http://pipeline-generator-svc:80/pipeline/generate/" + jobId + "?userId=" + userId;
        internalClient.post().uri(pipelineUrl).retrieve().toBodilessEntity();
        System.out.println("✅ Pipeline Generator triggered.");
    } catch (Exception e) { System.err.println("⚠️ Pipeline Trigger Failed"); }
    */
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