package com.codesense.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // Everything else externalizes via @Value; these two were hardcoded
    // literals, meaning a new deploy target (a Vercel preview URL, a staging
    // domain) needed a code change + redeploy instead of a config change.
    private final String[] allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // Vercel's rewrite in frontend/vercel.json proxies /api/* to this
                // backend, but that's a server-side rewrite, not a redirect - the
                // browser's original Origin header (the Vercel domain) still
                // travels through to us, so Spring's CORS check sees it as a
                // genuine cross-origin request and 403s with "Invalid CORS
                // request" unless that exact origin is allowed here too.
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
