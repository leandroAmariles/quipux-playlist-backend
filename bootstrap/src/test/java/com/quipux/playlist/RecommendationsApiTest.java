package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;
import com.quipux.playlist.infrastructure.security.TokenClaim;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * The endpoint with the default configuration: the AI is disabled.
 */
class RecommendationsApiTest extends ApiTestSupport {

    private static final WireMockServer LITELLM = startGateway();

    @Autowired
    private JwtEncoder encoder;

    @DynamicPropertySource
    static void pointToTheSimulatedGateway(DynamicPropertyRegistry registry) {
        registry.add("AI_BASE_URL", () -> LITELLM.baseUrl() + "/v1");
        registry.add("spring.r2dbc.url", () -> "r2dbc:h2:mem:///ai-disabled-test?options=DB_CLOSE_DELAY=-1");
    }

    @AfterAll
    static void stopTheGateway() {
        LITELLM.stop();
    }

    @Test
    void asksTheGatewayForNeitherEmbeddingsNorChat() {
        StepVerifier.create(create(playlist("rec-silent", Map.of("titulo", "Uno")))
                        .then(recommendations(webClient, "/lists/rec-silent/recommendations")))
                .assertNext(body -> assertThat(body).containsEntry("origen", "RESPALDO"))
                .verifyComplete();

        assertThat(LITELLM.getAllServeEvents()).isEmpty();
    }

    @Test
    void answersBothRolesWithTheDefaultList() {
        Mono<Integer> created = create(playlist("rec-roles", Map.of("titulo", "Uno", "artista", "Alguien")));

        StepVerifier.create(created.then(recommendations(webClient, "/lists/rec-roles/recommendations"))
                        .concatWith(tokenOf(DemoUser.USER.username(), userPassword).flatMap(token ->
                                recommendations(clientWith(token), "/lists/rec-roles/recommendations"))))
                .assertNext(RecommendationsApiTest::assertDefaultAnswer)
                .assertNext(RecommendationsApiTest::assertDefaultAnswer)
                .verifyComplete();
    }

    @Test
    void answersAPlaylistWithoutSongsAndLeavesOutTheSongsAlreadyInIt() {
        Mono<Map<String, Object>> fromAnEmptyList = create(playlist("rec-empty"))
                .then(recommendations(webClient, "/lists/rec-empty/recommendations"));

        Mono<Boolean> repeated = fromAnEmptyList.flatMap(answer -> {
            Map<String, Object> first = songsOf(answer).get(0);
            return create(playlist("rec-known", Map.of(
                            "titulo", first.get("titulo"), "artista", first.get("artista"))))
                    .then(recommendations(webClient, "/lists/rec-known/recommendations"))
                    .map(second -> songsOf(second).stream()
                            .anyMatch(song -> song.get("titulo").equals(first.get("titulo"))));
        });

        StepVerifier.create(repeated).expectNext(false).verifyComplete();
    }

    @Test
    void answers404ForAnUnknownPlaylist() {
        Mono<Map<String, Object>> problem = webClient.get().uri("/lists/rec-unknown/recommendations")
                .exchangeToMono(response -> {
                    assertThat(response.statusCode().value()).isEqualTo(404);
                    return response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { });
                });

        StepVerifier.create(problem)
                .assertNext(body -> assertThat(body)
                        .containsEntry("code", ResponseCodeEnum.PLAYLIST_NOT_FOUND.code()))
                .verifyComplete();
    }

    @Test
    void answers401WithoutAValidToken() {
        StepVerifier.create(status(anonymousClient, HttpMethod.GET, "/lists/rec-any/recommendations")
                        .concatWith(status(clientWith("not-a-token"), HttpMethod.GET,
                                "/lists/rec-any/recommendations")))
                .expectNext(401, 401)
                .verifyComplete();
    }

    @Test
    void answers403ToATokenWithoutAKnownRole() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(DemoUser.USER.username())
                .claim(TokenClaim.ROLE.claimName(), "ROOT")
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        String token = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        StepVerifier.create(status(clientWith(token), HttpMethod.GET, "/lists/rec-any/recommendations"))
                .expectNext(403)
                .verifyComplete();
    }

    @Test
    void answers400ToABlockedPathEvenWithoutAToken() {
        StepVerifier.create(status(anonymousClient, HttpMethod.GET, "/lists/%252F/recommendations"))
                .expectNext(400)
                .verifyComplete();
    }

    @Test
    void treatsHeadLikeGet() {
        StepVerifier.create(create(playlist("rec-head"))
                        .then(status(webClient, HttpMethod.HEAD, "/lists/rec-head/recommendations"))
                        .concatWith(status(webClient, HttpMethod.HEAD, "/lists/rec-unknown/recommendations")))
                .expectNext(200, 404)
                .verifyComplete();
    }

    @Test
    void handlesAPlaylistCalledLikeTheResource() {
        Mono<Map<String, Object>> playlist = create(playlist("recommendations"))
                .then(recommendations(webClient, "/lists/recommendations"));

        StepVerifier.create(playlist.concatWith(
                        recommendations(webClient, "/lists/recommendations/recommendations")))
                .assertNext(body -> assertThat(body).containsKey("canciones"))
                .assertNext(body -> assertThat(body).containsEntry("lista", "recommendations"))
                .verifyComplete();
    }

    @Test
    void answersInLessThanASecondWithoutAModel() {
        StepVerifier.create(create(playlist("rec-fast", Map.of("titulo", "Uno"))))
                .expectNext(201)
                .verifyComplete();

        StepVerifier.create(status(webClient, HttpMethod.GET, "/lists/rec-fast/recommendations"))
                .expectNext(200)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    private static WireMockServer startGateway() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    private static void assertDefaultAnswer(Map<String, Object> body) {
        assertThat(body)
                .containsOnlyKeys("lista", "origen", "versionPrompt", "recomendaciones")
                .containsEntry("lista", "rec-roles")
                .containsEntry("origen", "RESPALDO")
                .containsEntry("versionPrompt", "");
        assertThat(songsOf(body)).hasSize(5).allSatisfy(song -> {
            assertThat(song).containsOnlyKeys("idCatalogo", "titulo", "artista", "genero", "motivo")
                    .containsEntry("idCatalogo", "");
            assertThat(String.valueOf(song.get("titulo"))).isNotBlank();
        });
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> songsOf(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("recomendaciones");
    }

    private static Mono<Map<String, Object>> recommendations(WebClient client, String path) {
        return client.get().uri(path)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    private Mono<Integer> status(WebClient client, HttpMethod method, String path) {
        return client.method(method).uri(URI.create(baseUrl() + path))
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }
}
