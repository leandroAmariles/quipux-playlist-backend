package com.quipux.playlist.infrastructure.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.exception.SongCatalogUnavailableException;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.infrastructure.out.catalog.config.CatalogConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.test.StepVerifier;

@ExtendWith(OutputCaptureExtension.class)
class CatalogConfigTest {

    // Port 1 refuses the connection: nothing listens there.
    private static final String UNREACHABLE = "jdbc:postgresql://127.0.0.1:1/catalog";
    private static final String PASSWORD = "fake-catalog-password";

    // The conversions a Spring Boot application has, for durations such as 5s.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(CatalogConfig.class)
            .withPropertyValues("spring.ai.openai.api-key=fake-key");

    @Test
    void doesNotUseTheDatabaseWhileTheAiIsDisabled(CapturedOutput output) {
        runner.withPropertyValues(
                        "playlist.catalog.jdbc-url=" + UNREACHABLE, "playlist.catalog.password=" + PASSWORD)
                .run(context -> assertUnavailable(
                        context.getBean(SongCatalogAdapter.class), ErrorMessageEnum.CATALOG_DISABLED));
        assertThat(output).doesNotContain("RECOMMENDATION_ERROR_004");
    }

    @Test
    void doesNotUseTheDatabaseWithoutAnApiKeyAndAsksForNoEmbeddings() {
        FakeEmbeddingModel embeddings = new FakeEmbeddingModel();

        runner.withBean(EmbeddingModel.class, () -> embeddings)
                .withPropertyValues("spring.ai.openai.api-key=  ",
                        "playlist.catalog.jdbc-url=" + UNREACHABLE, "playlist.catalog.password=" + PASSWORD)
                .run(context -> assertUnavailable(
                        context.getBean(SongCatalogAdapter.class), ErrorMessageEnum.CATALOG_DISABLED));
        assertThat(embeddings.requested()).isEmpty();
    }

    @Test
    void warnsWhenTheAiIsEnabledWithoutADatabaseAddress(CapturedOutput output) {
        runner.withBean(EmbeddingModel.class, FakeEmbeddingModel::new)
                .withPropertyValues("playlist.catalog.password=" + PASSWORD)
                .run(context -> assertUnavailable(
                        context.getBean(SongCatalogAdapter.class), ErrorMessageEnum.CATALOG_NOT_CONFIGURED));
        assertThat(output).contains("RECOMMENDATION_ERROR_004").doesNotContain(PASSWORD);
    }

    @Test
    void warnsWhenTheAiIsEnabledWithoutADatabasePassword() {
        runner.withBean(EmbeddingModel.class, FakeEmbeddingModel::new)
                .withPropertyValues("playlist.catalog.jdbc-url=" + UNREACHABLE)
                .run(context -> assertUnavailable(
                        context.getBean(SongCatalogAdapter.class), ErrorMessageEnum.CATALOG_NOT_CONFIGURED));
    }

    @Test
    void usesTheDatabaseWithoutWaitingForItWhenEverythingIsConfigured() {
        runner.withBean(EmbeddingModel.class, FakeEmbeddingModel::new)
                .withPropertyValues("playlist.catalog.jdbc-url=" + UNREACHABLE,
                        "playlist.catalog.password=" + PASSWORD, "playlist.catalog.pool.max-size=3",
                        "playlist.catalog.pool.connection-timeout=300ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SongCatalogAdapter.class))
                            .isInstanceOf(PgVectorSongCatalogAdapter.class);
                    HikariDataSource pool = context.getBean("catalogDataSource", HikariDataSource.class);
                    assertThat(pool.getMaximumPoolSize()).isEqualTo(3);
                    assertThat(pool.getMinimumIdle()).isZero();
                    assertThat(pool.getConnectionTimeout()).isEqualTo(300);
                    assertThat(pool.getDataSourceProperties())
                            .containsEntry("connectTimeout", 1)
                            .containsEntry("socketTimeout", 4);
                    StepVerifier.create(context.getBean(SongCatalogAdapter.class).indexed())
                            .expectError(SongCatalogUnavailableException.class)
                            .verify(Duration.ofSeconds(10));
                });
    }

    @Test
    void doesNotOfferTheDatabaseOfTheCatalogToWhoAsksForAnyDatabase() {
        runner.run(context -> assertThat(context.getBeanNamesForType(DataSource.class))
                .containsExactly("catalogDataSource"));
        // Spring does not inject it into a dependency that does not name it.
        runner.withUserConfiguration(NeedsAnyDatabase.class)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class));
    }

    @Configuration
    static class NeedsAnyDatabase {

        @Bean
        Object anyDatabaseUser(DataSource database) {
            return database;
        }
    }

    private static void assertUnavailable(SongCatalogAdapter adapter, ErrorMessageEnum reason) {
        assertThat(adapter).isInstanceOf(UnavailableSongCatalogAdapter.class);
        assertThat(adapter.catalog().songs()).hasSizeGreaterThanOrEqualTo(200);
        StepVerifier.create(adapter.indexed())
                .expectErrorMessage(reason.format())
                .verify(Duration.ofSeconds(5));
    }
}
