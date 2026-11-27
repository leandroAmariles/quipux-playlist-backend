package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * The AI enabled without a key: the application starts, says so and never calls the gateway.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {"spring.ai.model.chat=openai", "AI_API_KEY=   "})
class RecommendationsWithoutKeyApiTest extends ApiTestSupport {

    private static final WireMockServer LITELLM = start();

    @DynamicPropertySource
    static void pointToTheSimulatedGateway(DynamicPropertyRegistry registry) {
        registry.add("AI_BASE_URL", () -> LITELLM.baseUrl() + "/v1");
        registry.add("spring.r2dbc.url", () -> "r2dbc:h2:mem:///no-key-test?options=DB_CLOSE_DELAY=-1");
    }

    @AfterAll
    static void stopTheGateway() {
        LITELLM.stop();
    }

    @Test
    void warnsAndAnswersWithTheDefaultsWithoutCallingTheGateway(CapturedOutput output) {
        Mono<Map<String, Object>> answer = create(playlist("no-key", Map.of("titulo", "Uno")))
                .then(webClient.get().uri("/lists/no-key/recommendations")
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { }));

        StepVerifier.create(answer)
                .assertNext(body -> assertThat(body).containsEntry("origen", "RESPALDO"))
                .verifyComplete();

        assertThat(LITELLM.getAllServeEvents()).isEmpty();
        assertThat(output).contains(
                ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE.code(), "The AI provider has no API key");
    }

    private static WireMockServer start() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }
}
