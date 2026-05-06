package com.christos_bramis.bram_vortex_repo_analyzer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // 1. Επιτρέπουμε τα στατικά αρχεία της React για να μπορεί να ξεκινήσει η εφαρμογή
                        .requestMatchers("/", "/index.html", "/static/**", "/*.js", "/*.css", "/*.json", "/*.ico", "/*.png").permitAll()

                        // 2. ΕΠΙΤΡΕΠΟΥΜΕ τα GET requests στα paths του Frontend (Refresh Fix)
                        // Αυτό επιτρέπει στον browser να πάρει το index.html χωρίς JWT
                        .requestMatchers(HttpMethod.GET, "/dashboard", "/dashboard/**", "/login").permitAll()

                        // 3. Επιτρέπουμε το Actuator
                        .requestMatchers("/actuator/**").permitAll()

                        // 4. ΟΛΑ τα υπόλοιπα (API calls, POST/PUT/DELETE) απαιτούν Authentication
                        .anyRequest().authenticated()
                )

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Επιτρέπουμε το origin της React (προσαρμογή αν τρέχει σε άλλη πόρτα)
        configuration.setAllowedOrigins(List.of("http://localhost", "http://127.0.0.1", "http://localhost:3000", "http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}