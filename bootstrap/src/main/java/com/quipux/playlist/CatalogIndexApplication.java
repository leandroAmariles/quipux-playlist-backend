package com.quipux.playlist;

import com.quipux.playlist.application.port.in.IndexCatalogPort;
import com.quipux.playlist.application.usecase.IndexCatalogUseCase;
import com.quipux.playlist.domain.exception.BusinessException;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.infrastructure.out.catalog.SongCatalogAdapter;
import com.quipux.playlist.infrastructure.out.catalog.config.CatalogConfig;
import com.quipux.playlist.infrastructure.out.recommendation.SingleAttemptInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * The process that indexes the catalog and ends. It holds only what indexing needs: no web server, no users, no
 * playlists. It carries no stereotype, so the scan of the application never picks it up.
 */
@ImportAutoConfiguration(OpenAiEmbeddingAutoConfiguration.class)
@Import(CatalogConfig.class)
public class CatalogIndexApplication {

    /** The argument that starts this process instead of the API. */
    public static final String COMMAND = "index-catalog";

    private static final Logger LOG = LoggerFactory.getLogger(CatalogIndexApplication.class);

    @Value("${playlist.catalog.index-batch-size:50}")
    private int batchSize;

    /**
     * Indexes the catalog.
     *
     * @return the exit code of the process: 0 when the index holds the current catalog
     */
    public static int run(String... args) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(CatalogIndexApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {
            // Waiting is right here: this process serves no requests and ends with the result.
            context.getBean(IndexCatalogPort.class).index().block();
            return 0;
        } catch (RuntimeException failure) {
            LOG.error(ErrorMessageEnum.CATALOG_INDEXING_FAILED.withCode(reasonOf(failure)));
            return 1;
        }
    }

    @Bean
    IndexCatalogPort indexCatalog(SongCatalogAdapter catalog) {
        return new IndexCatalogUseCase(catalog, catalog, batchSize);
    }

    @Bean
    OpenAiHttpClientBuilderCustomizer singleAttempt() {
        return client -> client.interceptor(new SingleAttemptInterceptor());
    }

    // Only our own messages are recorded: the text of any other error is not under our control.
    private static String reasonOf(RuntimeException failure) {
        return failure instanceof BusinessException
                ? failure.getMessage()
                : failure.getClass().getSimpleName();
    }
}
