package com.codesense.exec;

import java.time.Instant;

/**
 * Full record for {@code GET /api/executions/{id}} - unlike the live POST
 * response ({@link ExecutionResponse}), a history detail also needs the
 * original {@code sourceCode}/{@code language} the caller submitted, so the
 * frontend can restore the whole workspace (editor content, language
 * selector), not just replay the trace.
 */
public record ExecutionHistoryDetailResponse(
        Long id,
        String language,
        String sourceCode,
        ExecutionTrace trace,
        String executedSourceCode,
        boolean wasWrapped,
        Instant createdAt
) {
    static ExecutionHistoryDetailResponse from(ExecutionHistory history, ExecutionTrace trace) {
        return new ExecutionHistoryDetailResponse(
                history.getId(),
                history.getLanguage(),
                history.getSourceCode(),
                trace,
                history.getExecutedSourceCode(),
                history.isWasWrapped(),
                history.getCreatedAt());
    }
}
