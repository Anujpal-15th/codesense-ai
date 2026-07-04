package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiCandidateContent(List<GeminiPart> parts) {
}
