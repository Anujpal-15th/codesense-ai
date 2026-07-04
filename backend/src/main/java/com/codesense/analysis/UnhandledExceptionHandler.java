package com.codesense.analysis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * True last-resort fallback for any exception not handled by a more specific
 * {@code @ExceptionHandler} elsewhere (GlobalExceptionHandler,
 * ExecutionExceptionHandler) - without this, an unanticipated bug (e.g. a
 * NullPointerException deep in JavaTracer) would fall through to Spring
 * Boot's default error body, which doesn't populate this app's
 * ErrorResponse{error} shape the frontend reads.
 *
 * <p>Deliberately a SEPARATE {@code @RestControllerAdvice} bean, not a method
 * added to GlobalExceptionHandler, and deliberately given the lowest possible
 * {@code @Order}. Spring resolves {@code @ExceptionHandler}s by checking each
 * controller-advice bean in order and stopping at the first bean that has ANY
 * matching handler for the thrown exception's type hierarchy - it does not
 * keep searching other beans for a more specific match. A broad
 * {@code Exception.class} handler living in the same bean as (or ordered
 * before) a more specific one like CompilationFailedException's would
 * silently swallow it, turning a 422 into a misleading 500. Confirmed this
 * exact regression live while implementing this fix - do not merge this
 * handler back into another advice class without re-verifying ordering.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class UnhandledExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception reached the last-resort fallback handler", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An unexpected server error occurred."));
    }
}
