package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record ClaudeContentBlock(String type, String text) {
}
