package com.quipux.playlist.domain.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * What an answer of the model depends on: every song of the playlist, the prompt and the model. HU-13 adds the
 * version of the catalog.
 */
public record RecommendationCacheKey(String songsFingerprint, String promptVersion, String model) {

    private static final String ALGORITHM = "SHA-256";
    private static final int MISSING = -1;

    public RecommendationCacheKey {
        Objects.requireNonNull(songsFingerprint);
        Objects.requireNonNull(promptVersion);
        Objects.requireNonNull(model);
    }

    public static RecommendationCacheKey of(List<Song> songs, ProviderIdentity identity) {
        return new RecommendationCacheKey(fingerprintOf(songs), identity.promptVersion(), identity.model());
    }

    private static String fingerprintOf(List<Song> songs) {
        MessageDigest digest = digest();
        songs.stream()
                .flatMap(song -> Stream.of(
                        song.title(), song.artist(), song.album(), song.releaseYear(), song.genre()))
                .forEach(field -> add(digest, field));
        return HexFormat.of().formatHex(digest.digest());
    }

    // Every field goes after its length, so that two different songs never read the same.
    private static void add(MessageDigest digest, String field) {
        byte[] bytes = field == null ? new byte[0] : field.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(0, field == null ? MISSING : bytes.length));
        digest.update(bytes);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
