package com.codesense.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "github-models")
class GithubModelsLlmClient implements LlmClient {

    private final RestClient githubModelsRestClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String token;

    GithubModelsLlmClient(RestClient githubModelsRestClient,
                           ObjectMapper objectMapper,
                           @Value("${github-models.model}") String model,
                           @Value("${github-models.token}") String token) {
        this.githubModelsRestClient = githubModelsRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.token = token;
    }

    @Override
    public AnalysisResult analyze(String codeSnippet) {
        LlmClientSupport.requireApiKey("GitHub Models", token, "GITHUB_MODELS_TOKEN");
        GithubModelsChatRequest request = new GithubModelsChatRequest(
                model,
                List.of(
                        new GithubModelsMessage("system", SYSTEM_PROMPT),
                        new GithubModelsMessage("user", codeSnippet)
                )
        );
        String rawText = LlmClientSupport.callAndExtractText("GitHub Models",
                () -> githubModelsRestClient.post().uri("/chat/completions").body(request).retrieve().body(GithubModelsChatResponse.class),
                r -> r.choices().get(0).message().content());
        return LlmResponseParser.parse("GitHub Models", rawText, objectMapper);
    }

    @Override
    public String completeRaw(String systemPrompt, String userMessage) {
        LlmClientSupport.requireApiKey("GitHub Models", token, "GITHUB_MODELS_TOKEN");
        GithubModelsChatRequest request = new GithubModelsChatRequest(
                model,
                List.of(
                        new GithubModelsMessage("system", systemPrompt),
                        new GithubModelsMessage("user", userMessage)
                )
        );
        String rawText = LlmClientSupport.callAndExtractText("GitHub Models",
                () -> githubModelsRestClient.post().uri("/chat/completions").body(request).retrieve().body(GithubModelsChatResponse.class),
                r -> r.choices().get(0).message().content());
        return LlmResponseParser.stripCodeFences(rawText);
    }
}
