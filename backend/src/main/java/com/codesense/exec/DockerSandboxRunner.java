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
 * Runs the debuggee inside a locked-down Docker container: bounded
 * memory/CPU/pids, a read-only root filesystem except a small tmpfs scratch
 * area, a hard wall-clock timeout enforced by the backend itself, and no
 * outbound network access. The JDWP debug port is published to host loopback
 * only ({@code 127.0.0.1}), never to all interfaces - it is unauthenticated
 * remote code execution and must not be LAN-reachable.
 *
 * <p><b>Egress blocking is NOT self-contained in these {@code docker run}
 * flags.</b> {@code --network none}/{@code --internal} would block egress but
 * ALSO disable the {@code -p} port publishing the JDI attach needs (Docker
 * publishes ports over the same bridge/NAT path those modes remove - verified).
 * So the container joins a normal user-defined bridge network
 * ({@code execution.sandbox.docker.network}, default {@code codesense-sandbox-net})
 * and egress is instead dropped by a host-level {@code DOCKER-USER} iptables
 * rule. That network and rule are a <b>deployment prerequisite</b> provisioned
 * once by {@code backend/docker/execution-sandbox/provision-sandbox-network.sh};
 * if they are absent the container has FULL internet access.
 *
 * <p><b>Validated on Linux only.</b> This runner's isolation is designed for
 * and must be verified on the Linux deployment host. It is intentionally NOT
 * verified on the Windows Docker Desktop dev machine, where {@code DOCKER-USER}
 * iptables is not cleanly reachable (containers run in a managed VM); dev uses
 * {@code local-process} instead. {@code provision-sandbox-network.sh} prints
 * egress/port verification commands to run on the deployment host after
 * provisioning - run them; this egress-isolation design has not been
 * confirmed end-to-end on real Linux.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "execution.sandbox", name = "type", havingValue = "docker")
class DockerSandboxRunner implements SandboxRunner {

    private static final Pattern LISTENING_PATTERN =
            Pattern.compile("Listening for transport dt_socket at address: (\\d+)");
    private static final int CONTAINER_DEBUG_PORT = 5005;

    /** Cap on how much captured docker output to fold into an error message. */
    private static final int MAX_ERROR_OUTPUT_CHARS = 2000;

    private final String image;
    private final String memory;
    private final String cpus;
    private final String network;

    DockerSandboxRunner(@Value("${execution.sandbox.docker.image}") String image,
                         @Value("${execution.sandbox.docker.memory}") String memory,
                         @Value("${execution.sandbox.docker.cpus}") String cpus,
                         @Value("${execution.sandbox.docker.network}") String network) {
        this.image = image;
        this.memory = memory;
        this.cpus = cpus;
        this.network = network;
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
        } catch (TimeoutException e) {
            // The container is (probably) still alive but never printed the JDWP
            // readiness line within the window - a genuine slow start / hang.
            String detail = describeFailure(process, outputBuffer);
            DockerSandboxHandle.forceKill(process, containerName);
            log.warn("Docker sandbox did not become ready within {}s.{}", readinessTimeout.toSeconds(), detail);
            throw new ExecutionFailedException(
                    "Docker sandbox container did not become ready within " + readinessTimeout.toSeconds() + "s."
                            + detail, e);
        } catch (ExecutionException e) {
            // pumpOutput completed the future exceptionally: the container's
            // output stream closed before the readiness line appeared, i.e. the
            // container exited early (image missing, bad flags, immediate crash).
            // Its real docker error text is sitting in outputBuffer - surface it.
            String detail = describeFailure(process, outputBuffer);
            DockerSandboxHandle.forceKill(process, containerName);
            log.warn("Docker sandbox container exited before signaling readiness.{}", detail);
            throw new ExecutionFailedException(
                    "Docker sandbox container exited before signaling readiness." + detail, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DockerSandboxHandle.forceKill(process, containerName);
            throw new ExecutionFailedException("Interrupted while waiting for Docker sandbox to become ready", e);
        }

        return new DockerSandboxHandle(process, containerName, "localhost", hostPort, outputBuffer);
    }

    /**
     * Builds a diagnostic suffix for a startup failure: the {@code docker run}
     * process's exit code (when it has already terminated) plus the captured
     * docker stdout/stderr (bounded). Without this the real cause - e.g.
     * "pull access denied ... repository does not exist", or a bad flag, or a
     * missing sandbox network - sits unread in {@code outputBuffer} and the
     * caller only sees a generic "did not become ready" 502.
     */
    private String describeFailure(Process process, ConcurrentLinkedQueue<String> outputBuffer) {
        StringBuilder sb = new StringBuilder();
        if (!process.isAlive()) {
            sb.append(" docker exit code: ").append(process.exitValue()).append('.');
        }
        String captured = drainBounded(outputBuffer);
        if (captured.isBlank()) {
            sb.append(" (no output captured from docker.)");
        } else {
            sb.append(" docker output: ").append(captured.strip());
        }
        return sb.toString();
    }

    private String drainBounded(ConcurrentLinkedQueue<String> outputBuffer) {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = outputBuffer.poll()) != null) {
            sb.append(line);
            if (sb.length() >= MAX_ERROR_OUTPUT_CHARS) {
                sb.append("…(truncated)");
                break;
            }
        }
        return sb.toString();
    }

    private List<String> buildDockerRunCommand(Path classOutputDir, String mainClassName, int hostPort, String containerName) {
        List<String> command = DockerRunCommandBuilder.baseFlags(network, memory, cpus, classOutputDir);
        // Loopback-only: the JDWP port is unauthenticated RCE, never expose it
        // beyond the host. Binding 0.0.0.0 would make it LAN-reachable.
        command.add("-p");
        command.add("127.0.0.1:" + hostPort + ":" + CONTAINER_DEBUG_PORT);
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
