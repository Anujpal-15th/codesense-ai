package com.codesense.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "gemini")
public class GeminiClientConfig {

    @Bean
    RestClient geminiRestClient(@Value("${gemini.api-key}") String apiKey) {
        return RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", apiKey)
                .requestFactory(LlmRestClientTimeouts.hostedProviderFactory())
                .build();
    }
}
