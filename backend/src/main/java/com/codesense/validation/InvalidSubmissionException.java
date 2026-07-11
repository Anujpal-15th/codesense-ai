package com.codesense.validation;

/**
 * Thrown by {@link CodeSubmissionValidator} when submitted "code" isn't
 * analyzable Java — either it's another language (Python/JS) or it's a
 * natural-language instruction rather than actual code. Mapped to HTTP 400 by
 * the analysis package's GlobalExceptionHandler (application-wide advice).
 */
public class InvalidSubmissionException extends RuntimeException {
    public InvalidSubmissionException(String message) {
        super(message);
    }
}
