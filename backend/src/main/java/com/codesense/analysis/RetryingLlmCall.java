package com.codesense.analysis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/**
 * Retries a single LLM HTTP call when the provider returns 429 (rate
 * limited) - and only then. Any other status (bad auth, malformed request,
 * provider outage) is a real failure that retrying won't fix, so it's
 * rethrown on the first attempt exactly as before this existed.
 *
 * <p>Honors the provider's own {@code Retry-After} header when present -
 * exact, not a guess at how long the window is - falling back to a short
 * fixed backoff otherwise. Bounded to a small number of attempts since this
 * runs synchronously on the thread handling {@code POST /api/analyses}: an
 * unbounded retry would just hang the caller instead of degrading
 * gracefully. This absorbs a brief burst (a couple of requests too close
 * together); it deliberately does not help with a sustained outage or an
 * expired key - that needs a different fix (switching providers), not a
 * longer retry loop.
 */
@Slf4j
final class RetryingLlmCall {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MILLIS = {1_000, 3_000};

    private RetryingLlmCall() {
    }

    static <T> T call(String providerName, Supplier<T> httpCall) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return httpCall.get();
            } catch (RestClientResponseException e) {
                boolean rateLimited = e.getStatusCode().value() == 429;
                boolean lastAttempt = attempt == MAX_ATTEMPTS - 1;
                if (!rateLimited || lastAttempt) {
                    throw e;
                }
                long delay = retryDelayMillis(e, attempt);
                log.info("{} API rate-limited (429) - retrying in {}ms (attempt {}/{})",
                        providerName, delay, attempt + 2, MAX_ATTEMPTS);
                sleep(delay);
            }
        }
        // Unreachable: the loop always returns or throws, but the compiler
        // can't see that - satisfies the method's return type.
        throw new IllegalStateException("unreachable");
    }

    private static long retryDelayMillis(RestClientResponseException e, int attempt) {
        String retryAfter = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter.trim()) * 1_000L;
            } catch (NumberFormatException ignored) {
                // Retry-After can also be an HTTP-date, which is rare enough
                // for this use case to not be worth parsing - fall through.
            }
        }
        return BACKOFF_MILLIS[Math.min(attempt, BACKOFF_MILLIS.length - 1)];
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AnalysisFailedException("Interrupted while waiting to retry an LLM call", e);
        }
    }
}
