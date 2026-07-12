package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Lightweight history-list row for {@code GET /api/analyses/summary}. Populated
 * by a JPQL constructor-expression query ({@link AnalysisRepository#findSummariesByUserId(String)})
 * that selects only these scalar columns — it never loads the three
 * {@code @ElementCollection} tables (bugs/edgeCases/learningTips) or the large
 * unused TEXT columns, which is what made the full-entity history query slow.
 *
 * <p>{@code codePreview} and {@code explanationExcerpt} are truncated here (not
 * in SQL, for portability) so the API response stays small; the full code and
 * explanation are only fetched per-row via {@code GET /api/analyses/{id}}.
 */
public record AnalysisSummaryResponse(
        Long id,
        String pattern,
        String timeComplexity,
        String spaceComplexity,
        @JsonProperty("isOptimal") boolean isOptimal,
        String codePreview,
        String explanationExcerpt,
        Instant createdAt
) {
    private static final int CODE_PREVIEW_LIMIT = 150;
    private static final int EXPLANATION_EXCERPT_LIMIT = 200;

    public AnalysisSummaryResponse {
        codePreview = truncate(codePreview, CODE_PREVIEW_LIMIT);
        explanationExcerpt = truncate(explanationExcerpt, EXPLANATION_EXCERPT_LIMIT);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
