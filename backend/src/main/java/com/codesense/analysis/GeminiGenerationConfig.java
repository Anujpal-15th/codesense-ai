package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A {@code thinkingConfig.thinkingBudget=0} field was tried here to stop
 * {@code gemini-flash-latest} from spending part of {@code maxOutputTokens}
 * on an internal reasoning pass before the actual JSON answer (which was
 * truncating/corrupting responses) - but the exact request schema for that
 * varies by model generation and Gemini rejected it outright with a generic
 * "invalid argument" on this account's model. Sidestepping the schema
 * question entirely: just give the budget enough headroom for a reasoning
 * pass AND the full answer, which works regardless of which model generation
 * is live behind the "latest" alias.
 */
record GeminiGenerationConfig(@JsonProperty("maxOutputTokens") int maxOutputTokens) {
}
