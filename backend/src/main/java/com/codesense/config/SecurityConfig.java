package com.codesense.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * This is a stateless, no-login JSON API consumed cross-origin (see
 * CorsConfig) - there's no server-side session/cookie for CSRF to forge, and
 * every endpoint is meant to be reachable without authentication (identity
 * here means "opaque history-scoping id", not "logged in", see
 * {@link com.codesense.identity.UserIdentityService}). CSRF protection and
 * per-endpoint authorization exist to protect session-based state changes
 * against a forged cross-site request - neither applies here, so both are
 * deliberately off rather than configured against a threat model this app
 * doesn't have.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
