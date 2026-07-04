package com.codesense.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicClientConfig {

    @Bean
    RestClient anthropicRestClient(@Value("${anthropic.api-key}") String apiKey) {
        return RestClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }
}
