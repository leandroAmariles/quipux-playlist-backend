package com.quipux.playlist.infrastructure.out.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.test.StepVerifier;

class SimulatedRecommendationProviderAdapterTest {

    private static final ProviderIdentity IDENTITY = new ProviderIdentity("v1", "recomendador-chat");

    @ParameterizedTest
    @EnumSource(value = ErrorMessageEnum.class,
            names = {"RECOMMENDATION_PROVIDER_DISABLED", "RECOMMENDATION_PROVIDER_NOT_CONFIGURED"})
    void saysWhyItCannotAnswer(ErrorMessageEnum reason) {
        SimulatedRecommendationProviderAdapter adapter = new SimulatedRecommendationProviderAdapter(reason, IDENTITY);

        StepVerifier.create(adapter.recommend(List.of(new Song("Title", "Artist", null, null, null))))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(RecommendationProviderUnavailableException.class)
                        .hasMessage(reason.format()))
                .verify();
        assertThat(adapter.identity()).isEqualTo(IDENTITY);
    }
}
