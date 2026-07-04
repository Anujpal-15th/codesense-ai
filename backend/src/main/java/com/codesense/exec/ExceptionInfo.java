package com.codesense.exec;

import java.util.List;

public record ExceptionInfo(
        String exceptionClassName,
        String message,
        List<String> stackTraceLines
) {
}
