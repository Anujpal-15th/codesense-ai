package com.codesense.analysis;

import jakarta.validation.constraints.NotBlank;

public record AnalysisRequest(@NotBlank String codeSnippet) {
}
