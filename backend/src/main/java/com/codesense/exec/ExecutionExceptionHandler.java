package com.codesense.exec;

import com.codesense.analysis.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
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
