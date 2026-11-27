package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.GetGenresPort;
import com.quipux.playlist.application.port.out.GenreCachePort;
import com.quipux.playlist.application.port.out.GenreProviderPort;
import com.quipux.playlist.domain.exception.BusinessException;
import com.quipux.playlist.domain.exception.GenreProviderUnavailableException;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.GenreSource;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

public class GetGenresUseCase implements GetGenresPort {

    private static final Logger LOG = Loggers.getLogger(GetGenresUseCase.class);

    private final GenreProviderPort provider;
    private final GenreProviderPort fallback;
    private final GenreCachePort cache;

    public GetGenresUseCase(GenreProviderPort provider, GenreProviderPort fallback, GenreCachePort cache) {
        this.provider = provider;
        this.fallback = fallback;
        this.cache = cache;
    }

    @Override
    public Mono<GenreCatalog> getGenres() {
        return readFromCache()
                .switchIfEmpty(Mono.defer(this::fetchFromProviderAndCache))
                .onErrorResume(this::fromTheFallback);
    }

    private Mono<GenreCatalog> readFromCache() {
        return Mono.defer(cache::find)
                .map(genres -> new GenreCatalog(GenreSource.SPOTIFY, genres))
                .filter(catalog -> !catalog.isEmpty())
                .doOnNext(catalog -> LOG.debug("Serving Spotify genres from cache"))
                .onErrorResume(this::ignoreCacheFailure);
    }

    private Mono<GenreCatalog> fetchFromProviderAndCache() {
        return Mono.defer(provider::genres)
                .map(genres -> new GenreCatalog(GenreSource.SPOTIFY, genres))
                .filter(catalog -> !catalog.isEmpty())
                .switchIfEmpty(Mono.error(() ->
                        new GenreProviderUnavailableException(ErrorMessageEnum.GENRE_PROVIDER_NO_GENRES)))
                .flatMap(catalog -> writeToCache(catalog.genres()).thenReturn(catalog));
    }

    private Mono<Void> writeToCache(List<String> genres) {
        return Mono.defer(() -> cache.save(genres))
                .onErrorResume(this::ignoreCacheFailure);
    }

    private Mono<GenreCatalog> fromTheFallback(Throwable error) {
        LOG.warn(ErrorMessageEnum.GENRE_LOCAL_FALLBACK.withCode(reasonOf(error)));
        return fallback.genres().map(genres -> new GenreCatalog(GenreSource.LOCAL, genres));
    }

    private <T> Mono<T> ignoreCacheFailure(Throwable error) {
        LOG.warn(ErrorMessageEnum.GENRE_CACHE_FALLBACK.withCode(reasonOf(error)));
        return Mono.empty();
    }

    private static String reasonOf(Throwable error) {
        return error instanceof BusinessException ? error.getMessage() : error.getClass().getSimpleName();
    }
}
