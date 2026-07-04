package com.codesense.exec;

import java.nio.file.Path;
import java.time.Duration;

interface SandboxRunner {

    /**
     * Launches the compiled class under isolation with a suspended JDWP debug
     * connection, ready for a JDI attach. Blocks until the sandbox signals
     * readiness or {@code readinessTimeout} elapses.
     */
    SandboxHandle start(Path classOutputDir, String mainClassName, Duration readinessTimeout);
}
