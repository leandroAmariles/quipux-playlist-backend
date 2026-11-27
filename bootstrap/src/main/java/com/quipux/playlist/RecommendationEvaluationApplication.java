package com.quipux.playlist;

import com.quipux.playlist.application.port.in.EvaluateRecommendationsPort;
import com.quipux.playlist.application.port.out.DefaultRecommendationsPort;
import com.quipux.playlist.application.port.out.RecommendationProviderPort;
import com.quipux.playlist.application.port.out.SongCatalogPort;
import com.quipux.playlist.application.usecase.EvaluateRecommendationsUseCase;
import com.quipux.playlist.domain.exception.BusinessException;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.EvaluatedPlaylist;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.constants.RecommendationConstraints;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.infrastructure.out.catalog.config.CatalogConfig;
import com.quipux.playlist.infrastructure.out.recommendation.config.RecommendationConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The process that recommends for the cases of an evaluation and ends. It is the only way to see the candidates of a
 * recommendation: it has no web server. It carries no stereotype, so the scan of the application never picks it up.
 */
@ImportAutoConfiguration({
    OpenAiEmbeddingAutoConfiguration.class,
    OpenAiChatAutoConfiguration.class,
    ToolCallingAutoConfiguration.class
})
@Import({CatalogConfig.class, RecommendationConfig.class})
public class RecommendationEvaluationApplication {

    /** The argument that starts this process instead of the API. */
    public static final String COMMAND = "evaluate-recommendations";

    private static final Logger LOG = LoggerFactory.getLogger(RecommendationEvaluationApplication.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Value("${playlist.catalog.candidates:" + RecommendationConstraints.MAX_CANDIDATES + "}")
    private int maxCandidates;

    @Value("${playlist.ai.timeout:30s}")
    private Duration timeout;

    @Value("${playlist.ai.embedding-model:recomendador-embeddings}")
    private String embeddingModel;

    /**
     * Evaluates the cases of {@code playlist.evaluation.cases} into {@code playlist.evaluation.output}.
     *
     * @return 0 when the search gave candidates to some case, 1 otherwise
     */
    public static int run(String... args) {
        try (ConfigurableApplicationContext context =
                new SpringApplicationBuilder(RecommendationEvaluationApplication.class)
                        .web(WebApplicationType.NONE)
                        .run(args)) {
            return context.getBean(RecommendationEvaluationApplication.class).evaluate(
                    context.getBean(EvaluateRecommendationsPort.class),
                    context.getBean(SongCatalogPort.class),
                    context.getEnvironment().getRequiredProperty("playlist.evaluation.cases"),
                    context.getEnvironment().getRequiredProperty("playlist.evaluation.output"));
        } catch (RuntimeException failure) {
            LOG.error(ErrorMessageEnum.EVALUATION_FAILED.withCode(reasonOf(failure)));
            return 1;
        }
    }

    @Bean
    EvaluateRecommendationsPort evaluateRecommendations(
            RecommendationProviderPort provider,
            SongCatalogPort catalog,
            DefaultRecommendationsPort defaultRecommendations) {
        return new EvaluateRecommendationsUseCase(
                provider, catalog, defaultRecommendations, maxCandidates, timeout);
    }

    // Waiting is right here: this process serves no requests, and the cases go one after the other.
    private int evaluate(
            EvaluateRecommendationsPort evaluation, SongCatalogPort catalog, String cases, String output) {
        List<Map<String, Object>> results = readCases(Path.of(cases)).stream()
                .map(entry -> resultOf(entry.id(), evaluation.evaluate(entry.playlist()).block()))
                .toList();
        write(Path.of(output), Map.of(
                "versionCatalogo", catalog.catalog().version(),
                "modeloEmbeddings", embeddingModel,
                "modeloChat", results.isEmpty() ? "" : results.getFirst().get("modelo"),
                "casos", results));
        boolean searched = results.stream().anyMatch(result -> !((List<?>) result.get("candidatas")).isEmpty());
        return searched ? 0 : 1;
    }

    private static List<Case> readCases(Path file) {
        try {
            return MAPPER.readValue(Files.readAllBytes(file), new TypeReference<List<CaseData>>() { })
                    .stream()
                    .map(CaseData::toCase)
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void write(Path file, Map<String, Object> report) {
        try {
            Files.write(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Map<String, Object> resultOf(String id, EvaluatedPlaylist evaluated) {
        return Map.of(
                "id", id,
                "origen", evaluated.answer().origin().externalValue(),
                "versionPrompt", evaluated.answer().promptVersion(),
                "modelo", evaluated.identity().model(),
                "candidatas", evaluated.candidates().stream().map(RecommendationEvaluationApplication::of).toList(),
                "recomendaciones", evaluated.answer().recommendations().stream()
                        .map(RecommendationEvaluationApplication::of)
                        .toList());
    }

    private static Map<String, String> of(CatalogSong song) {
        return Map.of(
                "idCatalogo", song.id().toString(),
                "titulo", song.title(),
                "artista", song.artist(),
                "genero", song.genre(),
                "anno", song.releaseYear());
    }

    private static Map<String, String> of(Recommendation recommendation) {
        return Map.of(
                "idCatalogo", recommendation.catalogId(),
                "titulo", recommendation.title(),
                "artista", recommendation.artist(),
                "genero", recommendation.genre(),
                "motivo", recommendation.reason() == null ? "" : recommendation.reason());
    }

    // Only our own messages are recorded: the text of any other error is not under our control.
    private static String reasonOf(RuntimeException failure) {
        return failure instanceof BusinessException
                ? failure.getMessage()
                : failure.getClass().getSimpleName();
    }

    private record Case(String id, Playlist playlist) {
    }

    private record CaseData(String id, List<SongData> canciones, List<Object> esperadas) {

        private Case toCase() {
            return new Case(id, new Playlist(id, null, canciones.stream().map(SongData::toSong).toList()));
        }
    }

    private record SongData(String titulo, String artista, String album, String anno, String genero) {

        private Song toSong() {
            return new Song(titulo, artista, album, anno, genero);
        }
    }
}
