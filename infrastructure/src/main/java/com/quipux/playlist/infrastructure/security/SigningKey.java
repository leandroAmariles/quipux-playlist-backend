package com.quipux.playlist.infrastructure.security;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class SigningKey {

    private static final String ENVIRONMENT_VARIABLE = "JWT_SECRET";
    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_BYTES = 32;

    private final SecretKey secretKey;

    public SigningKey(String secret) {
        byte[] bytes = secret == null || secret.isBlank() ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_BYTES) {
            throw new IllegalStateException(
                    ErrorMessageEnum.SIGNING_KEY_TOO_SHORT.withCode(ENVIRONMENT_VARIABLE, MIN_BYTES));
        }
        this.secretKey = new SecretKeySpec(bytes, ALGORITHM);
    }

    public SecretKey secretKey() {
        return secretKey;
    }
}
