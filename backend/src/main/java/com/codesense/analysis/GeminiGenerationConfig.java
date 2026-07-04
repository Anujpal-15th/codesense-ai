package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;

record GeminiGenerationConfig(@JsonProperty("maxOutputTokens") int maxOutputTokens) {
}
