package com.codesense.exec;

interface SandboxHandle extends AutoCloseable {

    String host();

    int port();

    /**
     * Returns any stdout/stderr text produced since the last call, or "" if none.
     */
    String drainOutput();

    @Override
    void close();
}
