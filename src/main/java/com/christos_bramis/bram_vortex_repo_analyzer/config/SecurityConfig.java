package com.christos_bramis.bram_vortex_repo_analyzer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                // 1. Απενεργοποίηση CSRF (δεν χρειάζεται σε REST APIs με JWT)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Stateless Session (Δεν κρατάμε cookies, κάθε request θέλει token)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. Απενεργοποίηση Form Login & Basic Auth (για να μην κάνει redirects)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 4. Ορισμός Κανόνων Πρόσβασης
                .authorizeHttpRequests(auth -> auth
                        // Εδώ ορίζεις ποια endpoints είναι δημόσια
                        .requestMatchers("/actuator/**").permitAll() // Health checks (αν έχεις)

                        // Όλα τα υπόλοιπα απαιτούν έγκυρο JWT
                        .anyRequest().authenticated()
                )

                // 5. Προσθήκη του δικού μας φίλτρου ΠΡΙΝ το default φίλτρο
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}