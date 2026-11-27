package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * The indexing process as the composition runs it, with the real {@code application.yml}.
 */
@ExtendWith(OutputCaptureExtension.class)
@Testcontainers(disabledWithoutDocker = true)
class CatalogIndexApplicationTest {

    @Container
    private static final PostgreSQLContainer DATABASE = CatalogDatabase.create();

    private static final String KEY = "sk-fake-key-only-for-tests";
    private static WireMockServer gateway;
    private static int songs;

    @BeforeAll
    static void start() throws IOException {
        gateway = new WireMockServer(EmbeddingsStub.configure(wireMockConfig().dynamicPort()));
        gateway.start();
        songs = JsonMapper.builder().build()
                .readTree(new ClassPathResource("catalog/songs.json").getContentAsByteArray())
                .size();
    }

    @AfterAll
    static void stop() {
        gateway.stop();
    }

    @BeforeEach
    void emptyTheCatalog() throws SQLException {
        execute("TRUNCATE catalog.song_embedding");
        gateway.resetAll();
        EmbeddingsStub.answersOn(gateway);
    }

    @Test
    void indexesTheCatalogOnceInBatchesAndThenNothing(CapturedOutput output) throws SQLException {
        assertThat(index()).isZero();

        assertThat(rows()).isEqualTo(songs);
        assertThat(EmbeddingsStub.requestsOf(gateway)).hasSize((songs + 49) / 50)
                .allSatisfy(request -> assertThat(EmbeddingsStub.textsOf(request)).hasSizeBetween(1, 50));
        assertThat(output).containsPattern(
                "Catalog indexed: version=[0-9a-f]{12}, added=" + songs + ", kept=0, removed=0");
        gateway.resetRequests();

        assertThat(index()).isZero();

        assertThat(EmbeddingsStub.requestsOf(gateway)).isEmpty();
        assertThat(output).contains("added=0, kept=" + songs + ", removed=0");
        assertThat(output).doesNotContain(KEY, CatalogDatabase.PASSWORD);
    }

    @Test
    void endsWithAnErrorWithoutWritingWhatTheGatewayAnswered(CapturedOutput output) throws SQLException {
        gateway.stubFor(post(urlEqualTo(EmbeddingsStub.PATH))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":{\"message\":\"SECRET-ECHO\"}}")));

        assertThat(index()).isEqualTo(1);

        assertThat(rows()).isZero();
        assertThat(EmbeddingsStub.requestsOf(gateway)).hasSize(1);
        assertThat(output)
                .contains("RECOMMENDATION_ERROR_004", "The AI provider returned no usable embeddings")
                .doesNotContain("SECRET-ECHO", KEY, CatalogDatabase.PASSWORD);
    }

    @Test
    void endsWithAnErrorWhenTheAiIsDisabled(CapturedOutput output) {
        assertThat(index("--spring.ai.model.embedding=none")).isEqualTo(1);

        assertThat(EmbeddingsStub.requestsOf(gateway)).isEmpty();
        assertThat(output).contains("RECOMMENDATION_ERROR_004");
    }

    // Neither the secrets of the API nor a usable H2 are given: the process needs none of them.
    private static int index(String... more) {
        List<String> arguments = new ArrayList<>(List.of(
                CatalogIndexApplication.COMMAND,
                "--spring.ai.model.embedding=openai",
                "--AI_API_KEY=" + KEY,
                "--AI_BASE_URL=" + gateway.baseUrl() + "/v1",
                "--AI_EMBEDDING_TIMEOUT=30s",
                "--CATALOG_DB_URL=" + DATABASE.getJdbcUrl(),
                "--CATALOG_DB_USER=" + DATABASE.getUsername(),
                "--CATALOG_DB_PASSWORD=" + DATABASE.getPassword(),
                "--playlist.security.jwt.secret=",
                "--spring.r2dbc.url=not-a-database"));
        for (String argument : more) {
            arguments.removeIf(given -> given.startsWith(argument.substring(0, argument.indexOf('=') + 1)));
            arguments.add(argument);
        }
        return CatalogIndexApplication.run(arguments.toArray(String[]::new));
    }

    private static int rows() throws SQLException {
        try (Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT count(*) FROM catalog.song_embedding")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
    }
}
