package com.codesense.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "ollama")
public class OllamaClientConfig {

    @Bean
    RestClient ollamaRestClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:11434")
                .build();
    }
}
