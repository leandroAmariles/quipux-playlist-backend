package com.quipux.playlist.infrastructure.out.catalog;

import com.quipux.playlist.domain.exception.SongCatalogUnavailableException;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Stands in for the database when the AI is disabled or the catalog has no database: the songs of the file are known,
 * but nothing can be indexed or searched.
 */
public class UnavailableSongCatalogAdapter implements SongCatalogAdapter {

    private final SongCatalog catalog;
    private final ErrorMessageEnum reason;

    public UnavailableSongCatalogAdapter(SongCatalog catalog, ErrorMessageEnum reason) {
        this.catalog = catalog;
        this.reason = reason;
    }

    @Override
    public SongCatalog catalog() {
        return catalog;
    }

    @Override
    public Mono<List<UUID>> nearest(List<Song> songs, int limit) {
        return unavailable();
    }

    @Override
    public Mono<Map<UUID, String>> indexed() {
        return unavailable();
    }

    @Override
    public Mono<Void> save(List<CatalogSong> songs) {
        return unavailable();
    }

    @Override
    public Mono<Void> remove(List<UUID> ids) {
        return unavailable();
    }

    private <T> Mono<T> unavailable() {
        return Mono.error(() -> new SongCatalogUnavailableException(reason));
    }
}
