package com.quipux.playlist.infrastructure.out.catalog;

import com.openai.errors.OpenAIException;
import com.quipux.playlist.domain.exception.SongCatalogUnavailableException;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Turns a failure of the catalog into the domain error. Neither its message nor its cause is kept: what the gateway
 * or the database answer may repeat data or secrets.
 */
final class CatalogFailures {

    private static final int MAX_CAUSE_DEPTH = 20;

    private CatalogFailures() {
    }

    static SongCatalogUnavailableException translate(Throwable error) {
        if (error instanceof SongCatalogUnavailableException known) {
            return known;
        }
        if (error instanceof TimeoutException) {
            return new SongCatalogUnavailableException(ErrorMessageEnum.CATALOG_TIMEOUT);
        }
        boolean fromTheProvider = Stream.iterate(error, Objects::nonNull, Throwable::getCause)
                .limit(MAX_CAUSE_DEPTH)
                .anyMatch(OpenAIException.class::isInstance);
        return new SongCatalogUnavailableException(fromTheProvider
                ? ErrorMessageEnum.CATALOG_EMBEDDINGS_UNAVAILABLE
                : ErrorMessageEnum.CATALOG_UNREACHABLE);
    }
}
