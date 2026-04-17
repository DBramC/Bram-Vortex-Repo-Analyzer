package com.christos_bramis.bram_vortex_repo_analyzer.service;

import com.christos_bramis.bram_vortex_repo_analyzer.dto.AnalysisRequest;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.FileDiffResponse;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.InfrastructureAnalysis;
import com.christos_bramis.bram_vortex_repo_analyzer.dto.RepoResponse;
import com.christos_bramis.bram_vortex_repo_analyzer.entity.AnalysisJob;
import com.christos_bramis.bram_vortex_repo_analyzer.entity.ValidatorJob;
import com.christos_bramis.bram_vortex_repo_analyzer.repository.AnalysisJobRepository;
import com.christos_bramis.bram_vortex_repo_analyzer.repository.ValidatorJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class RepoService {

    private final RestClient restClient;
    private final VaultService vaultService;
    private final ChatModel chatModel;
    private final AnalysisJobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final RestClient internalClient = RestClient.create();
    private final ValidatorJobRepository validatorJobRepository;

    public RepoService(RestClient.Builder builder, VaultService vaultService, ChatModel chatModel,
                       AnalysisJobRepository jobRepository, ObjectMapper objectMapper,
                       ValidatorJobRepository validatorJobRepository) {
        this.restClient = builder.baseUrl("https://api.github.com").build();
        this.vaultService = vaultService;
        this.chatModel = chatModel;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.validatorJobRepository = validatorJobRepository;
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

        RULES (CRITICAL - YOU WILL FAIL IF YOU DO NOT FOLLOW THESE):
            1. ABSOLUTELY NO CONVERSATIONAL TEXT.
            2. ABSOLUTELY NO THINKING STEPS OR BULLET POINTS.
            3. DO NOT output markdown blocks (e.g. ```json).
            4. YOUR RESPONSE MUST START WITH THE '{' CHARACTER AND END WITH THE '}' CHARACTER.
            5. RESPOND ONLY WITH THE RAW JSON OBJECT. ANY OTHER TEXT WILL CAUSE A FATAL SYSTEM CRASH.
            6. 'targetCloud' must be exactly "%1$s".

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

                // Προαιρετικός καθαρισμός JSON
                int startIndex = aiResponse.indexOf('{');
                int endIndex = aiResponse.lastIndexOf('}');
                if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
                    aiResponse = aiResponse.substring(startIndex, endIndex + 1);
                }

                System.out.println("📥 [ASYNC] AI Response received. Converting to Blueprint...");

                InfrastructureAnalysis result = outputConverter.convert(aiResponse);
                job.setBlueprintJson(objectMapper.valueToTree(result));
                job.setStatus("ANALYZING");
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

        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            System.err.println("⚠️ [WEBHOOK ERROR] Job not found in DB: " + jobId);
            return;
        }

        String computeType = job.getComputeType();

        try {
            String terraformUrl = "http://terraform-generator-svc:80/terraform/generate/" + jobId + "?userId=" + userId;
            internalClient.post()
                    .uri(terraformUrl)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            System.out.println("✅ Terraform Generator triggered.");
        } catch (Exception e) {
            System.err.println("⚠️ [WEBHOOK ERROR] Terraform Trigger Failed: " + e.getMessage());
        }

        if ("VM".equalsIgnoreCase(computeType) || "Virtual Machine".equalsIgnoreCase(computeType)) {
            try {
                String ansibleUrl = "http://ansible-generator-svc:80/ansible/generate/" + jobId + "?userId=" + userId;
                internalClient.post().uri(ansibleUrl).header("Authorization", "Bearer " + token).retrieve().toBodilessEntity();
                System.out.println("✅ Ansible Generator triggered.");
            } catch (Exception e) { System.err.println("⚠️ Ansible Trigger Failed"); }
        } else {
            System.out.println("⏭️ [ORCHESTRATOR] Target is " + computeType + ". Skipping Ansible Generator.");
            job.setAnsibleStatus("SKIPPED");
            jobRepository.save(job);
        }

        try {
            String pipelineUrl = "http://pipeline-generator-svc:80/pipeline/generate/" + jobId;
            internalClient.post()
                    .uri(pipelineUrl)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            System.out.println("✅ Pipeline Generator triggered.");
        } catch (Exception e) {
            System.err.println("⚠️ [WEBHOOK ERROR] Pipeline Trigger Failed: " + e.getMessage());
        }
    }

    public void handleServiceCallback(String analysisJobId, String serviceName, String status, String token) {
        System.out.println("📥 [CALLBACK] Service: " + serviceName + " | Status: " + status + " for Job: " + analysisJobId);

        // 1. Ατομική ενημέρωση στη βάση (Atomic Update)
        switch (serviceName.toUpperCase()) {
            case "TERRAFORM" -> jobRepository.updateTerraformStatus(analysisJobId, status);
            case "ANSIBLE" -> jobRepository.updateAnsibleStatus(analysisJobId, status);
            case "PIPELINE" -> jobRepository.updatePipelineStatus(analysisJobId, status);
            case "VALIDATOR" -> jobRepository.updateValidatorStatus(analysisJobId, status);
        }

        // 2. Φέρνουμε το "φρέσκο" αντικείμενο από τη βάση ΜΕΤΑ το update
        AnalysisJob job = jobRepository.findById(analysisJobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        // 3. Αν κάποιο service αποτύχει, σταματάμε
        if ("FAILED".equals(status)) {
            job.setStatus("FAILED");
            jobRepository.save(job);
            return;
        }

        // --- ΕΛΕΓΧΟΣ: Τελείωσαν οι Generators; ---
        boolean isTerraformDone = "COMPLETED".equals(job.getTerraformStatus());
        boolean isPipelineDone = "COMPLETED".equals(job.getPipelineStatus());
        // Το Ansible θεωρείται done αν είναι COMPLETED ή αν έγινε SKIPPED (για Containers)
        boolean isAnsibleDone = "COMPLETED".equals(job.getAnsibleStatus()) || "SKIPPED".equals(job.getAnsibleStatus());

        System.out.println("⏳ [ORCHESTRATOR] Status Check for " + analysisJobId + ": TF:" + job.getTerraformStatus() +
                " | Pipe:" + job.getPipelineStatus() + " | Ans:" + job.getAnsibleStatus());

        // Αν όλα είναι έτοιμα και ο Validator δεν έχει ξεκινήσει
        if (isTerraformDone && isPipelineDone && isAnsibleDone &&
                (job.getValidatorStatus() == null || "PENDING".equals(job.getValidatorStatus()))) {

            System.out.println("🎉 [ORCHESTRATOR] All Generators finished! Aggregating...");

            try {
                // Εδώ καλείς τη createMasterZip όπως πριν...
                byte[] tfZip = jobRepository.findTerraformZip(analysisJobId);
                byte[] pipeZip = jobRepository.findPipelineZip(analysisJobId);
                byte[] ansZip = "SKIPPED".equals(job.getAnsibleStatus()) ? null : jobRepository.findAnsibleZip(analysisJobId);

                byte[] rawZip = createMasterZip(tfZip, ansZip, pipeZip);
                if (rawZip != null) {
                    job.setMasterZip(rawZip);
                }

                job.setValidatorStatus("TRIGGERED");
                jobRepository.save(job);
                triggerArchitectureValidator(job, token);

            } catch (Exception e) {
                System.err.println("❌ Error in aggregation: " + e.getMessage());
                job.setStatus("FAILED");
                jobRepository.save(job);
            }
        }

        // --- ΕΛΕΓΧΟΣ: Τελείωσε ο Validator; ---
        if ("COMPLETED".equals(job.getValidatorStatus())) {
            System.out.println("🏆 [ORCHESTRATOR] VALIDATOR COMPLETED!");
            job.setStatus("READY_FOR_EXECUTION");
            jobRepository.save(job);
        }
    }

    private void triggerArchitectureValidator(AnalysisJob job, String token) {
        try {
            String validatorUrl = "http://architecture-validator-svc:80/validator/validate/" + job.getJobId();

            internalClient.post()
                    .uri(validatorUrl)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();

            System.out.println("✅ Validator Service triggered successfully.");
        } catch (Exception e) {
            System.err.println("⚠️ [WEBHOOK ERROR] Validator Trigger Failed: " + e.getMessage());
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

    private byte[] createMasterZip(byte[] terraformZip, byte[] ansibleZip, byte[] pipelineZip) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            if (terraformZip != null && terraformZip.length > 0) {
                appendZipToMaster(zos, terraformZip, "infrastructure/");
            }
            if (ansibleZip != null && ansibleZip.length > 0) {
                appendZipToMaster(zos, ansibleZip, "configuration/");
            }
            if (pipelineZip != null && pipelineZip.length > 0) {
                appendZipToMaster(zos, pipelineZip, "");
            }
            zos.finish();
            zos.flush();
        } catch (Exception e) {
            System.err.println("❌ [ZIP MERGER ERROR]  Failed to merge zips: " + e.getMessage());
            return null;
        }
        return baos.toByteArray();
    }

    private void appendZipToMaster(ZipOutputStream zos, byte[] zipData, String folderPrefix) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String newEntryName = folderPrefix + entry.getName();
                zos.putNextEntry(new ZipEntry(newEntryName));
                zis.transferTo(zos);
                zos.closeEntry();
            }
        }
    }

    public Optional<AnalysisJob> getAnalysisJob(String jobId) {
        return jobRepository.findById(jobId);
    }

    // =================================================================================
    //                 DIFF REVIEW METHODS (PLAN VS APPLY)
    // =================================================================================

    public FileDiffResponse getAnalysisReviewDetails(String jobId, String currentUserId) {
        // 1. Φέρνουμε το Draft Job
        AnalysisJob draftJob = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found with ID: " + jobId));

        // 2. SECURITY CHECK: Έλεγχος ιδιοκτησίας
        if (!draftJob.getUserId().equals(currentUserId)) {
            System.err.println("⛔ Security Alert: User " + currentUserId + " tried to access Job " + jobId);
            throw new RuntimeException("Unauthorized: You do not have permission to view this analysis.");
        }

        // 3. Έλεγχος αν ο Validator έχει τελειώσει
        if (!"COMPLETED".equals(draftJob.getValidatorStatus())) {
            throw new RuntimeException("Validation in progress. Please wait for the Validator to finish.");
        }

        // 4. Φέρνουμε το Validated Job (εφόσον ξέρουμε ότι είναι COMPLETED)
        ValidatorJob validatedJob = validatorJobRepository.findByAnalysisJobId(jobId)
                .orElseThrow(() -> new RuntimeException("Validated data not found for Job ID: " + jobId));

        List<FileDiffResponse.FileDiff> diffFiles = new ArrayList<>();

        // --- 5. TERRAFORM DIFF ---
        String draftTf = extractSmartFileFromZip(draftJob.getMasterZip(), "infrastructure/", ".tf");
        String validTf = extractSmartFileFromZip(validatedJob.getValidatedMasterZip(), "infrastructure/", ".tf");
        diffFiles.add(new FileDiffResponse.FileDiff("Terraform", "hcl",
                draftTf != null ? draftTf : "// No draft code",
                validTf != null ? validTf : "// No validated code"));

        // --- 6. ANSIBLE DIFF (Μόνο για VM) ---
        if ("VM".equalsIgnoreCase(draftJob.getComputeType())) {
            String draftAns = extractSmartFileFromZip(draftJob.getMasterZip(), "configuration/", ".yml", ".yaml");
            String validAns = extractSmartFileFromZip(validatedJob.getValidatedMasterZip(), "configuration/", ".yml", ".yaml");
            diffFiles.add(new FileDiffResponse.FileDiff("Ansible", "yaml",
                    draftAns != null ? draftAns : "# No draft config",
                    validAns != null ? validAns : "# No validated config"));
        }

        // --- 7. CI/CD PIPELINE DIFF ---
        String draftPipe = extractSmartFileFromZip(draftJob.getMasterZip(), "", ".yml", ".yaml");
        String validPipe = extractSmartFileFromZip(validatedJob.getValidatedMasterZip(), "", ".yml", ".yaml");
        if (draftPipe != null || validPipe != null) {
            diffFiles.add(new FileDiffResponse.FileDiff("CI/CD Pipeline", "yaml",
                    draftPipe != null ? draftPipe : "# No draft pipeline",
                    validPipe != null ? validPipe : "# No validated pipeline"));
        }

        return new FileDiffResponse(jobId, diffFiles);
    }

    /**
     * Εξάγει δυναμικά το πρώτο αρχείο που ταιριάζει σε συγκεκριμένο φάκελο και κατάληξη.
     */
    private String extractSmartFileFromZip(byte[] zipBytes, String folderPrefix, String... allowedExtensions) {
        if (zipBytes == null || zipBytes.length == 0) return null;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // Αν το αρχείο βρίσκεται μέσα στον σωστό φάκελο
                if (name.startsWith(folderPrefix)) {
                    // Ελέγχουμε αν έχει μία από τις επιτρεπτές καταλήξεις
                    for (String ext : allowedExtensions) {
                        if (name.endsWith(ext)) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            zis.transferTo(baos);
                            return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            System.err.println("⚠️ [ZIP ERROR] Could not extract from " + folderPrefix + ": " + e.getMessage());
        }
        return null;
    }

    public byte[] createComparisonZip(String jobId) {
        AnalysisJob draftJob = jobRepository.findById(jobId).orElseThrow();
        ValidatorJob validJob = validatorJobRepository.findByAnalysisJobId(jobId).orElseThrow();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 1. Βάζουμε τα αρχεία του AI Draft στον φάκελο ai-draft/
            if (draftJob.getMasterZip() != null) {
                appendZipWithPrefix(zos, draftJob.getMasterZip(), "ai-draft/");
            }
            // 2. Βάζουμε τα αρχεία του Validator στον φάκελο validated/
            if (validJob.getValidatedMasterZip() != null) {
                appendZipWithPrefix(zos, validJob.getValidatedMasterZip(), "validated/");
            }
            zos.finish();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create comparison zip", e);
        }
        return baos.toByteArray();
    }

    private void appendZipWithPrefix(ZipOutputStream zos, byte[] zipData, String prefix) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(prefix + entry.getName()));
                zis.transferTo(zos);
                zos.closeEntry();
            }
        }
    }
}