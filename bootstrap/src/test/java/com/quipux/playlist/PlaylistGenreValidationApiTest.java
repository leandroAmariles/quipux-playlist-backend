package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.infrastructure.out.genre.SpotifyEndpoint;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PlaylistGenreValidationApiTest extends SpotifyApiTestSupport {

    @Test
    void createsWithGenresOfTheCatalogIgnoringTheCaseAndKeepingThemAsSent() {
        spotifyOffers("[\"rock\",\"pop\"]");

        StepVerifier.create(create(playlist("genre-case", song("rock"), song("Rock"), song("POP"))))
                .expectNext(201)
                .verifyComplete();

        StepVerifier.create(databaseClient
                        .sql("SELECT s.genre FROM song s JOIN playlist p ON p.id = s.playlist_id "
                                + "WHERE p.name = 'genre-case' ORDER BY s.id")
                        .map(row -> row.get(0, String.class))
                        .all())
                .expectNext("rock", "Rock", "POP")
                .verifyComplete();
        SPOTIFY.verify(1, postRequestedFor(urlEqualTo(SpotifyEndpoint.TOKEN.path())));
        SPOTIFY.verify(1, getRequestedFor(urlEqualTo(SpotifyEndpoint.GENRE_SEEDS.path())));
    }

    @Test
    void rejectsAnUnknownGenreWithoutSavingAnything() {
        spotifyOffers("[\"rock\"]");

        StepVerifier.create(webClient.post().uri("/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(playlist("genre-unknown", song("rock"), song("no-existe")))
                        .exchangeToMono(response -> response
                                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { })))
                .assertNext(problem -> assertThat(problem)
                        .containsEntry("status", 400)
                        .containsEntry("code", "PLAYLIST_GE_00001")
                        .containsEntry("detail", "A song genre is not in the genre catalog"))
                .verifyComplete();

        expectNotSaved("genre-unknown");
    }

    @Test
    void doesNotIgnoreTheSpacesAroundAGenre() {
        spotifyOffers("[\"rock\"]");

        StepVerifier.create(create(playlist("genre-spaces", song(" rock "))))
                .expectNext(400)
                .verifyComplete();
    }

    @Test
    void validatesAgainstTheLocalListWhenSpotifyFails() {
        spotifyFails(503);

        StepVerifier.create(create(playlist("genre-local", song("vallenato")))
                        .concatWith(create(playlist("genre-not-local", song("only-in-spotify")))))
                .expectNext(201, 400)
                .verifyComplete();

        expectNotSaved("genre-not-local");
    }

    @Test
    void createsWithinFourSecondsWhenSpotifyDoesNotAnswer() {
        SPOTIFY.stubFor(post(SpotifyEndpoint.TOKEN.path()).willReturn(okJson("{}").withFixedDelay(10_000)));

        Duration elapsed = StepVerifier.create(create(playlist("genre-slow", song("rock"))))
                .expectNext(201)
                .verifyComplete();

        assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
    }

    @Test
    void doesNotAskForTheCatalogWhenNoSongHasAGenre() {
        spotifyOffers("[\"rock\"]");

        StepVerifier.create(create(playlist("genre-none",
                        Map.of("titulo", "Uno"), song(""), song("   "))))
                .expectNext(201)
                .verifyComplete();

        assertThat(spotifyRequests()).isZero();
    }

    @Test
    void checksTheRoleAndTheOtherRulesBeforeTheGenre() {
        spotifyOffers("[\"rock\"]");
        Map<String, Object> body = playlist("genre-order", song("no-existe"));

        StepVerifier.create(send(anonymousClient, body)
                        .concatWith(tokenOf(DemoUser.USER.username(), userPassword)
                                .flatMap(token -> send(clientWith(token), body)))
                        .concatWith(create(playlist("genre-order", song("g".repeat(101))))))
                .expectNext(401, 403, 400)
                .verifyComplete();

        assertThat(spotifyRequests()).isZero();
        expectNotSaved("genre-order");
    }

    @Test
    void reportsTheGenreBeforeTheDuplicatedName() {
        spotifyOffers("[\"rock\"]");

        StepVerifier.create(create(playlist("genre-duplicate", song("rock")))
                        .concatWith(create(playlist("genre-duplicate", song("no-existe"))))
                        .concatWith(create(playlist("genre-duplicate", song("rock")))))
                .expectNext(201, 400, 409)
                .verifyComplete();
    }

    @Test
    void readsAndDeletesWithoutCheckingTheGenresAgain() {
        spotifyOffers("[\"only-today\"]");
        StepVerifier.create(create(playlist("genre-old", song("only-today"))))
                .expectNext(201)
                .verifyComplete();
        SPOTIFY.resetAll();

        StepVerifier.create(status(HttpMethod.GET, "/lists/genre-old")
                        .concatWith(status(HttpMethod.DELETE, "/lists/genre-old")))
                .expectNext(200, 204)
                .verifyComplete();

        assertThat(spotifyRequests()).isZero();
    }

    private static Map<String, Object> song(String genre) {
        return Map.of("genero", genre);
    }

    private static Mono<Integer> send(WebClient client, Object body) {
        return client.post().uri("/lists")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }

    private Mono<Integer> status(HttpMethod method, String path) {
        return webClient.method(method).uri(path)
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }

    private void expectNotSaved(String name) {
        StepVerifier.create(databaseClient
                        .sql("SELECT COUNT(*) FROM playlist WHERE name = '" + name + "'")
                        .map(row -> row.get(0, Long.class))
                        .one())
                .expectNext(0L)
                .verifyComplete();
    }
}
