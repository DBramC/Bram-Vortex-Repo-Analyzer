package com.christos_bramis.bram_vortex_repo_analyzer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                // 1. Σύνδεση με το Bean του CORS που ορίζουμε παρακάτω
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        // Όλα τα endpoints του dashboard απαιτούν authentication
                        .requestMatchers("/dashboard/**").authenticated()
                        .anyRequest().authenticated()
                )

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 2. ΕΠΙΤΡΕΠΟΥΜΕ ΜΟΝΟ ΤΟ ORIGIN ΤΟΥ FRONTEND
        // Αν η React τρέχει σε άλλη πόρτα (π.χ. 5173), άλλαξέ το εδώ.
        configuration.setAllowedOrigins(List.of("http://localhost", "http://127.0.0.1"));

        // 3. ΕΠΙΤΡΕΠΟΥΜΕ ΣΥΓΚΕΚΡΙΜΕΝΕΣ ΜΕΘΟΔΟΥΣ
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 4. ΕΠΙΤΡΕΠΟΥΜΕ ΣΥΓΚΕΚΡΙΜΕΝΑ HEADERS
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));

        // 5. ΕΠΙΤΡΕΠΟΥΜΕ CREDENTIALS (αν ποτέ χρησιμοποιήσεις cookies/sessions)
        configuration.setAllowCredentials(true);

        // 6. PREFLIGHT CACHE (Πόσο χρόνο ο browser θα "θυμάται" ότι το CORS είναι ΟΚ)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}