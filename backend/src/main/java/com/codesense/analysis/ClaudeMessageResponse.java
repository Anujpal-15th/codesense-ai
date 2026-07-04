package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record ClaudeMessageResponse(List<ClaudeContentBlock> content) {
}
