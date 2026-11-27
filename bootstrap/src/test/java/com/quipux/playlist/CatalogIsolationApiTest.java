package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.infrastructure.out.catalog.PgVectorSongCatalogAdapter;
import com.quipux.playlist.infrastructure.out.catalog.SongCatalogAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.TransactionManager;
import reactor.test.StepVerifier;

/**
 * The AI enabled and a catalog database that cannot be reached: the rest of the application does not notice.
 */
class CatalogIsolationApiTest extends AiApiTestSupport {

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void pointToAnUnreachableCatalog(DynamicPropertyRegistry registry) {
        // Port 1 refuses the connection: nothing listens there.
        registry.add("CATALOG_DB_URL", () -> "jdbc:postgresql://127.0.0.1:1/catalog");
        registry.add("CATALOG_DB_PASSWORD", () -> "fake-catalog-password-for-tests");
    }

    @Test
    void startsAndServesThePlaylists() {
        assertThat(context.getBean(SongCatalogAdapter.class)).isInstanceOf(PgVectorSongCatalogAdapter.class);

        StepVerifier.create(create(playlist("without-catalog"))
                        .concatWith(webClient.delete().uri("/lists/without-catalog")
                                .exchangeToMono(response -> response.releaseBody()
                                        .thenReturn(response.statusCode().value()))))
                .expectNext(HttpStatus.CREATED.value(), HttpStatus.NO_CONTENT.value())
                .verifyComplete();
    }

    @Test
    void keepsTheTransactionsInR2dbcAndOffersNoJdbcAccess() {
        assertThat(context.getBeansOfType(TransactionManager.class).values())
                .singleElement()
                .isInstanceOf(R2dbcTransactionManager.class);
        assertThat(context.getBeansOfType(JdbcTemplate.class)).isEmpty();
    }

    @Test
    void leavesTheCatalogOutOfTheHealthOfTheApplication() {
        assertThat(context.containsBean("dbHealthContributor")).isFalse();

        StepVerifier.create(anonymousClient.get().uri("/actuator/health/readiness")
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(response.statusCode().value())))
                .expectNext(HttpStatus.OK.value())
                .verifyComplete();
    }
}
