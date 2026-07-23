package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Disables Gemini's internal "thinking"/reasoning pass for this call - see
 * the comment on {@link GeminiGenerationConfig} for why this exists.
 * {@code thinkingBudget=0} means "answer directly, don't spend tokens
 * reasoning first". A model variant that doesn't support thinking at all
 * simply ignores this field rather than erroring on it.
 */
record GeminiThinkingConfig(@JsonProperty("thinkingBudget") int thinkingBudget) {
}
