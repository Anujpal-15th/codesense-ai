package com.codesense.exec;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the debuggee as a plain subprocess on the host JVM. Performs NO
 * isolation whatsoever - no network restriction, no memory/CPU/pids limits,
 * no filesystem restriction. Must never be the active sandbox in any
 * environment that accepts untrusted input without {@link DockerSandboxRunner}
 * also being enforced (see application.yml execution.sandbox.type).
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "execution.sandbox", name = "type", havingValue = "local-process", matchIfMissing = true)
class LocalProcessSandboxRunner implements SandboxRunner {

    private static final Pattern LISTENING_PATTERN =
            Pattern.compile("Listening for transport dt_socket at address: (\\d+)");

    /**
     * This bean only exists at all when execution.sandbox.type resolves to
     * local-process - including the "unset" default (matchIfMissing = true
     * above). That means a deploy that silently drops EXECUTION_SANDBOX_TYPE
     * (typo'd env var, missing systemd EnvironmentFile, etc.) falls back here
     * with zero indication anything is wrong. Fails loud instead of silent -
     * doesn't change behavior (still starts, matching the documented "dev has
     * no Docker" tradeoff), just makes a misconfigured deploy impossible to miss.
     */
    @PostConstruct
    void warnIfUnisolated() {
        log.warn("Execution sandbox is running in LOCAL-PROCESS mode: NO isolation - no network, memory, CPU, "
                + "or filesystem restriction on executed code. This must never be active in any environment that "
                + "accepts untrusted input. Set EXECUTION_SANDBOX_TYPE=docker to enable real isolation.");
    }

    @Override
    public SandboxHandle start(Path classOutputDir, String mainClassName, Duration readinessTimeout) {
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "java",
                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:0",
                    "-cp", classOutputDir.toString(),
                    mainClassName
            );
            // The JDWP "Listening for transport..." readiness line's stream
            // (stdout vs stderr) is not consistent across JDK builds - merge
            // both into one stream so we reliably see it regardless, and
            // because the trace model treats console output as a single
            // combined stream anyway.
            builder.redirectErrorStream(true);
            process = builder.start();
        } catch (IOException e) {
            throw new ExecutionFailedException("Failed to launch local sandbox process", e);
        }

        ConcurrentLinkedQueue<String> outputBuffer = new ConcurrentLinkedQueue<>();
        CompletableFuture<Integer> portFuture = new CompletableFuture<>();

        Thread outputReader = new Thread(() -> pumpOutput(process, outputBuffer, portFuture), "sandbox-output-reader");
        outputReader.setDaemon(true);
        outputReader.start();

        int port;
        try {
            port = portFuture.get(readinessTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            process.destroyForcibly();
            throw new ExecutionFailedException("Local sandbox process did not become ready in time", e);
        }

        return new LocalProcessSandboxHandle(process, "localhost", port, outputBuffer);
    }

    private void pumpOutput(Process process, ConcurrentLinkedQueue<String> outputBuffer, CompletableFuture<Integer> portFuture) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = LISTENING_PATTERN.matcher(line);
                if (!portFuture.isDone() && matcher.find()) {
                    portFuture.complete(Integer.parseInt(matcher.group(1)));
                    continue;
                }
                outputBuffer.add(line + System.lineSeparator());
            }
        } catch (IOException e) {
            log.debug("Sandbox output stream closed", e);
        } finally {
            portFuture.completeExceptionally(new IllegalStateException("Sandbox process exited before signaling readiness"));
        }
    }
}
