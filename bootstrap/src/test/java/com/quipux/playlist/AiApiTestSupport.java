package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

/**
 * The application with the AI enabled against a simulated LiteLLM, using the real {@code application.yml}: only the
 * environment variables that a deployment would set are given.
 */
@Import(InMemorySongCatalog.Registration.class)
abstract class AiApiTestSupport extends ApiTestSupport {

    protected static final String AI_KEY = "sk-fake-key-only-for-tests";
    protected static final String COMPLETIONS = "/v1/chat/completions";
    protected static final WireMockServer LITELLM = startLiteLlm();

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @DynamicPropertySource
    static void pointToTheSimulatedGateway(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.model.chat", () -> "openai");
        registry.add("spring.ai.model.embedding", () -> "openai");
        registry.add("AI_API_KEY", () -> AI_KEY);
        registry.add("AI_BASE_URL", () -> LITELLM.baseUrl() + "/v1");
        registry.add("AI_TIMEOUT", () -> "2s");
        registry.add("spring.r2dbc.url", () -> "r2dbc:h2:mem:///ai-test?options=DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void resetTheGateway() {
        LITELLM.resetAll();
    }

    protected static void modelAnswers(String content) {
        gatewayAnswers(streamOf(content));
    }

    protected static void gatewayAnswers(ResponseDefinitionBuilder response) {
        LITELLM.stubFor(post(urlEqualTo(COMPLETIONS)).willReturn(response));
    }

    /**
     * Registers stubs, as a step of a pipeline, on a thread that may block: building one calls
     * {@code UUID.randomUUID}, which reads /dev/urandom on Linux.
     */
    protected static <T> Mono<T> stubbing(Runnable stubs) {
        return Mono.<T>fromRunnable(stubs).subscribeOn(Schedulers.boundedElastic());
    }

    /** A streamed completion that delivers the content in two chunks. */
    protected static ResponseDefinitionBuilder streamOf(String content) {
        int half = content.length() / 2;
        String body = chunk(content.substring(0, half)) + chunk(content.substring(half)) + "data: [DONE]\n\n";
        return aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .withBody(body);
    }

    /** An answer of the model that chooses these identifiers, such as {@code c1}, in this order. */
    protected static String suggestions(String... identifiers) {
        List<Map<String, String>> entries = Arrays.stream(identifiers)
                .map(identifier -> Map.of("idCatalogo", identifier, "motivo", "Afín"))
                .collect(Collectors.toList());
        return MAPPER.writeValueAsString(Map.of("recomendaciones", entries));
    }

    protected static List<LoggedRequest> gatewayRequests() {
        return LITELLM.findAll(anyRequestedFor(anyUrl()));
    }

    protected static List<LoggedRequest> chatRequests() {
        return LITELLM.findAll(postRequestedFor(urlEqualTo(COMPLETIONS)));
    }

    protected Mono<Map<String, Object>> recommendationsOf(String playlist) {
        return webClient.get().uri("/lists/{name}/recommendations", playlist)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> songsOf(Map<String, Object> answer) {
        return (List<Map<String, Object>>) answer.get("recomendaciones");
    }

    private static String chunk(String content) {
        return "data: " + MAPPER.writeValueAsString(Map.of(
                "id", "chatcmpl-1",
                "object", "chat.completion.chunk",
                "created", 1,
                "model", "recomendador-chat",
                "choices", List.of(Map.of("index", 0, "delta", Map.of("content", content))))) + "\n\n";
    }

    private static WireMockServer startLiteLlm() {
        WireMockServer server = new WireMockServer(EmbeddingsStub.configure(wireMockConfig().dynamicPort()));
        server.start();
        return server;
    }
}
