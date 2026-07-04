package com.codesense.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "github-models")
public class GithubModelsClientConfig {

    @Bean
    RestClient githubModelsRestClient(@Value("${github-models.token}") String token) {
        return RestClient.builder()
                .baseUrl("https://models.github.ai/inference")
                .defaultHeader("Authorization", "Bearer " + token)
                .requestFactory(LlmRestClientTimeouts.hostedProviderFactory())
                .build();
    }
}
