package com.quipux.playlist.infrastructure.out.recommendation;

import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.SongSample;
import java.time.Duration;

/**
 * What the adapter needs besides the chat model: who answers, how much of a playlist is sent, how long it waits and
 * how many calls may be in progress at once.
 */
public record LlmSettings(ProviderIdentity identity, SongSample sample, Duration timeout, int maxConcurrentCalls) {
}
