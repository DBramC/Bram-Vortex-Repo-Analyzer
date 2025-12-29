package com.christos_bramis.bram_vortex_repo_analyzer.service;

import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
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

    public String getSigningPublicKey() {
        // Το όνομα του κλειδιού που μου έδωσες
        String keyName = "jwt-signing-key";
        String path = "transit/keys/" + keyName;

        try {
            VaultResponse response = vaultTemplate.read(path);

            if (response.getData() != null) {
                Map<String, Object> data = response.getData();

                // 1. Βρίσκουμε την τελευταία έκδοση (latest version)
                Object latestVersionObj = data.get("latest_version");
                String latestVersion = String.valueOf(latestVersionObj);

                // 2. Πλοηγούμαστε στο JSON: keys -> {version} -> public_key
                if (data.containsKey("keys") && data.get("keys") instanceof Map) {
                    Map<String, Object> keysMap = (Map<String, Object>) data.get("keys");

                    if (keysMap.containsKey(latestVersion)) {
                        Map<String, Object> keyVersionData = (Map<String, Object>) keysMap.get(latestVersion);

                        if (keyVersionData.containsKey("public_key")) {
                            return (String) keyVersionData.get("public_key");
                        }
                    }
                }
            }
            System.err.println("❌ Public Key 'jwt-signing-key' not found in Vault.");
            return null;

        } catch (Exception e) {
            System.err.println("❌ Error fetching Public Key from Vault: " + e.getMessage());
            return null;
        }
    }

    public PublicKey getKeyFromPEM(String pem) throws Exception {
        String publicKeyPEM = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", ""); // Καθαρισμός

        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(encoded));
    }
}