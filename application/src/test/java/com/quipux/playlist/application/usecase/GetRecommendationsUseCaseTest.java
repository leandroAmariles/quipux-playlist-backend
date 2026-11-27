package com.quipux.playlist.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.out.DefaultRecommendationsPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.application.port.out.RecommendationCachePort;
import com.quipux.playlist.application.port.out.RecommendationProviderPort;
import com.quipux.playlist.application.port.out.RequestQuotaPort;
import com.quipux.playlist.application.port.out.SongCatalogPort;
import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.exception.RecommendationRateLimitException;
import com.quipux.playlist.domain.exception.SongCatalogUnavailableException;
import com.quipux.playlist.domain.model.CachedRecommendations;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.DefaultRecommendations;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.ModelChoice;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.QuotaDecision;
import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.RecommendationCacheKey;
import com.quipux.playlist.domain.model.RecommendationRules;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.GenreSource;
import com.quipux.playlist.domain.model.enums.RecommendationOrigin;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetRecommendationsUseCaseTest {

    private static final String USER = "user";
    private static final String NAME = "Mix";
    private static final Song SONG = new Song("Title 0", "Artist", null, null, "rock");
    private static final ProviderIdentity IDENTITY = new ProviderIdentity("v2", "model");
    private static final int CANDIDATES = 3;
    // The first song of the catalog is the one of the playlist.
    private static final CatalogSong IN_PLAYLIST = catalogSong("Title 0", "Artist");
    private static final List<CatalogSong> NEW_ONES = IntStream.range(1, 6)
            .mapToObj(index -> catalogSong("New " + index, "Singer"))
            .toList();
    private static final SongCatalog CATALOG = new SongCatalog(
            Stream.concat(Stream.of(IN_PLAYLIST), NEW_ONES.stream()).toList(), "embeddings");
    private static final List<UUID> NEAREST = CATALOG.songs().stream().map(CatalogSong::id).toList();
    private static final RecommendationCacheKey KEY =
            RecommendationCacheKey.of(List.of(SONG), IDENTITY, CATALOG.version());
    private static final List<CatalogSong> OFFERED = NEW_ONES.subList(0, CANDIDATES);
    private static final Recommendation NEW_SONG =
            new Recommendation(NEW_ONES.get(0).id().toString(), "New 1", "Singer", "rock", "Reason");
    private static final DefaultRecommendations DEFAULTS = new DefaultRecommendations(
            IntStream.range(0, 6)
                    .mapToObj(index -> new Recommendation("Title " + index, "Artist", "rock", "Reason"))
                    .toList(),
            new GenreCatalog(GenreSource.LOCAL, List.of("rock")));

    @Mock
    private PlaylistRepositoryPort repository;

    @Mock
    private RecommendationProviderPort provider;

    @Mock
    private SongCatalogPort catalog;

    @Mock
    private DefaultRecommendationsPort defaults;

    @Mock
    private RecommendationCachePort cache;

    @Mock
    private RequestQuotaPort quota;

    private GetRecommendationsUseCase useCase;

    @BeforeEach
    void createUseCase() {
        useCase = useCaseWithin(Duration.ofSeconds(30));
        when(defaults.defaults()).thenReturn(Mono.just(DEFAULTS));
        when(provider.identity()).thenReturn(IDENTITY);
        when(provider.current()).thenReturn(Mono.just(provider));
        when(catalog.catalog()).thenReturn(CATALOG);
        when(catalog.nearest(any(), anyInt())).thenReturn(Mono.just(NEAREST));
        when(quota.consume(USER)).thenReturn(Mono.just(QuotaDecision.permit()));
        when(cache.find(any())).thenReturn(Mono.empty());
        when(cache.save(any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void rejectsARequestOverTheQuotaBeforeLookingForThePlaylist() {
        when(quota.consume(USER)).thenReturn(Mono.just(QuotaDecision.reject(Duration.ofSeconds(7))));

        StepVerifier.create(useCase.recommend(USER, NAME))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(RecommendationRateLimitException.class,
                                limit -> assertThat(limit.retryAfterSeconds()).isEqualTo(7)))
                .verify();
        verifyNoInteractions(repository, provider, catalog, cache, defaults);
    }

    @Test
    void failsOnAnUnknownPlaylistAfterCountingTheRequest() {
        when(repository.findByName(NAME)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.recommend(USER, NAME))
                .expectError(PlaylistNotFoundException.class)
                .verify();
        verify(quota).consume(USER);
        verifyNoInteractions(provider, catalog, defaults, cache);
    }

    @Test
    void doesNotHideAFailureOfTheRepository() {
        IllegalStateException failure = new IllegalStateException("database down");
        when(repository.findByName(NAME)).thenReturn(Mono.error(failure));

        StepVerifier.create(useCase.recommend(USER, NAME))
                .expectErrorMatches(error -> error == failure)
                .verify();
        verifyNoInteractions(provider, catalog, defaults, cache);
    }

    @Test
    void answersAnEmptyPlaylistWithTheDefaultsWithoutCacheCatalogOrProvider() {
        playlistWith();

        StepVerifier.create(useCase.recommend(USER, NAME))
                .assertNext(answer -> {
                    assertThat(answer.playlistName()).isEqualTo(NAME);
                    assertThat(answer.origin()).isEqualTo(RecommendationOrigin.FALLBACK);
                    assertThat(answer.promptVersion()).isEmpty();
                    assertThat(answer.recommendations()).hasSize(5);
                })
                .verifyComplete();
        verify(provider, never()).choose(any(), any());
        verifyNoInteractions(cache);
        verify(catalog, never()).nearest(any(), anyInt());
    }

    @Test
    void asksTheModelToChooseAmongTheCandidatesInTheAgreedOrder() {
        playlistWith(SONG);
        when(provider.choose(List.of(SONG), OFFERED)).thenReturn(Mono.just(List.of(
                new ModelChoice(IN_PLAYLIST.id(), "Already there"),
                new ModelChoice(NEW_ONES.get(0).id(), "Reason"))));

        StepVerifier.create(useCase.recommend(USER, NAME))
                .assertNext(answer -> {
                    assertThat(answer.origin()).isEqualTo(RecommendationOrigin.AI);
                    assertThat(answer.promptVersion()).isEqualTo("v2");
                    assertThat(answer.playlistName()).isEqualTo(NAME);
                    assertThat(answer.recommendations()).containsExactly(NEW_SONG);
                })
                .verifyComplete();
        InOrder order = inOrder(quota, repository, cache, catalog, provider);
        order.verify(quota).consume(USER);
        order.verify(repository).findByName(NAME);
        order.verify(cache).find(KEY);
        // One more than the candidates: the catalog holds one song of the playlist.
        order.verify(catalog).nearest(List.of(SONG), CANDIDATES + 1);
        order.verify(provider).choose(List.of(SONG), OFFERED);
        order.verify(cache).save(KEY, new CachedRecommendations("v2", List.of(NEW_SONG)));
        verify(defaults, never()).defaults();
    }

    @Test
    void answersFromTheCacheWithTheRequestedNameWithoutSearching() {
        playlistWith(SONG);
        when(cache.find(KEY)).thenReturn(Mono.just(new CachedRecommendations("v2", List.of(NEW_SONG))));

        StepVerifier.create(useCase.recommend(USER, NAME))
                .assertNext(answer -> {
                    assertThat(answer.origin()).isEqualTo(RecommendationOrigin.AI);
                    assertThat(answer.playlistName()).isEqualTo(NAME);
                    assertThat(answer.recommendations()).containsExactly(NEW_SONG);
                })
                .verifyComplete();
        verify(catalog, never()).nearest(any(), anyInt());
        verify(provider, never()).choose(any(), any());
        verify(cache, never()).save(any(), any());
    }

    @Test
    void doesNotCallTheModelWithoutCandidates() {
        playlistWith(SONG);
        when(catalog.nearest(any(), anyInt())).thenReturn(Mono.just(List.of(IN_PLAYLIST.id())));

        expectFallbackWithoutTheSong();
        verify(provider, never()).choose(any(), any());
    }

    @Test
    void fallsBackWithoutCallingTheModelWhenTheCatalogCannotBeSearched() {
        playlistWith(SONG);
        when(catalog.nearest(any(), anyInt())).thenReturn(Mono.error(
                new SongCatalogUnavailableException(ErrorMessageEnum.CATALOG_UNREACHABLE)));

        expectFallbackWithoutTheSong();
        verify(provider, never()).choose(any(), any());
    }

    @Test
    void offersTheNearestCandidatesWithoutSavingThemWhenTheProviderFails() {
        playlistWith(SONG);
        when(provider.choose(any(), any())).thenReturn(Mono.error(new RecommendationProviderUnavailableException(
                ErrorMessageEnum.RECOMMENDATION_PROVIDER_CIRCUIT_OPEN)));

        expectTheNearestCandidates(useCase);
    }

    @Test
    void offersTheNearestCandidatesOnAnUnexpectedErrorOfTheProvider() {
        playlistWith(SONG);
        when(provider.choose(any(), any())).thenThrow(new IllegalStateException("boom"));

        expectTheNearestCandidates(useCase);
    }

    @Test
    void offersTheNearestCandidatesWhenNoChoiceIsACandidate() {
        playlistWith(SONG);
        when(provider.choose(any(), any()))
                .thenReturn(Mono.just(List.of(new ModelChoice(UUID.randomUUID(), "Made up"))));

        expectTheNearestCandidates(useCase);
    }

    @Test
    void offersTheNearestCandidatesWhenTheModelTakesTheRestOfTheTime() {
        playlistWith(SONG);
        when(provider.choose(any(), any())).thenReturn(Mono.never());

        expectTheNearestCandidates(useCaseWithin(Duration.ofMillis(200)));
    }

    @Test
    void givesTheModelOnlyTheTimeThatTheSearchLeft() {
        playlistWith(SONG);
        when(catalog.nearest(any(), anyInt()))
                .thenReturn(Mono.delay(Duration.ofMillis(300)).thenReturn(NEAREST));
        when(provider.choose(any(), any())).thenReturn(Mono.delay(Duration.ofSeconds(3))
                .thenReturn(List.of(new ModelChoice(NEW_ONES.get(0).id(), "Reason"))));

        StepVerifier.create(useCaseWithin(Duration.ofSeconds(1)).recommend(USER, NAME))
                .assertNext(answer -> assertThat(answer.origin()).isEqualTo(RecommendationOrigin.SIMILARITY))
                .expectComplete()
                // The whole second of the time limit, never the three the model would take.
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void doesNotCallTheModelWithoutTimeLeft() {
        playlistWith(SONG);
        when(catalog.nearest(any(), anyInt()))
                .thenReturn(Mono.delay(Duration.ofMillis(150)).thenReturn(NEAREST));

        expectTheNearestCandidates(useCaseWithin(Duration.ofMillis(100)));

        verify(provider, never()).choose(any(), any());
    }

    private void expectFallbackWithoutTheSong() {
        StepVerifier.create(useCase.recommend(USER, NAME))
                .assertNext(answer -> {
                    assertThat(answer.origin()).isEqualTo(RecommendationOrigin.FALLBACK);
                    assertThat(answer.recommendations())
                            .extracting(Recommendation::title)
                            .containsExactly("Title 1", "Title 2", "Title 3", "Title 4", "Title 5");
                })
                .verifyComplete();
        verify(cache, never()).save(any(), any());
    }

    @Test
    void searchesAndAsksAgainWithAnotherVersionOfTheCatalog() {
        playlistWith(SONG);
        when(cache.find(KEY)).thenReturn(Mono.just(new CachedRecommendations("v2", List.of(NEW_SONG))));
        when(catalog.catalog()).thenReturn(new SongCatalog(CATALOG.songs(), "other-embeddings"));
        when(provider.choose(any(), any()))
                .thenReturn(Mono.just(List.of(new ModelChoice(NEW_ONES.get(0).id(), "Reason"))));

        StepVerifier.create(useCase.recommend(USER, NAME))
                .assertNext(answer -> assertThat(answer.origin()).isEqualTo(RecommendationOrigin.AI))
                .verifyComplete();
        verify(catalog).nearest(any(), anyInt());
        verify(provider).choose(any(), any());
    }

    private void expectTheNearestCandidates(GetRecommendationsUseCase tested) {
        StepVerifier.create(tested.recommend(USER, NAME))
                .assertNext(answer -> {
                    assertThat(answer.origin()).isEqualTo(RecommendationOrigin.SIMILARITY);
                    assertThat(answer.promptVersion()).isEmpty();
                    assertThat(answer.recommendations()).containsExactlyElementsOf(
                            RecommendationRules.similar(OFFERED));
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
        verify(cache, never()).save(any(), any());
        verify(defaults, never()).defaults();
    }

    private GetRecommendationsUseCase useCaseWithin(Duration timeout) {
        return new GetRecommendationsUseCase(
                repository, provider, catalog, defaults, cache, quota, CANDIDATES, timeout);
    }

    private static CatalogSong catalogSong(String title, String artist) {
        return CatalogSong.of(title, artist, "Album", "2000", "rock");
    }

    private void playlistWith(Song... songs) {
        when(repository.findByName(NAME)).thenReturn(Mono.just(new Playlist(NAME, null, List.of(songs))));
    }
}
