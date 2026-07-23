package com.codesense.exec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The lockdown flags shared by both {@code docker run} invocations
 * ({@link DockerSandboxRunner} for Java, {@link PythonExecutionHandler} for
 * Python) - bounded memory/CPU/pids, a read-only root filesystem except a
 * small tmpfs scratch area, and the read-only bind mount of the compiled/
 * prepared work dir. Was hand-built twice with the same flag sequence before
 * this extraction; each caller appends only what's actually
 * language-specific (Java's {@code -p} port publish + JDWP agent args vs.
 * Python's {@code --entrypoint python3} + script args).
 */
final class DockerRunCommandBuilder {

    private DockerRunCommandBuilder() {
    }

    static List<String> baseFlags(String network, String memory, String cpus, Path bindMountHostDir) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("--rm");
        // A normal user-defined bridge (so -p works, where applicable); egress
        // is dropped by the host DOCKER-USER iptables rule provisioned
        // alongside this network - NOT by these flags. See DockerSandboxRunner's
        // class javadoc for the full rationale.
        command.add("--network");
        command.add(network);
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
        command.add(bindMountHostDir.toAbsolutePath() + ":/work:ro");
        return command;
    }
}
