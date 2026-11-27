package com.quipux.playlist.infrastructure.out.persistence;

import com.quipux.playlist.infrastructure.out.persistence.mapper.PlaylistEntityMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DataR2dbcTest
@Import({PlaylistPersistenceAdapter.class, PlaylistEntityMapperImpl.class})
abstract class PersistenceTestSupport {

    @Autowired
    protected PlaylistPersistenceAdapter adapter;

    @Autowired
    protected DatabaseClient databaseClient;

    @BeforeEach
    void emptyTables() {
        StepVerifier.create(execute("DELETE FROM song").then(execute("DELETE FROM playlist")))
                .verifyComplete();
    }

    protected Mono<Void> execute(String statement) {
        return databaseClient.sql(statement).then();
    }

    protected Flux<String> column(String query) {
        return databaseClient.sql(query).map(row -> String.valueOf(row.get(0))).all();
    }
}
