package com.codesense.exec;

public class CompilationFailedException extends RuntimeException {

    public CompilationFailedException(String message) {
        super(message);
    }

    public CompilationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
