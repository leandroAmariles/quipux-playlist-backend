package com.quipux.playlist.domain.model;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;

/**
 * A list of text fields as bytes that no other list produces, to identify content.
 */
final class Fingerprint {

    private static final String ALGORITHM = "SHA-256";
    private static final int MISSING = -1;

    private Fingerprint() {
    }

    // Every field goes after its length, so that two different lists never read the same.
    static byte[] bytesOf(Stream<String> fields) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        fields.forEachOrdered(field -> {
            byte[] content = field == null ? new byte[0] : field.getBytes(StandardCharsets.UTF_8);
            bytes.writeBytes(ByteBuffer.allocate(Integer.BYTES)
                    .putInt(0, field == null ? MISSING : content.length)
                    .array());
            bytes.writeBytes(content);
        });
        return bytes.toByteArray();
    }

    static String sha256(Stream<String> fields) {
        return HexFormat.of().formatHex(digest().digest(bytesOf(fields)));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
