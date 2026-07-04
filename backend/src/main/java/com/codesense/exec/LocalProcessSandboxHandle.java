package com.codesense.exec;

import java.util.concurrent.ConcurrentLinkedQueue;

class LocalProcessSandboxHandle implements SandboxHandle {

    private final Process process;
    private final String host;
    private final int port;
    private final ConcurrentLinkedQueue<String> outputBuffer;

    LocalProcessSandboxHandle(Process process, String host, int port, ConcurrentLinkedQueue<String> outputBuffer) {
        this.process = process;
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
        process.destroyForcibly();
    }
}
