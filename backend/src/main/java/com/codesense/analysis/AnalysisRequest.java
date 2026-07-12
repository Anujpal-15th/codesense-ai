package com.codesense.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AnalysisRequest(
        @NotBlank String codeSnippet,
        /** "java" (default when absent) or "python". */
        @Pattern(regexp = "(?i)java|python", message = "language must be \"java\" or \"python\"")
        String language
) {
}
