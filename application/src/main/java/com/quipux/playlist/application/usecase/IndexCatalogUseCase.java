package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.IndexCatalogPort;
import com.quipux.playlist.application.port.out.CatalogIndexPort;
import com.quipux.playlist.application.port.out.SongCatalogPort;
import com.quipux.playlist.domain.model.CatalogIndexPlan;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.IndexReport;
import com.quipux.playlist.domain.model.SongCatalog;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

public class IndexCatalogUseCase implements IndexCatalogPort {

    private static final Logger LOG = Loggers.getLogger(IndexCatalogUseCase.class);
    private static final String INDEXED = "Catalog indexed: version={}, added={}, kept={}, removed={}";

    private final SongCatalogPort catalog;
    private final CatalogIndexPort index;
    private final int batchSize;

    public IndexCatalogUseCase(SongCatalogPort catalog, CatalogIndexPort index, int batchSize) {
        this.catalog = catalog;
        this.index = index;
        this.batchSize = batchSize;
    }

    @Override
    public Mono<IndexReport> index() {
        return Mono.defer(() -> {
            SongCatalog current = catalog.catalog();
            return index.indexed()
                    .map(indexed -> CatalogIndexPlan.of(current, indexed))
                    .flatMap(this::apply)
                    .doOnNext(report -> LOG.info(
                            INDEXED, current.version(), report.added(), report.kept(), report.removed()));
        });
    }

    // Saved before removing, so the index is never left empty; each batch stays even if a later one fails.
    private Mono<IndexReport> apply(CatalogIndexPlan plan) {
        return Flux.fromIterable(plan.toIndex())
                .buffer(batchSize)
                .concatMap(this::save, 1)
                .then(Mono.defer(() -> plan.toRemove().isEmpty() ? Mono.empty() : index.remove(plan.toRemove())))
                .thenReturn(new IndexReport(plan.toIndex().size(), plan.kept(), plan.toRemove().size()));
    }

    private Mono<Void> save(List<CatalogSong> batch) {
        return index.save(batch);
    }
}
