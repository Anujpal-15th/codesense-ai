package com.codesense.exec;

public enum ExecutionOutcome {
    NORMAL,
    EXCEPTION,
    TIMED_OUT,
    TRUNCATED,

    // Currently unused: compile failures throw CompilationFailedException (-> HTTP 422)
    // and sandbox launch failures throw ExecutionFailedException (-> HTTP 502) before an
    // ExecutionTrace is ever built, so these two outcomes are never actually set. Left
    // in place, not wired up, per explicit decision - not removing or using them for now.
    COMPILE_ERROR,
    SANDBOX_ERROR
}
