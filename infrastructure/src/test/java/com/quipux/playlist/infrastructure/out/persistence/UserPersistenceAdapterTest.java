package com.quipux.playlist.infrastructure.out.persistence;

import com.quipux.playlist.domain.model.User;
import com.quipux.playlist.domain.model.enums.Role;
import com.quipux.playlist.infrastructure.out.persistence.mapper.UserEntityMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

@DataR2dbcTest
@Import({UserPersistenceAdapter.class, UserEntityMapperImpl.class})
class UserPersistenceAdapterTest {

    private static final User USER = new User("user", "first-hash", Role.USER);

    @Autowired
    private UserPersistenceAdapter adapter;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void emptyTable() {
        StepVerifier.create(databaseClient.sql("DELETE FROM app_user").then())
                .verifyComplete();
    }

    @Test
    void findsASavedUserByItsExactUsername() {
        StepVerifier.create(adapter.save(USER).then(adapter.findByUsername("user")))
                .expectNext(USER)
                .verifyComplete();
        StepVerifier.create(adapter.findByUsername("User"))
                .verifyComplete();
    }

    @Test
    void findsNothingForAnUnknownUsername() {
        StepVerifier.create(adapter.findByUsername("nobody"))
                .verifyComplete();
    }

    @Test
    void updatesTheUserThatAlreadyExists() {
        User updated = new User("user", "second-hash", Role.ADMIN);

        StepVerifier.create(adapter.save(USER).then(adapter.save(updated)).then(adapter.findByUsername("user")))
                .expectNext(updated)
                .verifyComplete();
        StepVerifier.create(databaseClient.sql("SELECT COUNT(*) FROM app_user")
                        .map(row -> row.get(0, Long.class))
                        .one())
                .expectNext(1L)
                .verifyComplete();
    }
}
