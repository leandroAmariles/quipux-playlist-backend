package com.quipux.playlist.infrastructure.out.genre;

import com.quipux.playlist.application.port.out.GenreProviderPort;
import com.quipux.playlist.domain.exception.GenreProviderUnavailableException;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.infrastructure.out.genre.dto.SpotifyGenreSeedsDto;
import com.quipux.playlist.infrastructure.out.genre.dto.SpotifyTokenDto;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

public class SpotifyGenreProviderAdapter implements GenreProviderPort {

    private static final Logger LOG = LoggerFactory.getLogger(SpotifyGenreProviderAdapter.class);
    private static final String GRANT_TYPE_FIELD = "grant_type";
    private static final String GRANT_TYPE = "client_credentials";

    private final WebClient webClient;
    private final SpotifySettings settings;

    public SpotifyGenreProviderAdapter(WebClient webClient, SpotifySettings settings) {
        this.webClient = webClient;
        this.settings = settings;
        if (!settings.hasCredentials()) {
            LOG.warn(ErrorMessageEnum.GENRE_PROVIDER_NOT_CONFIGURED.withCode());
        }
    }

    @Override
    public Mono<List<String>> genres() {
        if (!settings.hasCredentials()) {
            return Mono.error(() ->
                    new GenreProviderUnavailableException(ErrorMessageEnum.GENRE_PROVIDER_NOT_CONFIGURED));
        }
        return requestToken()
                .flatMap(this::requestGenres)
                .timeout(settings.timeout())
                .onErrorMap(TimeoutException.class, error ->
                        new GenreProviderUnavailableException(ErrorMessageEnum.GENRE_PROVIDER_TIMEOUT));
    }

    private Mono<String> requestToken() {
        Mono<String> token = webClient.post()
                .uri(settings.accountsBaseUrl() + SpotifyEndpoint.TOKEN.path())
                .headers(headers -> headers.setBasicAuth(settings.clientId(), settings.clientSecret()))
                .body(BodyInserters.fromFormData(GRANT_TYPE_FIELD, GRANT_TYPE))
                .retrieve()
                .bodyToMono(SpotifyTokenDto.class)
                .mapNotNull(SpotifyTokenDto::accessToken)
                .filter(value -> !value.isBlank());
        return translatingErrors(token, SpotifyEndpoint.TOKEN);
    }

    private Mono<List<String>> requestGenres(String token) {
        Mono<List<String>> genres = webClient.get()
                .uri(settings.apiBaseUrl() + SpotifyEndpoint.GENRE_SEEDS.path())
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .bodyToMono(SpotifyGenreSeedsDto.class)
                .map(body -> body.genres() == null ? List.<String>of() : body.genres());
        return translatingErrors(genres, SpotifyEndpoint.GENRE_SEEDS);
    }

    private static <T> Mono<T> translatingErrors(Mono<T> response, SpotifyEndpoint endpoint) {
        return response
                .switchIfEmpty(Mono.error(() -> unreadable(endpoint)))
                .onErrorMap(
                        error -> !(error instanceof GenreProviderUnavailableException),
                        error -> translate(error, endpoint));
    }

    private static GenreProviderUnavailableException translate(Throwable error, SpotifyEndpoint endpoint) {
        if (error instanceof WebClientResponseException response && response.getStatusCode().isError()) {
            return new GenreProviderUnavailableException(ErrorMessageEnum.GENRE_PROVIDER_ERROR_RESPONSE,
                    response.getStatusCode().value(), endpoint.requestName());
        }
        if (error instanceof WebClientRequestException) {
            return new GenreProviderUnavailableException(
                    ErrorMessageEnum.GENRE_PROVIDER_UNREACHABLE, endpoint.requestName());
        }
        return unreadable(endpoint);
    }

    private static GenreProviderUnavailableException unreadable(SpotifyEndpoint endpoint) {
        return new GenreProviderUnavailableException(
                ErrorMessageEnum.GENRE_PROVIDER_UNREADABLE, endpoint.requestName());
    }
}
