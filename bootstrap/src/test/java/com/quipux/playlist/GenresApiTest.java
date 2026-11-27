package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.infrastructure.out.genre.SpotifyEndpoint;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import reactor.test.StepVerifier;

@ExtendWith(OutputCaptureExtension.class)
class GenresApiTest extends SpotifyApiTestSupport {

    @Test
    void returnsTheSpotifyGenresToBothRolesWithoutForwardingTheirTokens() {
        spotifyOffers("[\"rock\",\"pop\"]");

        StepVerifier.create(genres(webClient).concatWith(tokenOf(DemoUser.USER.username(), userPassword)
                        .flatMap(token -> genres(clientWith(token)))))
                .expectNext(
                        Map.of("generos", List.of("rock", "pop"), "origen", "SPOTIFY"),
                        Map.of("generos", List.of("rock", "pop"), "origen", "SPOTIFY"))
                .verifyComplete();

        SPOTIFY.verify(2, getRequestedFor(urlEqualTo(SpotifyEndpoint.GENRE_SEEDS.path()))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + SPOTIFY_TOKEN)));
    }

    @Test
    void doesNotCallSpotifyWithoutAToken() {
        spotifyOffers("[\"rock\"]");

        StepVerifier.create(anonymousClient.get().uri("/genres")
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(response.statusCode().value())))
                .expectNext(401)
                .verifyComplete();

        assertThat(spotifyRequests()).isZero();
    }

    @Test
    void answersWithTheLocalListAndLogsWhyWithoutSecrets(CapturedOutput output) {
        spotifyFails(404);

        StepVerifier.create(genres(webClient))
                .assertNext(body -> {
                    assertThat(body).containsEntry("origen", "LOCAL");
                    assertThat(body.get("generos")).asInstanceOf(
                            org.assertj.core.api.InstanceOfAssertFactories.LIST).contains("rock", "vallenato");
                })
                .verifyComplete();

        assertThat(output)
                .contains("GENRE_ERROR_001", "Spotify answered 404 to the genres request")
                .doesNotContain(SPOTIFY_TOKEN)
                .doesNotContain(CLIENT_SECRET);
    }

    @Test
    void doesNotLogTheTokenOfAnUnreadableAnswer(CapturedOutput output) {
        SPOTIFY.stubFor(post(SpotifyEndpoint.TOKEN.path()).willReturn(
                okJson("{\"access_token\":\"" + SPOTIFY_TOKEN + "\",")));

        StepVerifier.create(genres(webClient))
                .assertNext(body -> assertThat(body).containsEntry("origen", "LOCAL"))
                .verifyComplete();

        assertThat(output)
                .contains("Spotify answered the token request with unreadable content")
                .doesNotContain(SPOTIFY_TOKEN);
    }

    @Test
    void asksSpotifyAgainAfterAFailure() {
        spotifyFails(503);
        StepVerifier.create(genres(webClient))
                .assertNext(body -> assertThat(body).containsEntry("origen", "LOCAL"))
                .verifyComplete();

        spotifyOffers("[\"rock\"]");

        StepVerifier.create(genres(webClient))
                .expectNext(Map.of("generos", List.of("rock"), "origen", "SPOTIFY"))
                .verifyComplete();
    }

    @Test
    void answersInLessThanFourSecondsWhenSpotifyDoesNotAnswer() {
        SPOTIFY.stubFor(post(SpotifyEndpoint.TOKEN.path()).willReturn(okJson("{}").withFixedDelay(10_000)));

        Duration elapsed = StepVerifier.create(genres(webClient))
                .assertNext(body -> assertThat(body).containsEntry("origen", "LOCAL"))
                .verifyComplete();

        assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
    }
}
