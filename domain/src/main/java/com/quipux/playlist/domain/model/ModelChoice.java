package com.quipux.playlist.domain.model;

import java.util.UUID;

/**
 * A candidate chosen by a model and why. Nothing else of what a model answers is used: the data of the song come from
 * the catalog. The reason may be missing.
 */
public record ModelChoice(UUID catalogId, String reason) {
}
