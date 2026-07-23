package com.codesense.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "ollama")
class OllamaLlmClient implements LlmClient {

    private final RestClient ollamaRestClient;
    private final ObjectMapper objectMapper;
    private final String model;

    OllamaLlmClient(RestClient ollamaRestClient,
                     ObjectMapper objectMapper,
                     @Value("${ollama.model}") String model) {
        this.ollamaRestClient = ollamaRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    @Override
    public AnalysisResult analyze(String codeSnippet) {
        OllamaChatRequest request = new OllamaChatRequest(
                model,
                List.of(
                        new OllamaMessage("system", SYSTEM_PROMPT),
                        new OllamaMessage("user", codeSnippet)
                ),
                false,
                "json"
        );
        String rawText = LlmClientSupport.callAndExtractText("Ollama",
                () -> ollamaRestClient.post().uri("/api/chat").body(request).retrieve().body(OllamaChatResponse.class),
                r -> r.message().content());
        return LlmResponseParser.parse("Ollama", rawText, objectMapper);
    }

    @Override
    public String completeRaw(String systemPrompt, String userMessage) {
        OllamaChatRequest request = new OllamaChatRequest(
                model,
                List.of(
                        new OllamaMessage("system", systemPrompt),
                        new OllamaMessage("user", userMessage)
                ),
                false,
                null
        );
        String rawText = LlmClientSupport.callAndExtractText("Ollama",
                () -> ollamaRestClient.post().uri("/api/chat").body(request).retrieve().body(OllamaChatResponse.class),
                r -> r.message().content());
        return LlmResponseParser.stripCodeFences(rawText);
    }
}
