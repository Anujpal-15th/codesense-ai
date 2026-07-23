package com.codesense.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class LlmResponseParser {

    private LlmResponseParser() {
    }

    static AnalysisResult parse(String providerName, String rawText, ObjectMapper objectMapper) {
        String jsonText = stripCodeFences(rawText);
        try {
            return objectMapper.readValue(jsonText, AnalysisResult.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse {} response as JSON: {}", providerName, rawText);
            throw new AnalysisFailedException("Could not parse " + providerName + "'s analysis response", e);
        }
    }

    /** @throws AnalysisFailedException if {@code raw} is null - a provider
     *          response shape with no text (e.g. a tool-call/refusal) - rather
     *          than a bare NullPointerException reaching the generic 500 handler. */
    static String stripCodeFences(String raw) {
        if (raw == null) {
            throw new AnalysisFailedException("Provider returned no text content to parse");
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\R?", "");
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}
