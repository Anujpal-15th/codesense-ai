package com.codesense.analysis;

import com.codesense.validation.InvalidSubmissionException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Applies application-wide (not just to controllers in this package) since
 * {@code @RestControllerAdvice} has no basePackages/assignableTypes
 * restriction - this is also the fallback for com.codesense.exec's
 * controller, alongside its own more-specific ExecutionExceptionHandler.
 *
 * <p>The true catch-all ({@code Exception.class}) deliberately does NOT live
 * here - see {@link UnhandledExceptionHandler} for why. Explicit {@code @Order}
 * for the same reason documented on {@link com.codesense.exec.ExecutionExceptionHandler}
 * - see that class for the live-confirmed regression this prevents.
 */
@RestControllerAdvice
@Order(0)
public class GlobalExceptionHandler {

    @ExceptionHandler(AnalysisFailedException.class)
    public ResponseEntity<ErrorResponse> handleAnalysisFailed(AnalysisFailedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(AnalysisNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(AnalysisNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Non-Java or instruction-only submissions, rejected before any LLM call or
     * code execution. Application-wide advice, so this covers both the analysis
     * and execution controllers. See {@link com.codesense.validation.CodeSubmissionValidator}.
     */
    @ExceptionHandler(InvalidSubmissionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSubmission(InvalidSubmissionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Without this, a {@code @Valid} failure (e.g. blank sourceCode/codeSnippet)
     * would fall through to the generic handleUnexpected() below and come back
     * as a misleading 500 instead of 400 - ExceptionHandlerExceptionResolver
     * (which runs @ExceptionHandler methods) is tried before Spring's own
     * DefaultHandlerExceptionResolver, so a broad Exception.class handler
     * would otherwise shadow the framework's built-in validation handling.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailed(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }
}
