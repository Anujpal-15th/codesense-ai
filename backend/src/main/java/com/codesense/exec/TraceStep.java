package com.codesense.exec;

import java.util.List;

public record TraceStep(
        int stepIndex,
        String eventType,
        String threadName,
        List<StackFrameSnapshot> callStack,
        String consoleOutputDelta,
        TraceValue returnValue
) {
}
