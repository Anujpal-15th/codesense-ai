package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record GeminiRequest(
        @JsonProperty("systemInstruction") GeminiSystemInstruction systemInstruction,
        List<GeminiContent> contents,
        GeminiGenerationConfig generationConfig
) {
}
