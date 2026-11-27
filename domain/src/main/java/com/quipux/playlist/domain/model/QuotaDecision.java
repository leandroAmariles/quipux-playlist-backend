package com.quipux.playlist.domain.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Whether a request fits in the quota of whoever asks and, if it does not, how long to wait.
 */
public record QuotaDecision(boolean allowed, Duration retryAfter) {

    public QuotaDecision {
        Objects.requireNonNull(retryAfter);
    }

    public static QuotaDecision permit() {
        return new QuotaDecision(true, Duration.ZERO);
    }

    public static QuotaDecision reject(Duration retryAfter) {
        return new QuotaDecision(false, retryAfter);
    }
}
