package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The evaluation process as it is run, with the real {@code application.yml}, a real catalog database and a simulated
 * gateway.
 */
@ExtendWith(OutputCaptureExtension.class)
@Testcontainers(disabledWithoutDocker = true)
class RecommendationEvaluationApplicationTest {

    @Container
    private static final PostgreSQLContainer DATABASE = CatalogDatabase.create();

    private static final String KEY = "sk-fake-key-only-for-tests";
    private static final String CASES = "[{\"id\":\"rock\",\"canciones\":[{\"titulo\":\"Mine\","
            + "\"artista\":\"Me\",\"genero\":\"rock\"}],\"esperadas\":[{\"titulo\":\"x\",\"artista\":\"y\"}]}]";
    private static WireMockServer gateway;

    @TempDir
    private Path folder;

    @BeforeAll
    static void start() {
        gateway = new WireMockServer(EmbeddingsStub.configure(wireMockConfig().dynamicPort()));
        gateway.start();
    }

    @AfterAll
    static void stop() {
        gateway.stop();
    }

    @BeforeEach
    void answerEmbeddings() {
        gateway.resetAll();
        EmbeddingsStub.answersOn(gateway);
    }

    @Test
    void writesTheCandidatesAndTheAnswerOfEachCase(CapturedOutput output) throws IOException {
        assertThat(CatalogIndexApplication.run(arguments(CatalogIndexApplication.COMMAND).toArray(String[]::new)))
                .isZero();
        gateway.stubFor(post(urlEqualTo(AiApiTestSupport.COMPLETIONS))
                .willReturn(AiApiTestSupport.streamOf(AiApiTestSupport.suggestions("c2", "c1"))));

        assertThat(evaluate()).isZero();

        JsonNode report = JsonMapper.builder().build().readTree(Files.readAllBytes(folder.resolve("raw.json")));
        assertThat(report.path("versionCatalogo").asString()).matches("[0-9a-f]{12}");
        assertThat(report.path("modeloChat").asString()).isEqualTo("recomendador-chat");
        JsonNode result = report.path("casos").get(0);
        assertThat(result.path("origen").asString()).isEqualTo("IA");
        assertThat(result.path("versionPrompt").asString()).isEqualTo("v2");
        assertThat(result.path("candidatas").size()).isEqualTo(20);
        assertThat(result.path("recomendaciones").get(0).path("idCatalogo").asString())
                .isEqualTo(result.path("candidatas").get(1).path("idCatalogo").asString());
        assertThat(output).doesNotContain(KEY, CatalogDatabase.PASSWORD);
    }

    @Test
    void endsWithAnErrorWhenNoCaseCouldSearch() throws IOException {
        assertThat(evaluate("--CATALOG_DB_URL=jdbc:postgresql://127.0.0.1:1/catalog")).isEqualTo(1);

        assertThat(Files.readString(folder.resolve("raw.json"))).contains("RESPALDO");
    }

    @Test
    void endsWithAnErrorWhenTheCasesCannotBeRead(CapturedOutput output) {
        List<String> arguments = arguments(RecommendationEvaluationApplication.COMMAND);
        arguments.add("--playlist.evaluation.cases=" + folder.resolve("missing.json"));
        arguments.add("--playlist.evaluation.output=" + folder.resolve("raw.json"));

        assertThat(RecommendationEvaluationApplication.run(arguments.toArray(String[]::new))).isEqualTo(1);

        assertThat(output).contains("RECOMMENDATION_ERROR_003", "could not be evaluated");
    }

    private int evaluate(String... more) throws IOException {
        Path cases = Files.writeString(folder.resolve("cases.json"), CASES, StandardCharsets.UTF_8);
        List<String> arguments = arguments(RecommendationEvaluationApplication.COMMAND);
        arguments.add("--playlist.evaluation.cases=" + cases);
        arguments.add("--playlist.evaluation.output=" + folder.resolve("raw.json"));
        for (String argument : more) {
            arguments.removeIf(given -> given.startsWith(argument.substring(0, argument.indexOf('=') + 1)));
            arguments.add(argument);
        }
        return RecommendationEvaluationApplication.run(arguments.toArray(String[]::new));
    }

    // No secret of the API nor a usable H2: the process needs none of them.
    private static List<String> arguments(String command) {
        return new ArrayList<>(List.of(
                command,
                "--spring.ai.model.chat=openai",
                "--spring.ai.model.embedding=openai",
                "--AI_API_KEY=" + KEY,
                "--AI_BASE_URL=" + gateway.baseUrl() + "/v1",
                "--AI_EMBEDDING_TIMEOUT=30s",
                "--CATALOG_DB_URL=" + DATABASE.getJdbcUrl(),
                "--CATALOG_DB_USER=" + DATABASE.getUsername(),
                "--CATALOG_DB_PASSWORD=" + DATABASE.getPassword(),
                "--playlist.security.jwt.secret=",
                "--spring.r2dbc.url=not-a-database"));
    }
}
