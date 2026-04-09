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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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
                1. Identify exact tech stack (Language, Language Version, Framework, App Type).
                2. Define 'computeCategory' as exactly '%2$s'.
                3. Define 'targetCompute' based on '%2$s' (e.g., 'AWS ECS Fargate', 'Azure AKS').
                4. CRITICAL: Fill 'computeSpecs' with technical hardware requirements based on the tech stack:
                    - For Containers: include 'cpu_units', 'memory_mb', 'min_max_replicas'.
                    - For Kubernetes: include 'node_type' (instance size), 'autoscaling_range', 'min_nodes'.
                    - For VMs: include 'instance_family' (e.g., 't3.micro').
                5. Extract ALL keys/values from the CONFIGURATIONS section into 'configurationSettings'. 
                    - If a key exists in both a .properties/.yml and a .env, prioritize the .env value.
                6. Detect 'targetContainerPort' (e.g., 8080, 3000). Check configs for 'server.port' or 'PORT'.
                7. Generate 'deploymentMetadata' ONLY if compute type is 'Virtual Machine':
                    - 'osDistro': Recommend the best Linux distribution (e.g., 'Ubuntu 22.04 LTS').
                    - 'executionUser': A secure system username for the application.
                    - 'jvmArgs': Recommended memory/performance settings based on 'computeSpecs'.
                    - 'healthCheckEndpoint': Identify the application health or actuator path.
                8. CRITICAL - Generate 'ciCdMetadata' for pipeline construction (APPLIES TO ALL COMPUTE TYPES):
                    - 'buildTool': The primary build tool (e.g., 'Maven', 'Gradle', 'NPM', 'Poetry').
                    - 'languageVersion': The exact version needed for the build environment (e.g., '17', '21', '18.x').
                    - 'buildCommands': Array of exact shell commands to compile/build the app (e.g., ['mvn clean package -DskipTests']).
                    - 'testCommands': Array of test commands (e.g., ['mvn test']).
                    - 'artifactPath': The relative path to the compiled binary/artifact (e.g., 'target/*.jar', 'dist/').
                    - 'hasDockerfile': Boolean indicating if a Dockerfile was explicitly detected in the manifest.
                9. CRITICAL - ARCHITECTURE DECISION: Analyze the complexity of the repository (e.g., monolithic vs microservices, presence of Dockerfile, stateful vs stateless) and populate:
                    - 'validComputeTypes': An array containing strictly the appropriate targets from this list: ["Virtual Machine", "Container", "Kubernetes"]. EXCLUDE overkill or inadequate architectures. (e.g., Exclude "Kubernetes" for a simple static site or basic CRUD API. Exclude "Virtual Machine" if it's a massive microservices mesh).
                    - 'computeReasoning': A short, professional explanation (1-2 sentences) of WHY specific compute types were chosen and why others were deemed overkill or inadequate.

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
        if ("VM".equalsIgnoreCase(computeType) || "Virtual Machine".equalsIgnoreCase(computeType)) {

            try {
                String ansibleUrl = "http://ansible-generator-svc:80/ansible/generate/" + jobId + "?userId=" + userId;
                internalClient.post().uri(ansibleUrl).header("Authorization", token).retrieve().toBodilessEntity();
                System.out.println("✅ Ansible Generator triggered.");
            } catch (Exception e) { System.err.println("⚠️ Ansible Trigger Failed"); }
        } else {

            System.out.println("⏭️ [ORCHESTRATOR] Target is " + computeType + ". Skipping Ansible Generator.");
            job.setAnsibleStatus("SKIPPED");
            jobRepository.save(job);
        }
        // 4. Pipelines (Future)

        try {
            String pipelineUrl = "http://pipeline-generator-svc:80/pipeline/generate/" + jobId;

            internalClient.post()
                    .uri(pipelineUrl)
                    .header("Authorization", token) // JWT Token
                    .retrieve()
                    .toBodilessEntity();
            System.out.println("✅ Pipeline Generator triggered.");
        } catch (Exception e) {
            System.err.println("⚠️ [WEBHOOK ERROR] Pipeline Trigger Failed: " + e.getMessage());
        }

    }

    public void handleServiceCallback(String analysisJobId, String serviceName, String status) {
        AnalysisJob job = jobRepository.findById(analysisJobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        System.out.println("📥 [CALLBACK] Service: " + serviceName + " | Status: " + status + " for Job: " + analysisJobId);

        // 1. Ενημέρωση του συγκεκριμένου status στη βάση
        switch (serviceName.toUpperCase()) {
            case "TERRAFORM" -> job.setTerraformStatus(status);
            case "ANSIBLE" -> job.setAnsibleStatus(status);
            case "PIPELINE" -> job.setPipelineStatus(status);
        }

        // Αποθηκεύουμε την ενδιάμεση πρόοδο
        jobRepository.save(job);

        // 2. Αν κάποιο service αποτύχει, ακυρώνουμε όλο το workflow
        if ("FAILED".equals(status)) {
            System.err.println("❌ [ORCHESTRATOR] " + serviceName + " failed! Halting workflow.");
            job.setStatus("FAILED");
            jobRepository.save(job);
            return;
        }

        // 3. Έλεγχος ολοκλήρωσης όλων των επιμέρους εργασιών (Barrier)
        boolean isTerraformDone = "COMPLETED".equals(job.getTerraformStatus());
        boolean isPipelineDone = "COMPLETED".equals(job.getPipelineStatus());
        // Το Ansible είναι "έτοιμο" αν είναι COMPLETED ή αν παρακάμφθηκε (SKIPPED)
        boolean isAnsibleDone = "COMPLETED".equals(job.getAnsibleStatus()) || "SKIPPED".equals(job.getAnsibleStatus());

        // 4. ΜΟΝΟ αν τελείωσαν όλα, προχωράμε στη συγχώνευση
        if (isTerraformDone && isPipelineDone && isAnsibleDone) {
            System.out.println("🎉 [ORCHESTRATOR] ALL GENERATORS COMPLETED! Starting Master ZIP aggregation...");

            try {
                // Ανάκτηση των bytes χρησιμοποιώντας τις Native Query μεθόδους του Repository
                byte[] tfZipBytes = jobRepository.findTerraformZip(analysisJobId);
                byte[] pipeZipBytes = jobRepository.findPipelineZip(analysisJobId);

                // Αν το Ansible έγινε SKIPPED, δεν ψάχνουμε για ZIP (θα επέστρεφε null ούτως ή άλλως)
                byte[] ansZipBytes = "SKIPPED".equals(job.getAnsibleStatus()) ? null : jobRepository.findAnsibleZip(analysisJobId);

                // Δημιουργία του ενιαίου Master ZIP
                byte[] masterZip = createMasterZip(tfZipBytes, ansZipBytes, pipeZipBytes);

                if (masterZip != null) {
                    // Αποθήκευση του τελικού αρχείου και αλλαγή status για το επόμενο στάδιο
                    job.setMasterZip(masterZip);
                    job.setStatus("READY_FOR_CHECK");
                    jobRepository.save(job);

                    System.out.println("📦 [ORCHESTRATOR] Master ZIP created successfully! Size: " + masterZip.length + " bytes.");

                    // Εδώ θα μπει η κλήση για το επόμενο service (Architecture Checker)
                    // triggerArchitectureChecker(job);
                } else {
                    throw new RuntimeException("Master ZIP creation returned null");
                }

            } catch (Exception e) {
                System.err.println("❌ [ORCHESTRATOR ERROR] Aggregation failed: " + e.getMessage());
                job.setStatus("FAILED");
                jobRepository.save(job);
            }
        } else {
            // Ενημερωτικό μήνυμα για το ποιο service περιμένουμε ακόμα
            System.out.println("⏳ [ORCHESTRATOR] Waiting for remaining services... " +
                    "(TF: " + job.getTerraformStatus() +
                    ", Pipe: " + job.getPipelineStatus() +
                    ", Ansible: " + job.getAnsibleStatus() + ")");
        }
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

    /**
     * Ενώνει 3 ξεχωριστά byte arrays (ZIPs) σε ένα ενιαίο Master ZIP.
     */
    private byte[] createMasterZip(byte[] terraformZip, byte[] ansibleZip, byte[] pipelineZip) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            // 1. Το Terraform μπαίνει στον φάκελο "infrastructure/"
            if (terraformZip != null && terraformZip.length > 0) {
                appendZipToMaster(zos, terraformZip, "infrastructure/");
            }

            // 2. Το Ansible μπαίνει στον φάκελο "configuration/"
            if (ansibleZip != null && ansibleZip.length > 0) {
                appendZipToMaster(zos, ansibleZip, "configuration/");
            }

            // 3. Το Pipeline ΔΕΝ παίρνει φάκελο, γιατί έχει ήδη το ".github/workflows/"
            if (pipelineZip != null && pipelineZip.length > 0) {
                appendZipToMaster(zos, pipelineZip, "");
            }

            zos.finish();
            zos.flush();
        } catch (Exception e) {
            System.err.println("❌ [ZIP MERGER ERROR] Failed to merge zips: " + e.getMessage());
            return null; // Σε περίπτωση λάθους
        }
        return baos.toByteArray();
    }

    /**
     * Διαβάζει ένα μικρό ZIP και μεταφέρει τα αρχεία του στο μεγάλο ZIP.
     */
    private void appendZipToMaster(ZipOutputStream zos, byte[] zipData, String folderPrefix) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Φτιάχνουμε το νέο path προσθέτοντας τον φάκελο μπροστά
                String newEntryName = folderPrefix + entry.getName();
                zos.putNextEntry(new ZipEntry(newEntryName));

                // Αντιγράφουμε τα δεδομένα του αρχείου (Απαιτεί Java 9+)
                zis.transferTo(zos);
                zos.closeEntry();
            }
        }
    }

    public AnalysisJob getAnalysisJob(String jobId) {
        return jobRepository.findById(jobId).orElse(null);
    }
}