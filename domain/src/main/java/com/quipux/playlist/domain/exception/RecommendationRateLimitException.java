package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;

public class RecommendationRateLimitException extends BusinessException {

    private final long retryAfterSeconds;

    public RecommendationRateLimitException(long retryAfterSeconds) {
        super(ErrorMessageEnum.RECOMMENDATION_RATE_LIMIT);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
