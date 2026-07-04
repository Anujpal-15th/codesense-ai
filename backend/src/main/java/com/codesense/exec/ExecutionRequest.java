package com.codesense.exec;

import jakarta.validation.constraints.NotBlank;

public record ExecutionRequest(@NotBlank String sourceCode) {
}
