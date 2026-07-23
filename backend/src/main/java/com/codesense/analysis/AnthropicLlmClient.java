package com.codesense.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "anthropic", matchIfMissing = true)
class AnthropicLlmClient implements LlmClient {

    private final RestClient anthropicRestClient;
    private final ObjectMapper objectMapper;
    private final String model;

    AnthropicLlmClient(RestClient anthropicRestClient,
                        ObjectMapper objectMapper,
                        @Value("${anthropic.model}") String model,
                        @Value("${anthropic.api-key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY must be set when llm.provider=anthropic");
        }
        this.anthropicRestClient = anthropicRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    @Override
    public AnalysisResult analyze(String codeSnippet) {
        ClaudeMessageRequest request = new ClaudeMessageRequest(
                model,
                1024,
                SYSTEM_PROMPT,
                List.of(new ClaudeMessage("user", codeSnippet))
        );
        String rawText = LlmClientSupport.callAndExtractText("Claude",
                () -> anthropicRestClient.post().uri("/messages").body(request).retrieve().body(ClaudeMessageResponse.class),
                r -> r.content().get(0).text());
        return LlmResponseParser.parse("Claude", rawText, objectMapper);
    }

    @Override
    public String completeRaw(String systemPrompt, String userMessage) {
        ClaudeMessageRequest request = new ClaudeMessageRequest(
                model,
                4096,
                systemPrompt,
                List.of(new ClaudeMessage("user", userMessage))
        );
        String rawText = LlmClientSupport.callAndExtractText("Claude",
                () -> anthropicRestClient.post().uri("/messages").body(request).retrieve().body(ClaudeMessageResponse.class),
                r -> r.content().get(0).text());
        return LlmResponseParser.stripCodeFences(rawText);
    }
}
