package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;

class OpenApiContractTest extends ApiTestSupport {

    private static final String FILE = "openapi-operations.properties";
    private static final String CONTRACT_PATH = "/v3/api-docs";
    private static final String PUBLIC = "public";
    private static final String SCHEME = "bearerAuth";

    @Value("${playlist.security.jwt.secret}")
    private String secret;

    private JsonNode contract;

    @BeforeEach
    void fetchContract() {
        StepVerifier.create(contractIn("es-CO"))
                .assertNext(body -> contract = body)
                .verifyComplete();
    }

    @Test
    void publishesExactlyTheOperationsAndResponsesOfTheSpecification() {
        Map<String, Set<String>> published = new LinkedHashMap<>();
        contract.path("paths").properties().forEach(path -> path.getValue().properties()
                .forEach(operation -> published.put(
                        operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey(),
                        Set.copyOf(operation.getValue().path("responses").propertyNames()))));

        Map<String, Set<String>> expected = TestProperties.values(FILE, "operation.").stream()
                .map(line -> line.split("\\|"))
                .collect(Collectors.toMap(parts -> parts[0], parts -> responsesOf(parts[2]).keySet()));

        assertThat(published).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operations")
    void describesWhoCanCallTheOperation(String name, String roles, String responses) {
        JsonNode operation = operation(name);
        String description = operation.path("description").asString();

        assertThat(operation.path("summary").asString()).isNotBlank();
        if (PUBLIC.equals(roles)) {
            assertThat(operation.path("security").size()).isZero();
            assertThat(description).doesNotContain("USER", "ADMIN");
            return;
        }
        assertThat(operation.path("security").path(0).has(SCHEME)).isTrue();
        List.of("USER", "ADMIN").forEach(role -> assertThat(description.contains(role))
                .as("%s mentions %s", name, role)
                .isEqualTo(Arrays.asList(roles.split(",")).contains(role)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operations")
    void describesEveryResponseOfTheOperation(String name, String roles, String responses) {
        JsonNode published = operation(name).path("responses");

        responsesOf(responses).forEach((status, code) -> {
            JsonNode response = published.path(status);
            assertThat(response.path("description").asString()).contains(code);
            if (Integer.parseInt(status) >= 400) {
                assertThat(response.path("content").propertyNames())
                        .containsExactly(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                assertThat(response.path("content").path(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                        .path("schema").path("$ref").asString()).isEqualTo("#/components/schemas/Problem");
            } else if ("204".equals(status)) {
                assertThat(response.has("content")).isFalse();
            } else {
                assertThat(response.path("content").propertyNames())
                        .containsExactly(MediaType.APPLICATION_JSON_VALUE);
                assertThat(response.path("headers").has(HttpHeaders.LOCATION)).isEqualTo("201".equals(status));
            }
        });
    }

    @Test
    void declaresTheBearerScheme() {
        JsonNode scheme = contract.path("components").path("securitySchemes").path(SCHEME);

        assertThat(scheme.path("type").asString()).isEqualTo("http");
        assertThat(scheme.path("scheme").asString()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asString()).isEqualTo("JWT");
    }

    @Test
    void marksTheRequiredFieldsAndTheirMaximumLengths() {
        Map<String, String> published = new LinkedHashMap<>();
        contract.path("components").path("schemas").properties().forEach(schema -> {
            List<String> required = schema.getValue().path("required").valueStream()
                    .map(JsonNode::asString)
                    .toList();
            schema.getValue().path("properties").properties().forEach(field -> published.put(
                    "field." + schema.getKey() + "." + field.getKey(),
                    (required.contains(field.getKey()) ? "required," : "optional,")
                            + (field.getValue().has("maxLength")
                                    ? field.getValue().path("maxLength").asString()
                                    : "-")));
        });

        Properties file = TestProperties.load(FILE);
        Map<String, String> expected = file.stringPropertyNames().stream()
                .filter(key -> key.startsWith("field."))
                .collect(Collectors.toMap(key -> key, file::getProperty));

        assertThat(published).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void describesTheProjectItsVersionAndTheServerThatAnswers() {
        JsonNode info = contract.path("info");

        assertThat(info.path("title").asString()).isNotBlank();
        assertThat(info.path("description").asString()).isNotBlank();
        assertThat(info.path("version").asString()).matches("\\d+\\.\\d+\\.\\d+.*");
        assertThat(contract.path("servers").path(0).path("url").asString()).isEqualTo(baseUrl());
    }

    @Test
    void isTheSameInEveryLanguage() {
        StepVerifier.create(contractIn("fr-FR"))
                .assertNext(other -> assertThat(other).isEqualTo(contract))
                .verifyComplete();
    }

    @Test
    void containsNoSecrets() {
        assertThat(contract.toString()).doesNotContain(userPassword, adminPassword, secret, "eyJ");
    }

    @Test
    void proposesExamplesThatTheApiAccepts() {
        Map<String, Object> body = new LinkedHashMap<>(examplesOf("PlaylistDto"));
        body.put("canciones", List.of(examplesOf("SongDto")));
        String name = String.valueOf(body.get("nombre"));

        StepVerifier.create(create(body).flatMap(status -> webClient.delete().uri("/lists/{name}", name)
                        .exchangeToMono(response -> response.releaseBody().thenReturn(status))))
                .expectNext(201)
                .verifyComplete();
    }

    private Map<String, Object> examplesOf(String schema) {
        Map<String, Object> examples = new LinkedHashMap<>();
        contract.path("components").path("schemas").path(schema).path("properties").properties().stream()
                .filter(field -> field.getValue().has("example"))
                .forEach(field -> examples.put(field.getKey(), field.getValue().path("example").asString()));
        return examples;
    }

    private Mono<JsonNode> contractIn(String language) {
        return anonymousClient.get().uri(CONTRACT_PATH)
                .header(HttpHeaders.ACCEPT_LANGUAGE, language)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    private JsonNode operation(String name) {
        String[] parts = name.split(" ");
        return contract.path("paths").path(parts[1]).path(parts[0].toLowerCase(Locale.ROOT));
    }

    private static Stream<Arguments> operations() {
        return TestProperties.values(FILE, "operation.").stream()
                .map(line -> line.split("\\|"))
                .map(parts -> Arguments.of(parts[0], parts[1], parts[2]));
    }

    private static Map<String, String> responsesOf(String responses) {
        return Arrays.stream(responses.split(","))
                .map(response -> response.split(":"))
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts.length > 1 ? parts[1] : ""));
    }
}
