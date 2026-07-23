package com.codesense.exec;

import java.time.Instant;

/**
 * Lightweight history-list row for {@code GET /api/executions/summary} -
 * mirrors {@code com.codesense.analysis.AnalysisSummaryResponse}: a
 * constructor-expression projection that never loads the large
 * {@code traceJson}/{@code executedSourceCode} columns, only what a list
 * view needs.
 */
public record ExecutionHistorySummaryResponse(
        Long id,
        String language,
        String outcome,
        String codePreview,
        int totalStepsCaptured,
        Instant createdAt
) {
    private static final int CODE_PREVIEW_LIMIT = 150;

    public ExecutionHistorySummaryResponse {
        codePreview = truncate(codePreview, CODE_PREVIEW_LIMIT);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
