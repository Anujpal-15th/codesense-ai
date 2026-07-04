package com.codesense.exec;

public record VariableSnapshot(
        String name,
        String declaredType,
        TraceValue value
) {
}
