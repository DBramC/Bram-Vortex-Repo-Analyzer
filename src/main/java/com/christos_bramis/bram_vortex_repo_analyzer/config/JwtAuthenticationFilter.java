package com.christos_bramis.bram_vortex_repo_analyzer.config;

import com.christos_bramis.bram_vortex_repo_analyzer.service.VaultService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final VaultService vaultService;
    private PublicKey publicKey;

    public JwtAuthenticationFilter(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    /**
     * Αυτή η μέθοδος τρέχει ΜΟΝΟ ΜΙΑ ΦΟΡΑ όταν ξεκινάει η εφαρμογή.
     * Φέρνει το Public Key από τη Vault και το ετοιμάζει για χρήση.
     */
    @PostConstruct
    public void init() {
        try {
            System.out.println("🔄 Attempting to fetch Public Key from Vault...");

            // 1. Φέρνουμε το String (PEM) από τη Vault
            String pem = vaultService.getSigningPublicKey();

            if (pem != null) {
                // 2. Το μετατρέπουμε σε Java PublicKey αντικείμενο
                this.publicKey = vaultService.getKeyFromPEM(pem);
                System.out.println("✅ JWT Public Key loaded successfully!");
            } else {
                System.err.println("⚠️ Warning: Could not load JWT Public Key. Authentication will fail.");
            }
        } catch (Exception e) {
            System.err.println("❌ Critical Error loading Public Key: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Ελέγχουμε αν υπάρχει header και αν έχουμε φορτώσει το κλειδί
        if (publicKey != null && authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // Αφαιρούμε το "Bearer "

            try {
                // Επαλήθευση της υπογραφής του Token χρησιμοποιώντας το Public Key
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(publicKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String userId = claims.getSubject(); // Το ID του χρήστη (π.χ. "12345")

                // Αν το token είναι έγκυρο, βάζουμε τον χρήστη στο SecurityContext
                // (Εδώ βάζουμε κενή λίστα ρόλων, αν θες ρόλους τους διαβάζεις από τα claims)
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());

                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception e) {
                // Αν το token είναι ληγμένο ή ψεύτικο, απλά δεν κάνουμε authenticate.
                // Το Spring Security θα ρίξει 401/403 μετά.
                System.out.println("⛔ Invalid Token: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}