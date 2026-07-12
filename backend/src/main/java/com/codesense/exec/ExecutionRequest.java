package com.codesense.exec;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ExecutionRequest(
        @NotBlank String sourceCode,
        /**
         * "java" (default when absent - older clients and the regression suite
         * send only sourceCode) or "python".
         */
        @Pattern(regexp = "(?i)java|python", message = "language must be \"java\" or \"python\"")
        String language
) {
}
