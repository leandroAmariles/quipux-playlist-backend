package com.quipux.playlist.domain.model;

import com.quipux.playlist.domain.model.enums.PlaylistEventType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public record PlaylistEvent(
        UUID eventId,
        PlaylistEventType eventType,
        String playlistName,
        Integer songCount,
        Instant occurredAt) {

    public PlaylistEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        if (playlistName == null || playlistName.isBlank()) {
            throw new IllegalArgumentException("playlistName must not be blank");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (eventType == PlaylistEventType.PLAYLIST_CREATED && (songCount == null || songCount < 0)) {
            throw new IllegalArgumentException("songCount must be non-negative for PlaylistCreated");
        }
        if (eventType == PlaylistEventType.PLAYLIST_DELETED && songCount != null) {
            throw new IllegalArgumentException("songCount must be null for PlaylistDeleted");
        }
    }

    public static PlaylistEvent created(Playlist playlist) {
        Objects.requireNonNull(playlist, "playlist must not be null");
        return new PlaylistEvent(
                newEventId(),
                PlaylistEventType.PLAYLIST_CREATED,
                playlist.name(),
                playlist.songs().size(),
                Instant.now());
    }

    public static PlaylistEvent deleted(String playlistName) {
        return new PlaylistEvent(
                newEventId(),
                PlaylistEventType.PLAYLIST_DELETED,
                playlistName,
                null,
                Instant.now());
    }

    private static UUID newEventId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long mostSigBits = (random.nextLong() & ~0x0000_0000_0000_F000L) | 0x0000_0000_0000_4000L;
        long leastSigBits = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSigBits, leastSigBits);
    }
}
