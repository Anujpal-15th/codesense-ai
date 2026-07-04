package com.codesense.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "github-models")
class GithubModelsLlmClient implements LlmClient {

    private final RestClient githubModelsRestClient;
    private final ObjectMapper objectMapper;
    private final String model;

    GithubModelsLlmClient(RestClient githubModelsRestClient,
                           ObjectMapper objectMapper,
                           @Value("${github-models.model}") String model,
                           @Value("${github-models.token}") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GITHUB_MODELS_TOKEN must be set when llm.provider=github-models");
        }
        this.githubModelsRestClient = githubModelsRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    @Override
    public AnalysisResult analyze(String codeSnippet) {
        GithubModelsChatRequest request = new GithubModelsChatRequest(
                model,
                List.of(
                        new GithubModelsMessage("system", SYSTEM_PROMPT),
                        new GithubModelsMessage("user", codeSnippet)
                )
        );

        GithubModelsChatResponse response;
        try {
            response = githubModelsRestClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(GithubModelsChatResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("GitHub Models API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AnalysisFailedException("GitHub Models API returned " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new AnalysisFailedException("Failed to reach GitHub Models API", e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new AnalysisFailedException("GitHub Models API returned an empty response");
        }

        return LlmResponseParser.parse("GitHub Models", response.choices().get(0).message().content(), objectMapper);
    }

    @Override
    public String completeRaw(String systemPrompt, String userMessage) {
        GithubModelsChatRequest request = new GithubModelsChatRequest(
                model,
                List.of(
                        new GithubModelsMessage("system", systemPrompt),
                        new GithubModelsMessage("user", userMessage)
                )
        );

        GithubModelsChatResponse response;
        try {
            response = githubModelsRestClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(GithubModelsChatResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("GitHub Models API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AnalysisFailedException("GitHub Models API returned " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new AnalysisFailedException("Failed to reach GitHub Models API", e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new AnalysisFailedException("GitHub Models API returned an empty response");
        }

        return LlmResponseParser.stripCodeFences(response.choices().get(0).message().content());
    }
}
