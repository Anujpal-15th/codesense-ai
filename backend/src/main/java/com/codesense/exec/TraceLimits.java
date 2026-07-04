package com.codesense.exec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@ConfigurationProperties(prefix = "execution.limits")
record TraceLimits(
        int maxSteps,
        int maxStackDepth,
        int maxObjectDepth,
        int maxArrayElements,
        int maxObjectFields,
        int maxStringLength,
        @DurationUnit(ChronoUnit.SECONDS) Duration timeoutSeconds,
        int maxConsoleOutputBytes
) {
}
