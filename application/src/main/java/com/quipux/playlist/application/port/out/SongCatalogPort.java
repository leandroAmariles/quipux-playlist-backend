package com.quipux.playlist.application.port.out;

import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.SongCatalog;
import java.util.List;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface SongCatalogPort {

    /** The current catalog. It is fixed while the application runs and needs no input or output. */
    SongCatalog catalog();

    /**
     * The identifiers of the indexed songs most similar to the given ones, nearest first and at most {@code limit}.
     * Signals an error when the catalog cannot be searched.
     */
    Mono<List<UUID>> nearest(List<Song> songs, int limit);
}
