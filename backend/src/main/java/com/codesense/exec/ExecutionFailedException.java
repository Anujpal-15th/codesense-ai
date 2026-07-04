package com.codesense.exec;

public class ExecutionFailedException extends RuntimeException {

    public ExecutionFailedException(String message) {
        super(message);
    }

    public ExecutionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
