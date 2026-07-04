package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record ClaudeMessageRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        String system,
        List<ClaudeMessage> messages
) {
}
