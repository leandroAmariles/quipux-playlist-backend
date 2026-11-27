package com.quipux.playlist.domain.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What has to be done so that the index holds exactly the current catalog.
 */
public record CatalogIndexPlan(List<CatalogSong> toIndex, List<UUID> toRemove, int kept) {

    public CatalogIndexPlan {
        toIndex = List.copyOf(toIndex);
        toRemove = List.copyOf(toRemove);
    }

    /**
     * @param indexed the identifier of every indexed entry and the model that compared it
     */
    public static CatalogIndexPlan of(SongCatalog catalog, Map<UUID, String> indexed) {
        List<CatalogSong> toIndex = catalog.songs().stream()
                .filter(song -> !catalog.embeddingModel().equals(indexed.get(song.id())))
                .toList();
        List<UUID> toRemove = indexed.keySet().stream()
                .filter(id -> catalog.find(id).isEmpty())
                .sorted()
                .toList();
        return new CatalogIndexPlan(toIndex, toRemove, catalog.songs().size() - toIndex.size());
    }
}
