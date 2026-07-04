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

        OllamaChatResponse response;
        try {
            response = ollamaRestClient.post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .body(OllamaChatResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("Ollama returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AnalysisFailedException("Ollama returned " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new AnalysisFailedException("Failed to reach local Ollama server (is it running?)", e);
        }

        if (response == null || response.message() == null || response.message().content() == null) {
            throw new AnalysisFailedException("Ollama returned an empty response");
        }

        return LlmResponseParser.parse("Ollama", response.message().content(), objectMapper);
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

        OllamaChatResponse response;
        try {
            response = ollamaRestClient.post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .body(OllamaChatResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("Ollama returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AnalysisFailedException("Ollama returned " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new AnalysisFailedException("Failed to reach local Ollama server (is it running?)", e);
        }

        if (response == null || response.message() == null || response.message().content() == null) {
            throw new AnalysisFailedException("Ollama returned an empty response");
        }

        return LlmResponseParser.stripCodeFences(response.message().content());
    }
}
