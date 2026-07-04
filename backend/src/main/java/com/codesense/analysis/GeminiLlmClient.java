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
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "gemini")
class GeminiLlmClient implements LlmClient {

    private final RestClient geminiRestClient;
    private final ObjectMapper objectMapper;
    private final String model;

    GeminiLlmClient(RestClient geminiRestClient,
                     ObjectMapper objectMapper,
                     @Value("${gemini.model}") String model,
                     @Value("${gemini.api-key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY must be set when llm.provider=gemini");
        }
        this.geminiRestClient = geminiRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    @Override
    public AnalysisResult analyze(String codeSnippet) {
        GeminiRequest request = new GeminiRequest(
                new GeminiSystemInstruction(List.of(new GeminiPart(SYSTEM_PROMPT))),
                List.of(new GeminiContent("user", List.of(new GeminiPart(codeSnippet)))),
                new GeminiGenerationConfig(1024)
        );

        GeminiResponse response;
        try {
            response = geminiRestClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .body(request)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("Gemini API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AnalysisFailedException("Gemini API returned " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new AnalysisFailedException("Failed to reach Gemini API", e);
        }

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new AnalysisFailedException("Gemini API returned an empty response");
        }

        List<GeminiPart> parts = response.candidates().get(0).content().parts();
        if (parts == null || parts.isEmpty()) {
            throw new AnalysisFailedException("Gemini API returned an empty response");
        }

        return LlmResponseParser.parse("Gemini", parts.get(0).text(), objectMapper);
    }

    @Override
    public String completeRaw(String systemPrompt, String userMessage) {
        GeminiRequest request = new GeminiRequest(
                new GeminiSystemInstruction(List.of(new GeminiPart(systemPrompt))),
                List.of(new GeminiContent("user", List.of(new GeminiPart(userMessage)))),
                new GeminiGenerationConfig(4096)
        );

        GeminiResponse response;
        try {
            response = geminiRestClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .body(request)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("Gemini API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AnalysisFailedException("Gemini API returned " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new AnalysisFailedException("Failed to reach Gemini API", e);
        }

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new AnalysisFailedException("Gemini API returned an empty response");
        }

        List<GeminiPart> parts = response.candidates().get(0).content().parts();
        if (parts == null || parts.isEmpty()) {
            throw new AnalysisFailedException("Gemini API returned an empty response");
        }

        return LlmResponseParser.stripCodeFences(parts.get(0).text());
    }
}
