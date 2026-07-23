package com.codesense.config;

import com.codesense.analysis.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Per-IP token-bucket limiting for the two expensive POST endpoints
 * ({@code /api/analyses} costs an external LLM call, {@code /api/executions}
 * compiles + spawns a JVM), plus a generous limit on the single-record GET
 * endpoints ({@code /api/analyses/{id}}, {@code /api/executions/{id}}).
 *
 * <p>The GET limit is <b>not</b> a cross-user-data-leak defense - both
 * endpoints already scope by {@code findByIdAndUserId}, so a wrong id or a
 * mismatched X-User-Id 404s regardless, and the caller's own summary
 * endpoint already hands back every id they're allowed to read, so there's
 * nothing worth "enumerating" beyond what the caller can already see
 * directly. It's plain abuse/resource protection - a client hammering
 * thousands of single-record reads per second is still unwanted DB load,
 * same as any other endpoint. The summary list endpoints stay unlimited (a
 * single cheap query each, not id-driven).
 *
 * <p>In-memory only (Caffeine cache of IP -&gt; Bucket, evicted on access
 * idle), not backed by the project's existing (otherwise-unused) Redis
 * dependency - single-droplet deployment has no need for a distributed
 * limiter; Redis would be the upgrade path if this ever scales to multiple
 * instances.
 *
 * <p>Client IP resolution deliberately does NOT trust {@code X-Forwarded-For}
 * unconditionally - a reverse proxy (nginx/Caddy, for TLS) is expected in
 * front of Spring Boot on the deployment host, but the header is only
 * trusted when the direct TCP peer ({@code request.getRemoteAddr()}) is in
 * the configured trusted-proxy list. Otherwise an external client hitting
 * Spring directly could forge the header and dodge/frame another IP's limit.
 * Only the first hop of X-Forwarded-For is read, i.e. exactly one trusted
 * proxy is assumed between the client and this app - a chain of multiple
 * proxies is out of scope.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern HISTORY_BY_ID_PATH =
            Pattern.compile("^/api/(?:analyses|executions)/\\d+$");

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Set<String> trustedProxies;
    private final Bandwidth analysesLimit;
    private final Bandwidth executionsLimit;
    private final Bandwidth historyReadsLimit;
    private final Cache<String, Bucket> analysesBuckets;
    private final Cache<String, Bucket> executionsBuckets;
    private final Cache<String, Bucket> historyReadsBuckets;

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.trustedProxies = Set.copyOf(properties.trustedProxies());
        this.analysesLimit = toBandwidth(properties.analyses());
        this.executionsLimit = toBandwidth(properties.executions());
        this.historyReadsLimit = toBandwidth(properties.historyReads());
        this.analysesBuckets = newBucketCache();
        this.executionsBuckets = newBucketCache();
        this.historyReadsBuckets = newBucketCache();
    }

    private static Bandwidth toBandwidth(RateLimitProperties.BucketLimit limit) {
        return Bandwidth.builder()
                .capacity(limit.capacity())
                .refillGreedy(limit.refillTokens(), Duration.ofSeconds(limit.refillPeriodSeconds()))
                .build();
    }

    private static Cache<String, Bucket> newBucketCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(10))
                .maximumSize(100_000)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        Cache<String, Bucket> buckets = selectBucketCache(request);
        if (buckets == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Bandwidth limit = selectLimit(buckets);

        String clientIp = resolveClientIp(request);
        Bucket bucket = buckets.get(clientIp, key -> Bucket.builder().addLimit(limit).build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                new ErrorResponse("Rate limit exceeded. Try again in " + retryAfterSeconds + "s."));
    }

    private Cache<String, Bucket> selectBucketCache(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(method)) {
            if ("/api/analyses".equals(path)) {
                return analysesBuckets;
            }
            if ("/api/executions".equals(path)) {
                return executionsBuckets;
            }
        } else if ("GET".equalsIgnoreCase(method) && HISTORY_BY_ID_PATH.matcher(path).matches()) {
            return historyReadsBuckets;
        }
        return null;
    }

    private Bandwidth selectLimit(Cache<String, Bucket> buckets) {
        if (buckets == analysesBuckets) {
            return analysesLimit;
        }
        if (buckets == executionsBuckets) {
            return executionsLimit;
        }
        return historyReadsLimit;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }
        return forwardedFor.split(",")[0].trim();
    }
}
