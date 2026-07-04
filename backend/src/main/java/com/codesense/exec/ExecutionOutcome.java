package com.codesense.exec;

public enum ExecutionOutcome {
    NORMAL,
    EXCEPTION,
    TIMED_OUT,
    TRUNCATED,
    COMPILE_ERROR,
    SANDBOX_ERROR
}
