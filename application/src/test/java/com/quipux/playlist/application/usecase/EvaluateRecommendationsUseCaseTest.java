package com.quipux.playlist.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.application.port.out.RecommendationProviderPort;
import com.quipux.playlist.application.port.out.SongCatalogPort;
import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.DefaultRecommendations;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.ModelChoice;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.GenreSource;
import com.quipux.playlist.domain.model.enums.RecommendationOrigin;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class EvaluateRecommendationsUseCaseTest {

    private static final ProviderIdentity IDENTITY = new ProviderIdentity("v2", "model");
    private static final Playlist PLAYLIST =
            new Playlist("case", null, List.of(new Song("Mine", "Artist", null, null, "rock")));
    private static final List<CatalogSong> SONGS = IntStream.range(0, 4)
            .mapToObj(index -> CatalogSong.of("New " + index, "Singer", "Album", "2000", "rock"))
            .toList();

    @Test
    void tellsTheCandidatesTheModelHad() {
        EvaluateRecommendationsUseCase useCase = useCaseWith(
                Mono.just(List.of(new ModelChoice(SONGS.get(1).id(), "Reason"))));

        StepVerifier.create(useCase.evaluate(PLAYLIST))
                .assertNext(evaluated -> {
                    assertThat(evaluated.answer().origin()).isEqualTo(RecommendationOrigin.AI);
                    assertThat(evaluated.candidates()).containsExactlyElementsOf(SONGS.subList(0, 3));
                    assertThat(evaluated.identity()).isEqualTo(IDENTITY);
                })
                .verifyComplete();
    }

    @Test
    void keepsTheCandidatesWhenTheModelFails() {
        EvaluateRecommendationsUseCase useCase = useCaseWith(Mono.error(
                new RecommendationProviderUnavailableException(ErrorMessageEnum.RECOMMENDATION_PROVIDER_EMPTY)));

        StepVerifier.create(useCase.evaluate(PLAYLIST))
                .assertNext(evaluated -> {
                    assertThat(evaluated.answer().origin()).isEqualTo(RecommendationOrigin.SIMILARITY);
                    assertThat(evaluated.candidates()).hasSize(3);
                })
                .verifyComplete();
    }

    private static EvaluateRecommendationsUseCase useCaseWith(Mono<List<ModelChoice>> choice) {
        SongCatalog catalog = new SongCatalog(SONGS, "embeddings");
        SongCatalogPort search = new SongCatalogPort() {
            @Override
            public SongCatalog catalog() {
                return catalog;
            }

            @Override
            public Mono<List<UUID>> nearest(List<Song> songs, int limit) {
                return Mono.just(SONGS.stream().map(CatalogSong::id).toList());
            }
        };
        RecommendationProviderPort provider = new RecommendationProviderPort() {
            @Override
            public ProviderIdentity identity() {
                return IDENTITY;
            }

            @Override
            public Mono<List<ModelChoice>> choose(List<Song> songs, List<CatalogSong> candidates) {
                return choice;
            }
        };
        DefaultRecommendations defaults = new DefaultRecommendations(
                IntStream.range(0, 6)
                        .mapToObj(index -> new Recommendation("Title " + index, "Artist", "rock", "Reason"))
                        .toList(),
                new GenreCatalog(GenreSource.LOCAL, List.of("rock")));
        return new EvaluateRecommendationsUseCase(
                provider, search, () -> Mono.just(defaults), 3, Duration.ofSeconds(5));
    }
}
