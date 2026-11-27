package com.quipux.playlist.infrastructure.in.rest;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.in.GetRecommendationsPort;
import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.RecommendationSet;
import com.quipux.playlist.domain.model.User;
import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.infrastructure.in.rest.config.JacksonStrictTypesConfig;
import com.quipux.playlist.infrastructure.in.rest.config.SecurityConfig;
import com.quipux.playlist.infrastructure.in.rest.mapper.RecommendationDtoMapperImpl;
import com.quipux.playlist.infrastructure.out.security.JwtTokenIssuerAdapter;
import com.quipux.playlist.infrastructure.security.config.JwtConfig;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@WebFluxTest(RecommendationController.class)
@Import({
        JacksonStrictTypesConfig.class, SecurityConfig.class, JwtConfig.class, JwtTokenIssuerAdapter.class,
        RecommendationDtoMapperImpl.class})
class RecommendationControllerTest {

    private static final URI SPACED = URI.create("/lists/Lista%201/recommendations");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenIssuerAdapter tokenIssuer;

    @MockitoBean
    private GetRecommendationsPort getRecommendations;

    @ParameterizedTest
    @EnumSource(DemoUser.class)
    void asksOnBehalfOfTheTokenOwnerAndAnswersWithTheContract(DemoUser demoUser) {
        when(getRecommendations.recommend(demoUser.username(), "Lista 1")).thenReturn(Mono.just(
                RecommendationSet.fromAi("Lista 1", "v1", List.of(
                        new Recommendation("Song 2", "Another artist", "rock", "Same genre")))));

        withToken(demoUser, response -> response
                .expectStatus().isOk()
                .expectBody().json("{\"lista\":\"Lista 1\",\"origen\":\"IA\",\"versionPrompt\":\"v1\","
                        + "\"recomendaciones\":[{\"idCatalogo\":\"\",\"titulo\":\"Song 2\","
                        + "\"artista\":\"Another artist\",\"genero\":\"rock\",\"motivo\":\"Same genre\"}]}",
                        JsonCompareMode.STRICT));
    }

    @Test
    void writesEveryFieldOfAnEmptyFallback() {
        when(getRecommendations.recommend(DemoUser.USER.username(), "Lista 1"))
                .thenReturn(Mono.just(RecommendationSet.fallback("Lista 1", List.of())));

        withToken(DemoUser.USER, response -> response
                .expectStatus().isOk()
                .expectBody().json("{\"lista\":\"Lista 1\",\"origen\":\"RESPALDO\",\"versionPrompt\":\"\","
                        + "\"recomendaciones\":[]}", JsonCompareMode.STRICT));
    }

    @Test
    void answers404ForAnUnknownPlaylist() {
        when(getRecommendations.recommend(DemoUser.USER.username(), "Lista 1"))
                .thenReturn(Mono.error(new PlaylistNotFoundException()));

        withToken(DemoUser.USER, response -> response
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("PLAYLIST_GE_00002"));
    }

    @Test
    void answers401WithoutAToken() {
        webTestClient.get().uri(SPACED).exchange().expectStatus().isUnauthorized();

        verifyNoInteractions(getRecommendations);
    }

    private void withToken(DemoUser demoUser, java.util.function.Consumer<WebTestClient.ResponseSpec> assertions) {
        StepVerifier.create(tokenIssuer.issue(new User(demoUser.username(), "unused-hash", demoUser.role())))
                .assertNext(token -> assertions.accept(webTestClient.get().uri(SPACED)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.value())
                        .exchange()))
                .verifyComplete();
    }
}
