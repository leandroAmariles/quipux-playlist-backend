package com.quipux.playlist.domain.model;

import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.SONG_GENRE_MAX_LENGTH;

import com.quipux.playlist.domain.model.enums.GenreSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record GenreCatalog(GenreSource source, List<String> genres) {

    public GenreCatalog {
        Objects.requireNonNull(source);
        genres = usable(genres);
    }

    public boolean isEmpty() {
        return genres.isEmpty();
    }

    public boolean includesAll(List<String> names) {
        return names.stream().allMatch(name -> contains(genres, name));
    }

    private static List<String> usable(List<String> candidates) {
        List<String> usable = new ArrayList<>();
        if (candidates != null) {
            for (String candidate : candidates) {
                if (fits(candidate) && !contains(usable, candidate)) {
                    usable.add(candidate);
                }
            }
        }
        return List.copyOf(usable);
    }

    private static boolean fits(String genre) {
        return genre != null && !genre.isBlank() && genre.length() <= SONG_GENRE_MAX_LENGTH;
    }

    private static boolean contains(List<String> genres, String name) {
        return genres.stream().anyMatch(genre -> genre.equalsIgnoreCase(name));
    }
}
