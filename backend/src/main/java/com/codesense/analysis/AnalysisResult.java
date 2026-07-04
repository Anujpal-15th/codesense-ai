package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AnalysisResult(
        String pattern,
        String timeComplexity,
        String spaceComplexity,
        @JsonProperty("isOptimal") boolean isOptimal,
        String explanation,
        String readability,
        String structure,
        String styleSuggestions,
        String suggestedTimeComplexity,
        String efficiencySuggestions,
        Integer overallScore,
        String codeQuality,
        String maintainability,
        @JsonDeserialize(using = LenientStringListDeserializer.class) List<String> bugs,
        @JsonDeserialize(using = LenientStringListDeserializer.class) List<String> edgeCases,
        @JsonDeserialize(using = LenientStringListDeserializer.class) List<String> learningTips
) {
}
