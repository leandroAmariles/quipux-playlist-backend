package com.quipux.playlist.application.port.in;

import com.quipux.playlist.domain.model.GenreCatalog;
import reactor.core.publisher.Mono;

public interface GetGenresPort {

    Mono<GenreCatalog> getGenres();
}
