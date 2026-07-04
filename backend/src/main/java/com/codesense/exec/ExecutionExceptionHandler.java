package com.codesense.exec;

import com.codesense.analysis.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Explicit {@code @Order} is required here, not cosmetic: a bean with no
 * {@code @Order} defaults to {@code Ordered.LOWEST_PRECEDENCE} for comparison
 * purposes, same as the last-resort {@code UnhandledExceptionHandler}'s
 * explicit LOWEST_PRECEDENCE - ties aren't guaranteed to resolve in
 * declaration order, and were confirmed live to let the catch-all shadow
 * this class's more specific handlers. A concrete low value guarantees this
 * bean is always checked first.
 */
@RestControllerAdvice
@Order(0)
public class ExecutionExceptionHandler {

    @ExceptionHandler(CompilationFailedException.class)
    public ResponseEntity<ErrorResponse> handleCompilationFailed(CompilationFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ExecutionFailedException.class)
    public ResponseEntity<ErrorResponse> handleExecutionFailed(ExecutionFailedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(ex.getMessage()));
    }
}
