package com.quipux.playlist.application.port.in;

import com.quipux.playlist.domain.model.IndexReport;
import reactor.core.publisher.Mono;

public interface IndexCatalogPort {

    /** Leaves the index with exactly the current catalog. Running it again changes nothing. */
    Mono<IndexReport> index();
}
