package com.codesense.exec;

import java.time.Instant;

public record ExecutionResponse(
        ExecutionTrace trace,
        /**
         * The source actually compiled and run. Equal to the submitted source
         * unless {@code wasWrapped} is true, in which case the submitted snippet
         * had no {@code Main} entry point and this is the auto-generated runnable
         * version - the client should display THIS, not what it submitted, since
         * the trace's line numbers refer to this source.
         */
        String executedSourceCode,
        boolean wasWrapped,
        Instant createdAt
) {
}
