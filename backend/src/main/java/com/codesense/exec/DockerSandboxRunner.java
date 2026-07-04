package com.codesense.exec;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the debuggee inside a locked-down Docker container: no network
 * access, bounded memory/CPU/pids, a read-only root filesystem except a
 * small tmpfs scratch area, and a hard wall-clock timeout enforced by the
 * backend itself (not just relying on Docker/the container). This is the
 * real sandbox - see the security comment on execution.sandbox.type in
 * application.yml.
 *
 * <p><b>Not exercised in the environment this was developed in</b> (no
 * Docker available there). Verify on a Docker-capable machine before
 * relying on its isolation guarantees - see CLAUDE.md for the specific
 * claims that still need re-confirming (port-publish path, readiness-line
 * buffering behavior, whether the explicit {@code docker kill} backstop in
 * {@link DockerSandboxHandle} is actually necessary).
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "execution.sandbox", name = "type", havingValue = "docker")
class DockerSandboxRunner implements SandboxRunner {

    private static final Pattern LISTENING_PATTERN =
            Pattern.compile("Listening for transport dt_socket at address: (\\d+)");
    private static final int CONTAINER_DEBUG_PORT = 5005;

    private final String image;
    private final String memory;
    private final String cpus;

    DockerSandboxRunner(@Value("${execution.sandbox.docker.image}") String image,
                         @Value("${execution.sandbox.docker.memory}") String memory,
                         @Value("${execution.sandbox.docker.cpus}") String cpus) {
        this.image = image;
        this.memory = memory;
        this.cpus = cpus;
    }

    @Override
    public SandboxHandle start(Path classOutputDir, String mainClassName, Duration readinessTimeout) {
        int hostPort = findFreePort();
        String containerName = "codesense-exec-" + UUID.randomUUID();

        Process process;
        try {
            List<String> command = buildDockerRunCommand(classOutputDir, mainClassName, hostPort, containerName);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
        } catch (IOException e) {
            throw new ExecutionFailedException("Failed to launch Docker sandbox container", e);
        }

        ConcurrentLinkedQueue<String> outputBuffer = new ConcurrentLinkedQueue<>();
        CompletableFuture<Integer> readyFuture = new CompletableFuture<>();

        Thread outputReader = new Thread(
                () -> pumpOutput(process, outputBuffer, readyFuture, hostPort), "docker-sandbox-output-reader");
        outputReader.setDaemon(true);
        outputReader.start();

        try {
            readyFuture.get(readinessTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            DockerSandboxHandle.forceKill(process, containerName);
            throw new ExecutionFailedException("Docker sandbox container did not become ready in time", e);
        }

        return new DockerSandboxHandle(process, containerName, "localhost", hostPort, outputBuffer);
    }

    private List<String> buildDockerRunCommand(Path classOutputDir, String mainClassName, int hostPort, String containerName) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("--rm");
        command.add("--network");
        command.add("none");
        command.add("--memory");
        command.add(memory);
        command.add("--memory-swap");
        command.add(memory);
        command.add("--cpus");
        command.add(cpus);
        command.add("--pids-limit");
        command.add("128");
        command.add("--read-only");
        command.add("--tmpfs");
        command.add("/tmp:rw,size=16m,mode=1777");
        command.add("-v");
        command.add(classOutputDir.toAbsolutePath() + ":/work:ro");
        command.add("-p");
        command.add(hostPort + ":" + CONTAINER_DEBUG_PORT);
        command.add("--name");
        command.add(containerName);
        command.add(image);
        command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:" + CONTAINER_DEBUG_PORT);
        command.add("-Xmx192m");
        command.add("-XX:+UseSerialGC");
        command.add("-cp");
        command.add("/work");
        command.add(mainClassName);
        return command;
    }

    private void pumpOutput(Process process, ConcurrentLinkedQueue<String> outputBuffer,
                             CompletableFuture<Integer> readyFuture, int hostPort) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // The readiness line reports the port INSIDE the container
                // (CONTAINER_DEBUG_PORT), not our host-published port - we
                // already know which host port to connect to (hostPort, chosen
                // before launch), so the line is only used as a ready signal,
                // its captured port number is intentionally not used here.
                Matcher matcher = LISTENING_PATTERN.matcher(line);
                if (!readyFuture.isDone() && matcher.find()) {
                    readyFuture.complete(hostPort);
                    continue;
                }
                outputBuffer.add(line + System.lineSeparator());
            }
        } catch (IOException e) {
            log.debug("Docker sandbox output stream closed", e);
        } finally {
            readyFuture.completeExceptionally(new IllegalStateException("Sandbox container exited before signaling readiness"));
        }
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new ExecutionFailedException("Failed to allocate a free host port for the sandbox debug port", e);
        }
    }
}
