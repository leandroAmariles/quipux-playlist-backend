package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

/**
 * The prompt of Langfuse and what happens when Langfuse stops answering, with a cache of the prompt short enough to
 * see both in one run.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecommendationLangfuseApiTest extends AiApiTestSupport {

    private static final String SECRET = "sk-lf-fake-secret-only-for-tests";
    private static final String PROMPT_PATH = "/api/public/v2/prompts/recomendaciones-v2";
    private static final WireMockServer LANGFUSE = new WireMockServer(wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void pointToTheSimulatedLangfuse(DynamicPropertyRegistry registry) {
        LANGFUSE.start();
        registry.add("LANGFUSE_HOST", LANGFUSE::baseUrl);
        registry.add("LANGFUSE_PUBLIC_KEY", () -> "pk-lf-test");
        registry.add("LANGFUSE_SECRET_KEY", () -> SECRET);
        registry.add("playlist.ai.langfuse.cache-ttl", () -> "200ms");
        registry.add("spring.r2dbc.url", () -> "r2dbc:h2:mem:///langfuse-test?options=DB_CLOSE_DELAY=-1");
    }

    @BeforeAll
    static void publishThePrompt() {
        LANGFUSE.stubFor(get(urlPathEqualTo(PROMPT_PATH)).willReturn(okJson(JsonMapper.builder().build()
                .writeValueAsString(Map.of("version", 3, "prompt",
                        "Instrucciones de Langfuse\n\n<lista>{{lista}}</lista>\n"
                                + "<candidatas>{{candidatas}}</candidatas>")))));
    }

    @Test
    @Order(1)
    void recommendsWithThePromptOfLangfuseAndTellsItsVersion() {
        modelAnswers(suggestions("c1"));

        StepVerifier.create(create(playlist("lf-up", Map.of("titulo", "One", "artista", "Me")))
                        .then(recommendationsOf("lf-up")))
                .assertNext(body -> assertThat(body)
                        .containsEntry("origen", "IA")
                        .containsEntry("versionPrompt", "v2-lf3"))
                .verifyComplete();

        assertThat(chatRequests()).singleElement().satisfies(request -> assertThat(request.getBodyAsString())
                .contains("Instrucciones de Langfuse", "\"version\":\"v2-lf3\"",
                        "\"trace_name\":\"recomendaciones\""));
    }

    @Test
    @Order(2)
    void recommendsWithThePromptOfTheFileWhenLangfuseIsDown(CapturedOutput output) {
        modelAnswers(suggestions("c1"));
        LANGFUSE.resetAll();
        LANGFUSE.stubFor(get(urlPathEqualTo(PROMPT_PATH)).willReturn(aResponse().withStatus(503)));

        Mono<Map<String, Object>> answer = create(playlist("lf-down", Map.of("titulo", "Two", "artista", "Me")))
                .delayElement(java.time.Duration.ofMillis(400))
                .then(recommendationsOf("lf-down"));

        StepVerifier.create(answer)
                .assertNext(body -> assertThat(body)
                        .containsEntry("origen", "IA")
                        .containsEntry("versionPrompt", "v2"))
                .verifyComplete();

        assertThat(chatRequests()).singleElement().satisfies(request -> assertThat(request.getBodyAsString())
                .contains("Eres un recomendador", "\"version\":\"v2\""));
        assertThat(output).contains("the remote one is not available").doesNotContain(SECRET);
    }
}
