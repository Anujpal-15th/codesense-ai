package com.codesense.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "gemini")
class GeminiLlmClient implements LlmClient {

    private final RestClient geminiRestClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiKey;

    GeminiLlmClient(RestClient geminiRestClient,
                     ObjectMapper objectMapper,
                     @Value("${gemini.model}") String model,
                     @Value("${gemini.api-key}") String apiKey) {
        this.geminiRestClient = geminiRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public AnalysisResult analyze(String codeSnippet) {
        LlmClientSupport.requireApiKey("Gemini", apiKey, "GEMINI_API_KEY");
        GeminiRequest request = new GeminiRequest(
                new GeminiSystemInstruction(List.of(new GeminiPart(SYSTEM_PROMPT))),
                List.of(new GeminiContent("user", List.of(new GeminiPart(codeSnippet)))),
                new GeminiGenerationConfig(8192)
        );
        String rawText = LlmClientSupport.callAndExtractText("Gemini",
                () -> geminiRestClient.post().uri("/models/{model}:generateContent", model).body(request).retrieve().body(GeminiResponse.class),
                r -> r.candidates().get(0).content().parts().get(0).text());
        return LlmResponseParser.parse("Gemini", rawText, objectMapper);
    }

    @Override
    public String completeRaw(String systemPrompt, String userMessage) {
        LlmClientSupport.requireApiKey("Gemini", apiKey, "GEMINI_API_KEY");
        GeminiRequest request = new GeminiRequest(
                new GeminiSystemInstruction(List.of(new GeminiPart(systemPrompt))),
                List.of(new GeminiContent("user", List.of(new GeminiPart(userMessage)))),
                new GeminiGenerationConfig(8192)
        );
        String rawText = LlmClientSupport.callAndExtractText("Gemini",
                () -> geminiRestClient.post().uri("/models/{model}:generateContent", model).body(request).retrieve().body(GeminiResponse.class),
                r -> r.candidates().get(0).content().parts().get(0).text());
        return LlmResponseParser.stripCodeFences(rawText);
    }
}
