package com.codesense.exec;

public class ExecutionNotFoundException extends RuntimeException {

    public ExecutionNotFoundException(Long id) {
        super("No execution found with id " + id);
    }
}
