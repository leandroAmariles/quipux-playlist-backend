package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.exception.InvalidPlaylistException;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SongTest {

    @Test
    void acceptsSongWithoutAnyField() {
        Song song = new Song(null, null, null, null, null);

        assertThat(song.title()).isNull();
        assertThat(song.genre()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("limitedFields")
    void acceptsValueAtTheMaximumLength(String field, int maximum, Function<String, Song> factory) {
        assertThatCode(() -> factory.apply("a".repeat(maximum))).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("limitedFields")
    void rejectsValueLongerThanTheMaximum(String field, int maximum, Function<String, Song> factory) {
        assertThatThrownBy(() -> factory.apply("a".repeat(maximum + 1)))
                .isInstanceOf(InvalidPlaylistException.class)
                .hasMessageContaining(field);
    }

    private static Stream<Arguments> limitedFields() {
        return Stream.of(
                field("title", 255, value -> new Song(value, null, null, null, null)),
                field("artist", 255, value -> new Song(null, value, null, null, null)),
                field("album", 255, value -> new Song(null, null, value, null, null)),
                field("release year", 10, value -> new Song(null, null, null, value, null)),
                field("genre", 100, value -> new Song(null, null, null, null, value)));
    }

    private static Arguments field(String name, int maximum, Function<String, Song> factory) {
        return Arguments.of(name, maximum, factory);
    }
}
