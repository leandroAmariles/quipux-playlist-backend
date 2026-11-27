package com.quipux.playlist.infrastructure.out.recommendation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.quipux.playlist.application.port.out.RequestQuotaPort;
import com.quipux.playlist.domain.model.QuotaDecision;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

/**
 * At most {@code limit} requests of each requester within any period of the length of the window, counted in memory.
 * Rejected requests are not counted.
 */
public class SlidingWindowRequestQuotaAdapter implements RequestQuotaPort {

    private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();

    private final int limit;
    private final long window;
    private final Ticker ticker;
    // Whoever has not asked for a whole window is forgotten.
    private final Cache<String, Deque<Long>> requests;

    public SlidingWindowRequestQuotaAdapter(int limit, Duration window, Ticker ticker) {
        if (limit < 1) {
            throw new IllegalStateException(ErrorMessageEnum.INVALID_REQUEST_QUOTA.withCode(limit));
        }
        this.limit = limit;
        this.window = window.toNanos();
        this.ticker = ticker;
        this.requests = Caffeine.newBuilder()
                .expireAfterAccess(window)
                .ticker(ticker)
                .executor(Runnable::run)
                .build();
    }

    @Override
    public Mono<QuotaDecision> consume(String requester) {
        return Mono.fromSupplier(() -> decide(requester));
    }

    private QuotaDecision decide(String requester) {
        AtomicReference<QuotaDecision> decision = new AtomicReference<>();
        // compute runs alone for each requester, so two requests never take the same place.
        requests.asMap().compute(requester, (key, times) -> {
            Deque<Long> recent = times == null ? new ArrayDeque<>() : times;
            decision.set(count(recent, ticker.read()));
            return recent;
        });
        return decision.get();
    }

    private QuotaDecision count(Deque<Long> recent, long now) {
        while (!recent.isEmpty() && now - recent.getFirst() >= window) {
            recent.removeFirst();
        }
        if (recent.size() < limit) {
            recent.addLast(now);
            return QuotaDecision.permit();
        }
        // The limit is at least one, so a full quota always holds an oldest request.
        long remaining = window - (now - recent.getFirst());
        // Whole seconds, rounded up, so that waiting them is always enough.
        return QuotaDecision.reject(Duration.ofSeconds(Math.max(1, Math.ceilDiv(remaining, NANOS_PER_SECOND))));
    }
}
