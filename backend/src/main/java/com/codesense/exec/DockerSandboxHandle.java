package com.codesense.exec;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
class DockerSandboxHandle implements SandboxHandle {

    private final Process dockerRunProcess;
    private final String containerName;
    private final String host;
    private final int port;
    private final ConcurrentLinkedQueue<String> outputBuffer;

    DockerSandboxHandle(Process dockerRunProcess, String containerName, String host, int port,
                         ConcurrentLinkedQueue<String> outputBuffer) {
        this.dockerRunProcess = dockerRunProcess;
        this.containerName = containerName;
        this.host = host;
        this.port = port;
        this.outputBuffer = outputBuffer;
    }

    @Override
    public String host() {
        return host;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public String drainOutput() {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = outputBuffer.poll()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    @Override
    public void close() {
        forceKill(dockerRunProcess, containerName);
    }

    /**
     * Kills both the {@code docker run} client process and the container it
     * launched. {@code destroyForcibly()} on the client process alone is NOT
     * reliably sufficient to guarantee the container itself dies: {@code docker
     * run} is a client attached to the Docker daemon, and killing that client
     * doesn't necessarily stop the container the daemon is still running on its
     * behalf - this explicit {@code docker kill} closes that gap directly.
     * Unverified without Docker available in the environment this was built in;
     * killing an already-gone container is a harmless no-op either way.
     */
    static void forceKill(Process dockerRunProcess, String containerName) {
        dockerRunProcess.destroyForcibly();
        try {
            new ProcessBuilder("docker", "kill", containerName).start().waitFor(5, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            log.debug("docker kill for {} failed or timed out (container may already be gone)", containerName, e);
        }
    }
}
