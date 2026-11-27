package com.quipux.playlist.application.port.out;

import java.util.List;
import reactor.core.publisher.Mono;

public interface GenreProviderPort {

    Mono<List<String>> genres();
}
