package com.christos_bramis.bram_vortex_repo_analyzer.service;

import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;

@Service
public class VaultService {

    private final VaultTemplate vaultTemplate;

    // Το όνομα του κλειδιού μέσα στο JSON που έχεις αποθηκεύσει
    private static final String TOKEN_KEY = "github_token";

    public VaultService(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    public String getGithubToken(String userId) {
        // Στο KV 1 το path είναι ακριβώς όπως το είπες:
        // secret/users/{userId}
        String secretPath = "secret/users/" + userId;

        try {
            // Χρησιμοποιούμε την απλή μέθοδο .read()
            VaultResponse response = vaultTemplate.read(secretPath);

            if (response.getData() != null) {
                Map<String, Object> data = response.getData();

                if (data.containsKey(TOKEN_KEY)) {
                    return (String) data.get(TOKEN_KEY);
                }
            }

            // Αν δεν βρέθηκε το secret ή το κλειδί
            System.err.println("Warning: Token not found in Vault for path: " + secretPath);
            return null;

        } catch (Exception e) {
            System.err.println("Error reading from Vault: " + e.getMessage());
            return null;
        }
    }
}