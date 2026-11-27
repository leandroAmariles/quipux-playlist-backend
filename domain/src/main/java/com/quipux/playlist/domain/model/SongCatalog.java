package com.quipux.playlist.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The songs that can be recommended, with a version that changes with their content or with the model that compares
 * them. Knowing the version needs neither the database nor the models.
 */
public final class SongCatalog {

    private static final int VERSION_LENGTH = 12;

    private final List<CatalogSong> songs;
    private final Map<UUID, CatalogSong> songsById;
    private final String embeddingModel;
    private final String version;

    public SongCatalog(List<CatalogSong> songs, String embeddingModel) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel);
        this.songs = List.copyOf(songs);
        this.songsById = this.songs.stream()
                .collect(Collectors.toUnmodifiableMap(CatalogSong::id, Function.identity(), (first, same) -> first));
        this.version = Fingerprint.sha256(Stream.concat(
                        Stream.of(embeddingModel),
                        this.songsById.keySet().stream().map(UUID::toString).sorted()))
                .substring(0, VERSION_LENGTH);
    }

    public List<CatalogSong> songs() {
        return songs;
    }

    /** The model whose embeddings compare the songs. */
    public String embeddingModel() {
        return embeddingModel;
    }

    public String version() {
        return version;
    }

    public Optional<CatalogSong> find(UUID id) {
        return Optional.ofNullable(songsById.get(id));
    }

    /** How many songs of the catalog are in a playlist. */
    public int countOf(List<Song> playlist) {
        Set<SongIdentity> known = playlist.stream()
                .map(song -> SongIdentity.of(song.title(), song.artist()))
                .collect(Collectors.toSet());
        return (int) songs.stream()
                .filter(song -> known.contains(SongIdentity.of(song.title(), song.artist())))
                .count();
    }
}
