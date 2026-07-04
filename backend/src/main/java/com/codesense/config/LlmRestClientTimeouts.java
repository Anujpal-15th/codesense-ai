package com.codesense.config;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Connect/read timeouts for the Anthropic/Gemini/Ollama {@code RestClient}
 * beans. Without these, a stalled LLM provider (connects, then never
 * responds) blocks the HTTP request thread indefinitely - none of the three
 * providers' RestClient.Builder configs set any timeout otherwise.
 *
 * <p>Split per provider tier, not shared, because a single value doesn't fit
 * both: live-measured, the actual 16-key analysis prompt against local
 * {@code llama3.2:1b} (Ollama) took 23.4s end to end on this CPU-only dev
 * machine - a single 20s read timeout (reasonable for a hosted API) falsely
 * failed genuinely-working Ollama requests. Hosted providers (Anthropic,
 * Gemini) are fast, reliable network calls and should still fail fast.
 */
final class LlmRestClientTimeouts {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration HOSTED_READ_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration LOCAL_READ_TIMEOUT = Duration.ofSeconds(45);

    private LlmRestClientTimeouts() {
    }

    /**
     * For hosted, network-based providers (Anthropic, Gemini) - fails fast
     * since a real connection either succeeds and responds quickly, or won't
     * work at all.
     */
    static ClientHttpRequestFactory hostedProviderFactory() {
        return factory(HOSTED_READ_TIMEOUT);
    }

    /**
     * For the local Ollama provider - CPU-bound inference on this dev
     * machine is measurably slower than a hosted API's network round trip,
     * so this needs real margin above the 23.4s worst case measured directly
     * against Ollama for the actual analysis prompt.
     */
    static ClientHttpRequestFactory localProviderFactory() {
        return factory(LOCAL_READ_TIMEOUT);
    }

    private static ClientHttpRequestFactory factory(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
