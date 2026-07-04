package com.codesense.exec;

import java.util.List;

public record ExecutionTrace(
        List<TraceStep> steps,
        ExecutionOutcome outcome,
        ExceptionInfo exceptionInfo,
        String consoleOutput,
        boolean truncated,
        int totalStepsCaptured,
        long executionTimeMillis
) {
}
