package com.quipux.playlist.application.port.out;

import com.quipux.playlist.domain.model.CatalogSong;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * The index where the songs of the catalog can be compared by similarity.
 */
public interface CatalogIndexPort {

    /** The identifier of every indexed entry and the model that compared it. */
    Mono<Map<UUID, String>> indexed();

    /** Indexes the songs, replacing the entries that already have their identifiers. */
    Mono<Void> save(List<CatalogSong> songs);

    Mono<Void> remove(List<UUID> ids);
}
