package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.model.enums.GenreSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecommendationRulesTest {

    private static final GenreCatalog ALLOWED = new GenreCatalog(GenreSource.LOCAL, List.of("rock"));
    private static final SongCatalog CATALOG = new SongCatalog(
            IntStream.range(0, 30).mapToObj(RecommendationRulesTest::catalogSong).toList(), "embeddings");

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
    void takesAsCandidatesTheNearestSongsThatAreNotInTheWholePlaylist() {
        List<Song> songs = new ArrayList<>();
        IntStream.range(0, 50).forEach(index -> songs.add(song("Old " + index, "Singer")));
        // The 51st song: it is not sent to the model and still cannot be recommended.
        songs.add(song(" CATALOG 1 ", "artist"));

        assertThat(RecommendationRules.candidates(idsOf(3, 1, 0), CATALOG, songs, 20))
                .containsExactly(catalogSong(3), catalogSong(0));
    }

    @Test
    void leavesOutOfTheCandidatesWhatTheCurrentCatalogDoesNotHold() {
        List<UUID> nearest = List.of(UUID.randomUUID(), catalogSong(2).id(), catalogSong(2).id());

        assertThat(RecommendationRules.candidates(nearest, CATALOG, List.of(), 20))
                .containsExactly(catalogSong(2));
    }

    @Test
    void keepsAsManyCandidatesAsAskedForInTheirOrder() {
        List<UUID> nearest = IntStream.range(0, 30).mapToObj(index -> catalogSong(29 - index).id()).toList();

        assertThat(RecommendationRules.candidates(nearest, CATALOG, List.of(), 20))
                .hasSize(20)
                .startsWith(catalogSong(29), catalogSong(28))
                .endsWith(catalogSong(10));
    }

    @Test
    void mayFindNoCandidate() {
        assertThat(RecommendationRules.candidates(List.of(), CATALOG, List.of(), 20)).isEmpty();
    }

    @Test
    void deliversTheChosenSongsWithTheDataOfTheCatalog() {
        CatalogSong chosen = catalogSong(4);

        assertThat(RecommendationRules.fromChoices(
                        List.of(new ModelChoice(chosen.id(), "Afín")), List.of(catalogSong(1), chosen)))
                .containsExactly(new Recommendation(
                        chosen.id().toString(), chosen.title(), chosen.artist(), chosen.genre(), "Afín"));
    }

    @Test
    void discardsTheChoicesThatAreNotCandidatesAndTheRepeatedOnes() {
        List<CatalogSong> candidates = List.of(catalogSong(0), catalogSong(1));
        List<ModelChoice> choices = Arrays.asList(
                choice(catalogSong(5)), new ModelChoice(UUID.randomUUID(), "Made up"), null,
                choice(catalogSong(1)), choice(catalogSong(1)), choice(catalogSong(0)));

        assertThat(titlesOf(RecommendationRules.fromChoices(choices, candidates)))
                .containsExactly("Catalog 1", "Catalog 0");
    }

    @Test
    void keepsTheFirstFiveChoicesInTheOrderOfTheModel() {
        List<CatalogSong> candidates = IntStream.range(0, 8).mapToObj(RecommendationRulesTest::catalogSong).toList();
        List<ModelChoice> choices = IntStream.range(0, 8)
                .mapToObj(index -> choice(catalogSong(7 - index)))
                .toList();

        assertThat(titlesOf(RecommendationRules.fromChoices(choices, candidates)))
                .containsExactly("Catalog 7", "Catalog 6", "Catalog 5", "Catalog 4", "Catalog 3");
    }

    @Test
    void trimsALongReasonAndAcceptsNone() {
        List<CatalogSong> candidates = List.of(catalogSong(0), catalogSong(1));
        // The note takes two Java characters: the 120th and the 121st.
        List<ModelChoice> choices = List.of(
                new ModelChoice(catalogSong(0).id(), "r".repeat(119) + "🎵tail"),
                new ModelChoice(catalogSong(1).id(), null));

        assertThat(RecommendationRules.fromChoices(choices, candidates))
                .extracting(Recommendation::reason)
                .containsExactly("r".repeat(119) + "🎵", "");
    }

    @Test
    void offersTheFiveNearestCandidatesWithAFixedReasonWhenTheModelGivesNothing() {
        List<CatalogSong> candidates = IntStream.range(0, 8).mapToObj(RecommendationRulesTest::catalogSong).toList();

        assertThat(RecommendationRules.similar(candidates))
                .containsExactlyElementsOf(candidates.subList(0, 5).stream()
                        .map(song -> new Recommendation(song.id().toString(), song.title(), song.artist(),
                                song.genre(), "Similar a las canciones de la lista"))
                        .toList());
    }

    @Test
    void offersEveryCandidateWhenThereAreFewerThanFive() {
        assertThat(titlesOf(RecommendationRules.similar(List.of(catalogSong(2), catalogSong(1)))))
                .containsExactly("Catalog 2", "Catalog 1");
    }

    private static ModelChoice choice(CatalogSong song) {
        return new ModelChoice(song.id(), "Reason");
    }

    private static CatalogSong catalogSong(int number) {
        return CatalogSong.of("Catalog " + number, "Artist", "Album", "2000", "rock");
    }

    private static List<UUID> idsOf(int... numbers) {
        return Arrays.stream(numbers).mapToObj(number -> catalogSong(number).id()).toList();
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
