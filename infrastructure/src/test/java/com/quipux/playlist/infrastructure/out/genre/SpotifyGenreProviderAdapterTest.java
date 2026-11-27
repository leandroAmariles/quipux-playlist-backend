package com.quipux.playlist.infrastructure.out.genre;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.quipux.playlist.domain.exception.GenreProviderUnavailableException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class SpotifyGenreProviderAdapterTest {

    private static final String TOKEN_PATH = "/api/token";
    private static final String GENRES_PATH = "/v1/recommendations/available-genre-seeds";
    private static final String CLIENT_ID = "fake-client-id";
    private static final String CLIENT_SECRET = "fake-client-secret";
    private static final String TOKEN = "fake-spotify-token";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @RegisterExtension
    private static final WireMockExtension SPOTIFY = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void asksForATokenWithClientCredentialsAndThenForTheGenres() {
        stubToken();
        stubGenres("{\"genres\":[\"rock\",\"pop\"],\"extra\":{\"ignored\":true}}");

        StepVerifier.create(adapter(CLIENT_ID, CLIENT_SECRET).genres())
                .expectNext(List.of("rock", "pop"))
                .verifyComplete();

        SPOTIFY.verify(postRequestedFor(urlEqualTo(TOKEN_PATH))
                .withBasicAuth(new BasicCredentials(CLIENT_ID, CLIENT_SECRET))
                .withRequestBody(equalTo("grant_type=client_credentials")));
        SPOTIFY.verify(getRequestedFor(urlEqualTo(GENRES_PATH))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    void returnsNoGenresWhenTheAnswerHasNone() {
        stubToken();
        stubGenres("{}");

        StepVerifier.create(adapter(CLIENT_ID, CLIENT_SECRET).genres())
                .expectNext(List.of())
                .verifyComplete();
    }

    @ParameterizedTest
    @CsvSource({"'', fake-client-secret", "fake-client-id, ''", "'   ', fake-client-secret", "'', ''"})
    void doesNotCallSpotifyWithoutCompleteCredentials(String clientId, String clientSecret) {
        expectFailure(adapter(clientId, clientSecret), "Spotify credentials are not configured");

        SPOTIFY.verify(0, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 503})
    void failsWhenTheTokenIsRejected(int code) {
        SPOTIFY.stubFor(post(TOKEN_PATH).willReturn(status(code)));

        expectFailure(adapter(CLIENT_ID, CLIENT_SECRET), "Spotify answered " + code + " to the token request");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 429, 500, 503})
    void failsWhenTheGenresRequestIsRejected(int code) {
        stubToken();
        SPOTIFY.stubFor(get(GENRES_PATH).willReturn(status(code)));

        expectFailure(adapter(CLIENT_ID, CLIENT_SECRET), "Spotify answered " + code + " to the genres request");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"genres\":", "<html></html>", "{\"genres\":{}}", ""})
    void failsWhenTheGenresCannotBeRead(String body) {
        stubToken();
        stubGenres(body);

        expectFailure(adapter(CLIENT_ID, CLIENT_SECRET),
                "Spotify answered the genres request with unreadable content");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"access_token\":\"\"}", "{\"access_token\":\"fake-spotify-token\",", ""})
    void failsWhenThereIsNoReadableToken(String body) {
        SPOTIFY.stubFor(post(TOKEN_PATH).willReturn(okJson(body)));

        expectFailure(adapter(CLIENT_ID, CLIENT_SECRET),
                "Spotify answered the token request with unreadable content");
    }

    @Test
    void failsWhenTheWholeExchangeTakesTooLong() {
        SPOTIFY.stubFor(post(TOKEN_PATH).willReturn(
                okJson("{\"access_token\":\"" + TOKEN + "\"}").withFixedDelay(300)));
        SPOTIFY.stubFor(get(GENRES_PATH).willReturn(aResponse().withStatus(200).withFixedDelay(300)));

        expectFailure(adapter(SPOTIFY.baseUrl(), CLIENT_ID, CLIENT_SECRET, Duration.ofMillis(500)),
                "Spotify did not answer in time");
    }

    @Test
    void failsWhenSpotifyCannotBeReached() {
        expectFailure(adapter("http://127.0.0.1:1", CLIENT_ID, CLIENT_SECRET, TIMEOUT),
                "Spotify could not be reached for the token request");
    }

    @Test
    void hidesTheCredentialsFromItsTextForm() {
        assertThat(new SpotifySettings(SPOTIFY.baseUrl(), SPOTIFY.baseUrl(), CLIENT_ID, CLIENT_SECRET, TIMEOUT)
                .toString())
                .doesNotContain(CLIENT_ID)
                .doesNotContain(CLIENT_SECRET);
    }

    private static void expectFailure(SpotifyGenreProviderAdapter adapter, String message) {
        StepVerifier.create(adapter.genres())
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(GenreProviderUnavailableException.class)
                        .hasMessage(message)
                        .hasNoCause())
                .verify();
    }

    private static void stubToken() {
        SPOTIFY.stubFor(post(TOKEN_PATH).willReturn(
                okJson("{\"access_token\":\"" + TOKEN + "\",\"token_type\":\"bearer\",\"expires_in\":3600}")));
    }

    private static void stubGenres(String body) {
        SPOTIFY.stubFor(get(GENRES_PATH).willReturn(okJson(body)));
    }

    private static SpotifyGenreProviderAdapter adapter(String clientId, String clientSecret) {
        return adapter(SPOTIFY.baseUrl(), clientId, clientSecret, TIMEOUT);
    }

    private static SpotifyGenreProviderAdapter adapter(
            String baseUrl, String clientId, String clientSecret, Duration timeout) {
        return new SpotifyGenreProviderAdapter(
                WebClient.create(), new SpotifySettings(baseUrl, baseUrl, clientId, clientSecret, timeout));
    }
}
