package com.codesense.exec;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExecutionRequest(
        // Upper bound rejects multi-MB DoS payloads at binding time (400), before
        // compilation or the sandbox ever run. 100k chars is ~2500 lines - far
        // above any legitimate submission, so it never trips real use.
        @NotBlank @Size(max = 100_000, message = "must be at most 100000 characters")
        String sourceCode,
        /**
         * "java" (default when absent - older clients and the regression suite
         * send only sourceCode) or "python".
         */
        @Pattern(regexp = "(?i)java|python", message = "language must be \"java\" or \"python\"")
        String language
) {
}
