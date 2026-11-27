package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.exception.InvalidPlaylistException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PlaylistTest {

    private static final Song SONG = new Song("title", "artist", "album", "1999", "rock");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void rejectsMissingOrBlankName(String name) {
        assertThatThrownBy(() -> playlist(name)).isInstanceOf(InvalidPlaylistException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a/b", "/", "a\\b", "a\u0000b", "a\tb", "a\nb", "a\u007Fb", ".", ".."})
    void rejectsNameThatCannotBeUsedInAUri(String name) {
        assertThatThrownBy(() -> playlist(name)).isInstanceOf(InvalidPlaylistException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "%2F", "%2f", "a%2Fb", "%5C", "x%5cy", "%00", "%0A", "%0a", "%0D", "%0d", "a b", "a b"})
    void rejectsNameWithASequenceThatThePathProtectionBlocks(String name) {
        assertThatThrownBy(() -> playlist(name)).isInstanceOf(InvalidPlaylistException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "%25", "%2E", "%2", "2F", "a;b", "100%"})
    void acceptsNameThatOnlyLooksLikeABlockedSequence(String name) {
        assertThat(playlist(name).name()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mis favoritas", " Rock ", "...", "a.b", "ñandú", "a;b+c&d?e#f%g"})
    void keepsAcceptedNameExactlyAsSent(String name) {
        assertThat(playlist(name).name()).isEqualTo(name);
    }

    @Test
    void acceptsNameAndDescriptionAtTheirMaximumLength() {
        Playlist playlist = new Playlist("n".repeat(255), "d".repeat(1000), null);

        assertThat(playlist.name()).hasSize(255);
        assertThat(playlist.description()).hasSize(1000);
    }

    @Test
    void rejectsNameLongerThanTheMaximum() {
        assertThatThrownBy(() -> playlist("n".repeat(256))).isInstanceOf(InvalidPlaylistException.class);
    }

    @Test
    void rejectsDescriptionLongerThanTheMaximum() {
        assertThatThrownBy(() -> new Playlist("Rock", "d".repeat(1001), null))
                .isInstanceOf(InvalidPlaylistException.class);
    }

    @Test
    void acceptsMissingDescriptionAndSongs() {
        Playlist playlist = new Playlist("Rock", null, null);

        assertThat(playlist.description()).isNull();
        assertThat(playlist.songs()).isEmpty();
    }

    @Test
    void rejectsNullSong() {
        List<Song> songs = Arrays.asList(SONG, null);

        assertThatThrownBy(() -> new Playlist("Rock", null, songs)).isInstanceOf(InvalidPlaylistException.class);
    }

    @Test
    void keepsSongOrderAndRepeatedSongs() {
        Song other = new Song("other", null, null, null, null);

        Playlist playlist = new Playlist("Rock", null, List.of(other, SONG, other));

        assertThat(playlist.songs()).containsExactly(other, SONG, other);
    }

    @Test
    void doesNotChangeWhenTheOriginalListChanges() {
        List<Song> songs = new ArrayList<>(List.of(SONG));
        Playlist playlist = new Playlist("Rock", null, songs);

        songs.clear();

        assertThat(playlist.songs()).containsExactly(SONG);
        assertThatThrownBy(() -> playlist.songs().add(SONG)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void declaresOnlyTheGenresThatHaveText() {
        Playlist playlist = new Playlist("Rock", null, List.of(
                genre("rock"), genre(null), genre(""), genre("   "), genre("Salsa"), genre("rock")));

        assertThat(playlist.declaredGenres()).containsExactly("rock", "Salsa", "rock");
    }

    private static Song genre(String genre) {
        return new Song(null, null, null, null, genre);
    }

    private static Playlist playlist(String name) {
        return new Playlist(name, "description", List.of());
    }
}
