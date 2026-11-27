package com.quipux.playlist.application.port.out;

import com.quipux.playlist.domain.model.QuotaDecision;
import reactor.core.publisher.Mono;

public interface RequestQuotaPort {

    /**
     * Counts a request of whoever asks, if it fits in their quota.
     */
    Mono<QuotaDecision> consume(String requester);
}
