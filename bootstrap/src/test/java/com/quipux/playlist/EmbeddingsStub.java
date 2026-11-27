package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The embeddings of a simulated gateway: one vector for each text received, always the same for the same text.
 */
final class EmbeddingsStub implements ResponseDefinitionTransformerV2 {

    static final String PATH = "/v1/embeddings";
    private static final String NAME = "embeddings";
    private static final int DIMENSIONS = 768;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** A server configuration that can answer embeddings. */
    static WireMockConfiguration configure(WireMockConfiguration configuration) {
        return configuration.extensions(new EmbeddingsStub());
    }

    static void answersOn(WireMockServer server) {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withTransformers(NAME)));
    }

    static List<LoggedRequest> requestsOf(WireMockServer server) {
        return server.findAll(postRequestedFor(urlEqualTo(PATH)));
    }

    /** The texts of a request of embeddings. */
    static List<String> textsOf(LoggedRequest request) {
        JsonNode input = MAPPER.readTree(request.getBodyAsString()).path("input");
        return input.isArray() ? input.valueStream().map(JsonNode::asString).toList() : List.of(input.asString());
    }

    @Override
    public ResponseDefinition transform(ServeEvent event) {
        JsonNode input = MAPPER.readTree(event.getRequest().getBodyAsString()).path("input");
        int texts = input.isArray() ? input.size() : 1;
        List<Map<String, Object>> data = new ArrayList<>();
        for (int index = 0; index < texts; index++) {
            String text = input.isArray() ? input.get(index).asString() : input.asString();
            data.add(Map.of("object", "embedding", "index", index, "embedding", vectorOf(text)));
        }
        return ResponseDefinitionBuilder.responseDefinition()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(MAPPER.writeValueAsString(Map.of(
                        "object", "list", "model", "recomendador-embeddings", "data", data,
                        "usage", Map.of("prompt_tokens", 1, "total_tokens", 1))))
                .build();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    // Only positive values: every pair is closer than a right angle, as the vectors of a real model are.
    private static float[] vectorOf(String text) {
        Random random = new Random(text.hashCode());
        float[] vector = new float[DIMENSIONS];
        for (int index = 0; index < vector.length; index++) {
            vector[index] = random.nextFloat();
        }
        return vector;
    }
}
