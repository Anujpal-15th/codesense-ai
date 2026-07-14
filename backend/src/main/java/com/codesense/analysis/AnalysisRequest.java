package com.codesense.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AnalysisRequest(
        // Upper bound rejects multi-MB DoS payloads at binding time (400), before
        // the O(n) regex validator/LLM ever run. 100k chars is ~2500 lines - far
        // above any legitimate interview snippet, so it never trips real use.
        @NotBlank @Size(max = 100_000, message = "must be at most 100000 characters")
        String codeSnippet,
        /** "java" (default when absent) or "python". */
        @Pattern(regexp = "(?i)java|python", message = "language must be \"java\" or \"python\"")
        String language
) {
}
