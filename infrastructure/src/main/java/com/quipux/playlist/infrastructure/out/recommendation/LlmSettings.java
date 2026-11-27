package com.quipux.playlist.infrastructure.out.recommendation;

import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.SongSample;
import java.time.Duration;

/**
 * What the adapter needs besides the chat model: who answers, how much of a playlist is sent, how long it waits, how
 * many calls may be in progress at once and, if its traces are to be grouped, under which session.
 */
public record LlmSettings(
        ProviderIdentity identity,
        SongSample sample,
        Duration timeout,
        int maxConcurrentCalls,
        String traceSession) {

    public LlmSettings(ProviderIdentity identity, SongSample sample, Duration timeout, int maxConcurrentCalls) {
        this(identity, sample, timeout, maxConcurrentCalls, "");
    }
}
