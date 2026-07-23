package com.codesense.analysis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared request/response handling for every {@link LlmClient} implementation
 * (Claude, Gemini, GitHub Models, Ollama) - each one differs only in its
 * request DTO and how it navigates its own response shape (content[0].text,
 * candidates[0].content.parts[0].text, choices[0].message.content, ...).
 * Everything else - retry-on-429 via {@link RetryingLlmCall}, HTTP error
 * wrapping, and safely extracting the raw text without letting a null
 * anywhere in an unexpected response shape (a tool-call/refusal response, a
 * provider outage returning a truncated body) escape as a bare
 * NullPointerException - was previously copy-pasted in each of the four
 * client classes.
 */
@Slf4j
final class LlmClientSupport {

    private LlmClientSupport() {
    }

    /**
     * Performs {@code httpCall} (with 429 retry), then applies
     * {@code textExtractor} to pull the raw completion text out of the
     * provider-specific response shape. Any failure along the way - a
     * transport error, an HTTP error status, a null/missing field anywhere
     * the extractor navigates, or a blank result - becomes a single
     * {@link AnalysisFailedException} instead of a raw NPE or
     * IndexOutOfBoundsException reaching the generic fallback handler.
     */
    static <T> String callAndExtractText(String providerName, Supplier<T> httpCall, Function<T, String> textExtractor) {
        T response;
        try {
            response = RetryingLlmCall.call(providerName, httpCall);
        } catch (RestClientResponseException e) {
            log.warn("{} API returned {}: {}", providerName, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AnalysisFailedException(providerName + " API returned " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new AnalysisFailedException("Failed to reach " + providerName + " API", e);
        }

        String text;
        try {
            text = response == null ? null : textExtractor.apply(response);
        } catch (RuntimeException e) {
            throw new AnalysisFailedException(providerName + " API returned an unexpected response shape", e);
        }
        if (text == null || text.isBlank()) {
            throw new AnalysisFailedException(providerName + " API returned an empty response");
        }
        return text;
    }
}
