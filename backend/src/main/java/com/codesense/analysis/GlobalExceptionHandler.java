package com.codesense.analysis;

import com.codesense.validation.InvalidSubmissionException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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

    /**
     * The three framework-level client errors below have the same shadowing
     * problem the validation handler above solves: without an explicit handler
     * here, the catch-all Exception.class in {@link UnhandledExceptionHandler}
     * swallows them and returns a misleading 500 for what are really 4xx client
     * mistakes (confirmed live in the pre-launch pentest, finding F1). Messages
     * are deliberately generic - no framework internals or stack traces leak.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse("HTTP method " + ex.getMethod() + " is not supported for this endpoint"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse("Content-Type not supported; send application/json"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Malformed or unreadable request body"));
    }
}
