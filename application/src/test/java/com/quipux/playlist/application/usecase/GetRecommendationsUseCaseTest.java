package com.quipux.playlist.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.exception.RecommendationRateLimitException;
import com.quipux.playlist.domain.model.CachedRecommendations;
import com.quipux.playlist.domain.model.DefaultRecommendations;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.QuotaDecision;
import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.RecommendationCacheKey;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.GenreSource;
import com.quipux.playlist.domain.model.enums.RecommendationOrigin;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
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
    private static final ProviderIdentity IDENTITY = new ProviderIdentity("v1", "model");
    private static final RecommendationCacheKey KEY = RecommendationCacheKey.of(List.of(SONG), IDENTITY);
    private static final Recommendation NEW_SONG = new Recommendation("New", "Singer", "rock", "Reason");
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
    private DefaultRecommendationsPort defaults;

    @Mock
    private RecommendationCachePort cache;

    @Mock
    private RequestQuotaPort quota;

    private GetRecommendationsUseCase useCase;

    @BeforeEach
    void createUseCase() {
        useCase = new GetRecommendationsUseCase(repository, provider, defaults, cache, quota);
        when(defaults.defaults()).thenReturn(Mono.just(DEFAULTS));
        when(provider.identity()).thenReturn(IDENTITY);
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
        verifyNoInteractions(repository, provider, cache, defaults);
    }

    @Test
    void failsOnAnUnknownPlaylistAfterCountingTheRequest() {
        when(repository.findByName(NAME)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.recommend(USER, NAME))
                .expectError(PlaylistNotFoundException.class)
                .verify();
        verify(quota).consume(USER);
        verifyNoInteractions(provider, defaults, cache);
    }

    @Test
    void doesNotHideAFailureOfTheRepository() {
        IllegalStateException failure = new IllegalStateException("database down");
        when(repository.findByName(NAME)).thenReturn(Mono.error(failure));

        StepVerifier.create(useCase.recommend(USER, NAME))
                .expectErrorMatches(error -> error == failure)
                .verify();
        verifyNoInteractions(provider, defaults, cache);
    }

    @Test
    void answersAnEmptyPlaylistWithTheDefaultsWithoutCacheOrProvider() {
        playlistWith();

        StepVerifier.create(useCase.recommend(USER, NAME))
                .assertNext(answer -> {
                    assertThat(answer.playlistName()).isEqualTo(NAME);
                    assertThat(answer.origin()).isEqualTo(RecommendationOrigin.FALLBACK);
                    assertThat(answer.promptVersion()).isEmpty();
                    assertThat(answer.recommendations()).hasSize(5);
                })
                .verifyComplete();
        verify(provider, never()).recommend(any());
        verifyNoInteractions(cache);
    }

    @Test
    void deliversAndSavesTheValidSuggestionsInTheAgreedOrder() {
        playlistWith(SONG);
        when(provider.recommend(List.of(SONG))).thenReturn(Mono.just(List.of(
                new Recommendation("Title 0", "Artist", "rock", "Already there"), NEW_SONG)));

        StepVerifier.create(useCase.recommend(USER, NAME))
                .assertNext(answer -> {
                    assertThat(answer.origin()).isEqualTo(RecommendationOrigin.AI);
                    assertThat(answer.promptVersion()).isEqualTo("v1");
                    assertThat(answer.playlistName()).isEqualTo(NAME);
                    assertThat(answer.recommendations()).containsExactly(NEW_SONG);
                })
                .verifyComplete();
        InOrder order = inOrder(quota, repository, cache, provider);
        order.verify(quota).consume(USER);
        order.verify(repository).findByName(NAME);
        order.verify(cache).find(KEY);
        order.verify(provider).recommend(List.of(SONG));
        order.verify(cache).save(KEY, new CachedRecommendations("v1", List.of(NEW_SONG)));
        verify(defaults, never()).defaults();
    }

    @Test
    void answersFromTheCacheWithTheRequestedName() {
        playlistWith(SONG);
        when(cache.find(KEY)).thenReturn(Mono.just(new CachedRecommendations("v1", List.of(NEW_SONG))));

        StepVerifier.create(useCase.recommend(USER, NAME))
                .assertNext(answer -> {
                    assertThat(answer.origin()).isEqualTo(RecommendationOrigin.AI);
                    assertThat(answer.playlistName()).isEqualTo(NAME);
                    assertThat(answer.recommendations()).containsExactly(NEW_SONG);
                })
                .verifyComplete();
        verify(provider, never()).recommend(any());
        verify(cache, never()).save(any(), any());
    }

    @Test
    void fallsBackWithoutSavingWhenTheProviderIsUnavailable() {
        playlistWith(SONG);
        when(provider.recommend(List.of(SONG))).thenReturn(Mono.error(new RecommendationProviderUnavailableException(
                ErrorMessageEnum.RECOMMENDATION_PROVIDER_DISABLED)));

        expectFallbackWithoutTheSong();
    }

    @Test
    void fallsBackOnAnUnexpectedErrorOfTheProvider() {
        playlistWith(SONG);
        when(provider.recommend(any())).thenThrow(new IllegalStateException("boom"));

        expectFallbackWithoutTheSong();
    }

    @Test
    void fallsBackWithoutSavingWhenNoSuggestionIsValid() {
        playlistWith(SONG);
        when(provider.recommend(List.of(SONG)))
                .thenReturn(Mono.just(List.of(new Recommendation("Title 0", "artist", null, null))));

        expectFallbackWithoutTheSong();
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

    private void playlistWith(Song... songs) {
        when(repository.findByName(NAME)).thenReturn(Mono.just(new Playlist(NAME, null, List.of(songs))));
    }
}
