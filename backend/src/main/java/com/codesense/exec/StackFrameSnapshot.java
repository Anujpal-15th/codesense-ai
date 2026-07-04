package com.codesense.exec;

import java.util.List;

public record StackFrameSnapshot(
        String className,
        String methodName,
        int lineNumber,
        List<VariableSnapshot> localVariables,
        VariableSnapshot thisObject
) {
}
