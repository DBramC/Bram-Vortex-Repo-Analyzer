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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RepoService {

    private final RestClient restClient;
    private final VaultService vaultService;
    private final ChatModel chatModel;
    private final AnalysisJobRepository jobRepository; // <--- Σύνδεση με την Βάση
    private final ObjectMapper objectMapper;           // <--- Εργαλείο για μετατροπή Object σε JSON

    // Constructor Injection
    public RepoService(RestClient.Builder builder, VaultService vaultService, ChatModel chatModel,
                       AnalysisJobRepository jobRepository, ObjectMapper objectMapper) {
        // Χτίζουμε τον client με το base URL του GitHub
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

        String accessToken = vaultService.getGithubToken(userId);

        System.out.println("Starting analysis for Repo ID: " + request.getRepoId());
        System.out.println("Target URL: " + request.getRepoUrl());

        // 1. Παίρνουμε τον Cloud Provider
        String targetCloud = request.getCloudProvider() != null && !request.getCloudProvider().isEmpty()
                ? request.getCloudProvider()
                : "AWS";

        // 2. Εξάγουμε το "owner/repo" από το URL (π.χ. από https://github.com/dbramc/bram-vortex -> dbramc/bram-vortex)
        String ownerAndRepo = request.getRepoUrl()
                .replace("https://github.com/", "")
                .replace(".git", ""); // Αφαιρούμε το .git αν υπάρχει

        // 3. ΔΥΝΑΜΙΚΗ ΚΑΙ ΑΝΑΔΡΟΜΙΚΗ ΑΝΑΖΗΤΗΣΗ MANIFEST ΚΑΙ CONFIG FILE
        String realManifestContent = null;
        String realConfigContent = null; // <-- ΝΕΟ: Αποθήκευση του config
        String foundManifestPath = null;
        String foundConfigPath = null;   // <-- ΝΕΟ: Path του config

        System.out.println("🔍 Searching recursively for manifest and config files in: " + ownerAndRepo);

        try {
            // 3α. Παίρνουμε τη λίστα όλων των αρχείων αναδρομικά (recursive=1)
            Map<String, Object> treeResponse = restClient.get()
                    .uri("/repos/" + ownerAndRepo + "/git/trees/main?recursive=1")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            List<Map<String, String>> tree = (List<Map<String, String>>) treeResponse.get("tree");

            // Ορίζουμε τι ψάχνουμε
            List<String> priorityManifests = List.of("pom.xml", "package.json", "Dockerfile", "requirements.txt");
            List<String> priorityConfigs = List.of("application.properties", "application.yml", "application.yaml", ".env");

            // 3β. Ψάχνουμε το path για το Manifest
            for (String target : priorityManifests) {
                foundManifestPath = tree.stream()
                        .map(item -> item.get("path"))
                        .filter(path -> path.endsWith(target))
                        .findFirst()
                        .orElse(null);
                if (foundManifestPath != null) break;
            }

            // 3γ. Ψάχνουμε το path για το Config
            for (String target : priorityConfigs) {
                foundConfigPath = tree.stream()
                        .map(item -> item.get("path"))
                        .filter(path -> path.endsWith(target))
                        .findFirst()
                        .orElse(null);
                if (foundConfigPath != null) break;
            }

            // 3δ. Τραβάμε το περιεχόμενο του Manifest (Αν βρέθηκε)
            if (foundManifestPath != null) {
                realManifestContent = restClient.get()
                        .uri("/repos/" + ownerAndRepo + "/contents/" + foundManifestPath)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/vnd.github.v3.raw")
                        .retrieve()
                        .body(String.class);

                System.out.println("✅ Found manifest at path: " + foundManifestPath);
            }

            // 3ε. Τραβάμε το περιεχόμενο του Config (Αν βρέθηκε)
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
            // Fallback: Αν το branch δεν είναι 'main', μπορείς να δοκιμάσεις και το 'master' εδώ αν θες
        }

        // Αν δεν βρήκαμε κανένα αναγνωρίσιμο αρχείο MANIFEST, ρίχνουμε Exception (το config είναι προαιρετικό)
        if (realManifestContent == null) {
            throw new RuntimeException("No supported manifest file found in the repository (checked all subdirectories).");
        }

        // 4. Ετοιμάζουμε τον Converter του Spring AI
        var outputConverter = new BeanOutputConverter<>(InfrastructureAnalysis.class);
        String formatInstructions = outputConverter.getFormat();

        // 5. Φτιάχνουμε το Δυναμικό System Prompt χρησιμοποιώντας το ΠΡΑΓΜΑΤΙΚΟ ΠΕΡΙΕΧΟΜΕΝΟ
        String configSection = (realConfigContent != null && !realConfigContent.isEmpty())
                ? realConfigContent
                : "No explicit configuration file found. Assume framework defaults.";

        String promptMessage = String.format("""
        You are an expert Cloud Software Architect and AI Agent representing the 'Bram Vortex' platform.
        Your task is to analyze the following repository files and generate an "Architectural Blueprint" (Single Source of Truth).
        
        This blueprint will be routed to specialized microservices to automatically provision the infrastructure. 
        DO NOT GENERATE CODE. Generate only the specifications.
        
        TARGET CLOUD PROVIDER: %s
        MANIFEST FILE PATH: %s
        CONFIG FILE PATH: %s
        
        --- MANIFEST FILE CONTENT ---
        %s
        
        --- CONFIGURATION FILE CONTENT ---
        %s
        
        TASKS:
        1. Analyze the tech stack to identify the primary language, framework, and application type.
        2. Scan dependencies to detect required external infrastructure (e.g., PostgreSQL, Redis).
        3. Recommend the optimal compute infrastructure for %s (e.g., EKS, GKE, serverless containers).
        4. Identify the expected exposed port. CRITICAL: Check the CONFIGURATION FILE CONTENT above for custom ports (e.g., server.port=9090). If none is specified, output the framework's default port (e.g., 8080 for Spring).
        5. Outline the required CI/CD build steps and necessary monitoring metrics.
        
        CRITICAL INSTRUCTIONS FOR JSON OUTPUT:
        - You must respond EXCLUSIVELY with a valid, raw JSON object representing the blueprint.
        - DO NOT include ANY markdown formatting around the JSON object.
        - Provide ONLY raw JSON that strictly matches the schema provided below.
        
        SCHEMA INSTRUCTIONS:
        %s
        """,
                targetCloud,
                foundManifestPath,
                (foundConfigPath != null ? foundConfigPath : "None"),
                realManifestContent,
                configSection,
                targetCloud,
                formatInstructions);

        // 6. Δημιουργούμε το μοναδικό Job ID
        String jobId = UUID.randomUUID().toString();

        // 7. ΑΡΧΙΚΗ ΑΠΟΘΗΚΕΥΣΗ (ANALYZING): Σώζουμε το Job στη βάση ΠΡΙΝ απαντήσει το AI
        AnalysisJob job = new AnalysisJob();
        job.setJobId(jobId);
        job.setUserId(userId);
        job.setRepoId(request.getRepoId());

        // Προσοχή: Ελέγχουμε αν υπάρχει το getRepoName() στο Request σου, αν όχι βάζουμε το ownerAndRepo
        String repoName = request.getRepoName() != null ? request.getRepoName() : ownerAndRepo;
        job.setRepoName(repoName);

        job.setTargetCloud(targetCloud);
        job.setStatus("ANALYZING");

        // ΝΕΟ: Αποθήκευση του Prompt στη βάση (Βεβαιώσου ότι η AnalysisJob έχει το πεδίο promptMessage)
        job.setPromptMessage(promptMessage);

        jobRepository.save(job);

        // 8. Κάνουμε την κλήση στον Gemini Agent
        try {
            System.out.println("🤖 Asking Gemini to analyze and generate Blueprint for cloud: " + targetCloud + "...");
            String aiResponse = chatModel.call(promptMessage);

            // Μετατροπή της απάντησης σε Java Object (DTO)
            InfrastructureAnalysis analysisResult = outputConverter.convert(aiResponse);

            System.out.println("✅ Gemini Analysis Complete!");
            System.out.println("Primary Language: " + analysisResult.getPrimaryLanguage());
            System.out.println("Recommended Infra: " + analysisResult.getRecommendedCompute());

            // 9. ΤΕΛΙΚΗ ΑΠΟΘΗΚΕΥΣΗ (COMPLETED): Μετατρέπουμε το DTO σε JSON String και κάνουμε Update
            String jsonBlueprint = objectMapper.writeValueAsString(analysisResult);

            job.setBlueprintJson(jsonBlueprint);
            job.setStatus("COMPLETED");
            jobRepository.save(job);

            System.out.println("💾 Blueprint successfully saved in PostgresSQL! Job ID: " + jobId);


        } catch (Exception e) {
            System.err.println("❌ Error during AI Analysis: " + e.getMessage());
            // Ενημερώνουμε τη βάση ότι η ανάλυση απέτυχε
            job.setStatus("FAILED");
            jobRepository.save(job);
        }

        // 10. Επιστρέφουμε το Job ID στο Frontend
        return jobId;
    }
    /**
     * Επιστρέφει το Blueprint (JSON String) από τη βάση δεδομένων με βάση το Job ID.
     */
    public String getAnalysisResult(String jobId) {
        return jobRepository.findById(jobId)
                .map(AnalysisJob::getBlueprintJson)
                .orElse(null); // Αν δε βρεθεί, επιστρέφει null
    }
}