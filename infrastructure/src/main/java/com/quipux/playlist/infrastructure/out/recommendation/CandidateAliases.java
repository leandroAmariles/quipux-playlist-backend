package com.quipux.playlist.infrastructure.out.recommendation;

import com.quipux.playlist.domain.model.CatalogSong;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Short identifiers for the candidates of one request: {@code c1}, {@code c2}… A model copies them far better than the
 * identifiers of the catalog, and they take a fraction of the tokens.
 */
public final class CandidateAliases {

    private static final String PREFIX = "c";

    private final Map<String, CatalogSong> songsByAlias;

    private CandidateAliases(Map<String, CatalogSong> songsByAlias) {
        this.songsByAlias = Collections.unmodifiableMap(songsByAlias);
    }

    public static CandidateAliases of(List<CatalogSong> candidates) {
        Map<String, CatalogSong> songs = new LinkedHashMap<>();
        candidates.forEach(song -> songs.put(PREFIX + (songs.size() + 1), song));
        return new CandidateAliases(songs);
    }

    /** The aliases, in the order of the candidates. */
    public List<String> aliases() {
        return List.copyOf(songsByAlias.keySet());
    }

    public Map<String, CatalogSong> songs() {
        return songsByAlias;
    }

    /** The catalog identifier behind an alias, empty for any text that was not offered. */
    public Optional<UUID> idOf(String alias) {
        return Optional.ofNullable(songsByAlias.get(alias)).map(CatalogSong::id);
    }
}
