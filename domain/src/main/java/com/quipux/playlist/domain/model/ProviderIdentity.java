package com.quipux.playlist.domain.model;

import java.util.Objects;

/**
 * What produces an answer: the prompt version and the model alias.
 */
public record ProviderIdentity(String promptVersion, String model) {

    public ProviderIdentity {
        Objects.requireNonNull(promptVersion);
        Objects.requireNonNull(model);
    }
}
