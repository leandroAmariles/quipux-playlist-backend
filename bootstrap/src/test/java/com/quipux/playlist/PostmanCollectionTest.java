package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PostmanCollectionTest {

    private static final String FILE = "postman-collection.properties";
    private static final Path FOLDER = Path.of("..", "postman");
    private static final Path COLLECTION = FOLDER.resolve("quipux-playlist.postman_collection.json");
    private static final String BASE_URL = "{{baseUrl}}";
    private static final String SCHEMA = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";
    private static final Pattern EXPECTED_STATUS = Pattern.compile("status\\((\\d{3})\\)|oneOf\\(\\[([\\d, ]+)]\\)");
    private static final Pattern SECRET = Pattern.compile("eyJ[\\w-]{10,}|client_secret|SPOTIFY_CLIENT");

    private final JsonNode collection = JsonMapper.shared().readTree(read(COLLECTION));

    @Test
    void usesTheVersion21Format() {
        assertThat(collection.path("info").path("schema").asString()).isEqualTo(SCHEMA);
        assertThat(collection.path("info").path("description").asString()).contains("userPassword", "30 minutos");
    }

    @Test
    void runsTheRequestsOfTheContractInOrder() {
        assertThat(requests().stream().map(PostmanCollectionTest::summaryOf))
                .containsExactlyElementsOf(TestProperties.values(FILE, "request."));
    }

    @Test
    void coversEveryOperationAndTheMainErrors() {
        List<String[]> published = requests().stream().map(item -> summaryOf(item).split("\\|")).toList();

        assertThat(published.stream().map(parts -> parts[0]))
                .containsAll(TestProperties.values(FILE, "operation."));
        assertThat(published.stream().flatMap(parts -> Arrays.stream(parts[2].split(","))))
                .containsAll(TestProperties.values(FILE, "error."));
    }

    @Test
    void checksTheStatusOfEveryRequest() {
        assertThat(requests()).allSatisfy(item -> assertThat(scriptOf(item)).contains("pm.test("));
    }

    @Test
    void deliversThePasswordsAndTokensEmpty() {
        Map<String, String> published = new LinkedHashMap<>();
        collection.path("variable").valueStream().forEach(variable -> published.put(
                "variable." + variable.path("key").asString(), variable.path("value").asString()));
        Properties file = TestProperties.load(FILE);
        Map<String, String> expected = file.stringPropertyNames().stream()
                .filter(key -> key.startsWith("variable."))
                .collect(Collectors.toMap(key -> key, file::getProperty));

        assertThat(published).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void takesTheCorrectPasswordsOnlyFromTheVariables() {
        assertThat(requests().stream()
                .filter(item -> summaryOf(item).equals("POST /auth/login|-|200"))
                .map(item -> item.path("request").path("body").path("raw").asString()))
                .hasSize(2)
                .allSatisfy(body -> assertThat(body).containsPattern("\"password\": \"\\{\\{\\w+Password}}\""));
    }

    @Test
    void containsNoSecrets() throws IOException {
        try (Stream<Path> files = Files.walk(FOLDER)) {
            assertThat(files.filter(Files::isRegularFile).toList())
                    .isNotEmpty()
                    .allSatisfy(file -> assertThat(SECRET.matcher(read(file)).find()).isFalse());
        }
    }

    private List<JsonNode> requests() {
        List<JsonNode> requests = new ArrayList<>();
        collect(collection.path("item"), requests);
        return requests;
    }

    private static void collect(JsonNode items, List<JsonNode> requests) {
        items.valueStream().forEach(item -> {
            if (item.has("request")) {
                requests.add(item);
            } else {
                collect(item.path("item"), requests);
            }
        });
    }

    private static String summaryOf(JsonNode item) {
        JsonNode request = item.path("request");
        String path = request.path("url").asString()
                .substring(BASE_URL.length())
                .replaceFirst("^/lists/.+$", "/lists/{listName}");
        String token = request.path("auth").path("bearer").path(0).path("value").asString("-")
                .replaceAll("[{}]", "");
        String statuses = EXPECTED_STATUS.matcher(scriptOf(item)).results()
                .map(PostmanCollectionTest::statusesOf)
                .collect(Collectors.joining(","));
        return request.path("method").asString() + " " + path + "|" + token + "|" + statuses;
    }

    private static String statusesOf(MatchResult match) {
        return match.group(1) != null ? match.group(1) : match.group(2).replace(" ", "");
    }

    private static String scriptOf(JsonNode item) {
        return item.path("event").valueStream()
                .filter(event -> "test".equals(event.path("listen").asString()))
                .flatMap(event -> event.path("script").path("exec").valueStream())
                .map(JsonNode::asString)
                .collect(Collectors.joining("\n"));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
