package com.codesense.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "rate-limit")
record RateLimitProperties(
        boolean enabled,
        List<String> trustedProxies,
        BucketLimit analyses,
        BucketLimit executions,
        BucketLimit historyReads
) {
    record BucketLimit(long capacity, long refillTokens, long refillPeriodSeconds) {
    }
}
