package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code thinkingConfig} defaults to null (thinking left on) unless a caller
 * passes one - added after {@code gemini-flash-latest} started emitting its
 * internal reasoning text before/instead of the requested JSON, or getting
 * cut off mid-JSON: newer "thinking" Gemini models spend part of
 * {@code maxOutputTokens} on an internal reasoning pass before the actual
 * answer, so a budget sized for "just the JSON" silently truncates the real
 * response. See {@link GeminiThinkingConfig}.
 */
record GeminiGenerationConfig(
        @JsonProperty("maxOutputTokens") int maxOutputTokens,
        @JsonProperty("thinkingConfig") GeminiThinkingConfig thinkingConfig
) {
}
