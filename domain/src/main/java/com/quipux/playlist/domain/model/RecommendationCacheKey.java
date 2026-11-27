package com.quipux.playlist.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * What an answer of the model depends on: every song of the playlist, the prompt, the model and the version of the
 * catalog, which also changes with the model of the embeddings.
 */
public record RecommendationCacheKey(
        String songsFingerprint, String promptVersion, String model, String catalogVersion) {

    public RecommendationCacheKey {
        Objects.requireNonNull(songsFingerprint);
        Objects.requireNonNull(promptVersion);
        Objects.requireNonNull(model);
        Objects.requireNonNull(catalogVersion);
    }

    public static RecommendationCacheKey of(List<Song> songs, ProviderIdentity identity, String catalogVersion) {
        return new RecommendationCacheKey(
                fingerprintOf(songs), identity.promptVersion(), identity.model(), catalogVersion);
    }

    private static String fingerprintOf(List<Song> songs) {
        return Fingerprint.sha256(songs.stream().flatMap(song -> Stream.of(
                song.title(), song.artist(), song.album(), song.releaseYear(), song.genre())));
    }
}
