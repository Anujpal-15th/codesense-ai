package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record AnalysisResponse(
        Long id,
        String codeSnippet,
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
        List<String> bugs,
        List<String> edgeCases,
        List<String> learningTips,
        Instant createdAt
) {

    static AnalysisResponse from(Analysis analysis) {
        return new AnalysisResponse(
                analysis.getId(),
                analysis.getCodeSnippet(),
                analysis.getPattern(),
                analysis.getTimeComplexity(),
                analysis.getSpaceComplexity(),
                analysis.isOptimal(),
                analysis.getExplanation(),
                analysis.getReadability(),
                analysis.getStructure(),
                analysis.getStyleSuggestions(),
                analysis.getSuggestedTimeComplexity(),
                analysis.getEfficiencySuggestions(),
                analysis.getOverallScore(),
                analysis.getCodeQuality(),
                analysis.getMaintainability(),
                analysis.getBugs(),
                analysis.getEdgeCases(),
                analysis.getLearningTips(),
                analysis.getCreatedAt()
        );
    }
}
