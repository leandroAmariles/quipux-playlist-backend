package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.quipux.playlist.domain.model.enums.GenreSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecommendationRulesTest {

    private static final GenreCatalog ALLOWED = new GenreCatalog(GenreSource.LOCAL, List.of("rock"));

    @Test
    void fallsBackToTheFirstFiveDefaults() {
        DefaultRecommendations defaults = defaults(7);

        assertThat(RecommendationRules.fallbackFor(defaults, List.of()))
                .containsExactlyElementsOf(defaults.recommendations().subList(0, 5));
    }

    @Test
    void leavesOutOfTheFallbackTheSongsOfThePlaylist() {
        List<Song> songs = List.of(song(" TITLE 0 ", "artist"), song("Title 1", "Another artist"));

        assertThat(RecommendationRules.fallbackFor(defaults(6), songs))
                .extracting(Recommendation::title)
                .containsExactly("Title 1", "Title 2", "Title 3", "Title 4", "Title 5");
    }

    @Test
    void mayLeaveTheFallbackEmpty() {
        List<Song> songs = IntStream.range(0, 5).mapToObj(index -> song("Title " + index, "Artist")).toList();

        assertThat(RecommendationRules.fallbackFor(defaults(5), songs)).isEmpty();
    }

    @Test
    void doesNotMatchASongWithoutTitleOrArtist() {
        List<Song> songs = List.of(song("Title 0", null), song(null, "Artist"));

        assertThat(RecommendationRules.fallbackFor(defaults(5), songs)).hasSize(5);
    }

    @Test
    void keepsAValidSuggestionAndFillsTheOptionalFields() {
        List<Recommendation> raw = List.of(new Recommendation("New", "Singer", null, null));

        assertThat(RecommendationRules.validate(raw, List.of()))
                .containsExactly(new Recommendation("New", "Singer", "", ""));
    }

    @Test
    void discardsASuggestionWithoutTitleOrArtist() {
        List<Recommendation> raw = Arrays.asList(
                suggestion(null, "Singer"), suggestion(" ", "Singer"), suggestion("New", null),
                suggestion("New", ""), null, suggestion("Kept", "Singer"));

        assertThat(titlesOf(RecommendationRules.validate(raw, List.of()))).containsExactly("Kept");
    }

    @Test
    void discardsOversizedFieldsButAcceptsTheLimits() {
        List<Recommendation> raw = List.of(
                suggestion("t".repeat(256), "Singer"),
                suggestion("Long artist", "a".repeat(256)),
                new Recommendation("Long genre", "Singer", "g".repeat(101), ""),
                new Recommendation("t".repeat(255), "a".repeat(255), "g".repeat(100), ""));

        assertThat(titlesOf(RecommendationRules.validate(raw, List.of()))).containsExactly("t".repeat(255));
    }

    @Test
    void trimsALongReason() {
        Recommendation raw = new Recommendation("New", "Singer", "rock", "r".repeat(121));

        assertThat(RecommendationRules.validate(List.of(raw), List.of()))
                .singleElement()
                .extracting(Recommendation::reason)
                .isEqualTo("r".repeat(120));
    }

    @Test
    void doesNotSplitACharacterWhenTrimmingTheReason() {
        // The note takes two Java characters: the 120th and the 121st.
        Recommendation raw = new Recommendation("New", "Singer", "rock", "r".repeat(119) + "🎵tail");

        assertThat(RecommendationRules.validate(List.of(raw), List.of()))
                .singleElement()
                .extracting(Recommendation::reason)
                .isEqualTo("r".repeat(119) + "🎵");
    }

    @Test
    void discardsTheSongsOfTheWholePlaylistAndTheRepeatedOnes() {
        List<Song> songs = new ArrayList<>();
        IntStream.range(0, 60).forEach(index -> songs.add(song("Old " + index, "Singer")));
        List<Recommendation> raw = List.of(
                suggestion(" old 59 ", "SINGER"), suggestion("New", "Singer"),
                suggestion("NEW ", "singer"), suggestion("New", "Another"));

        assertThat(RecommendationRules.validate(raw, songs))
                .extracting(Recommendation::title, Recommendation::artist)
                .containsExactly(tuple("New", "Singer"), tuple("New", "Another"));
    }

    @Test
    void keepsTheFirstFiveValidSuggestions() {
        List<Recommendation> raw = IntStream.range(0, 8)
                .mapToObj(index -> suggestion("New " + index, "Singer"))
                .toList();

        assertThat(titlesOf(RecommendationRules.validate(raw, List.of())))
                .containsExactly("New 0", "New 1", "New 2", "New 3", "New 4");
    }

    private static Recommendation suggestion(String title, String artist) {
        return new Recommendation(title, artist, "rock", "Reason");
    }

    private static List<String> titlesOf(List<Recommendation> recommendations) {
        return recommendations.stream().map(Recommendation::title).toList();
    }

    private static Song song(String title, String artist) {
        return new Song(title, artist, null, null, null);
    }

    private static DefaultRecommendations defaults(int count) {
        return new DefaultRecommendations(IntStream.range(0, count)
                .mapToObj(index -> new Recommendation("Title " + index, "Artist", "rock", "Reason"))
                .toList(), ALLOWED);
    }
}
