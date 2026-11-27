package com.quipux.playlist.infrastructure.out.recommendation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

class LangfusePromptSourceTest {

    private static final String PROMPT_URL = "/api/public/v2/prompts/recomendaciones-v2?label=production";
    private static final String REMOTE_TEXT =
            "Instrucciones de Langfuse\n\n<lista>{{lista}}</lista>\n<candidatas>{{candidatas}}</candidatas>";
    private static final ActivePrompt LOCAL = new ActivePrompt(
            "v2", new PromptTemplate(new ClassPathResource("prompts/recommendations/v2.txt")));
    private static WireMockServer langfuse;

    @BeforeAll
    static void start() {
        langfuse = new WireMockServer(wireMockConfig().dynamicPort());
        langfuse.start();
    }

    @AfterAll
    static void stop() {
        langfuse.stop();
    }

    @BeforeEach
    void reset() {
        langfuse.resetAll();
    }

    @Test
    void usesTheProductionPromptAndNamesItsVersion() {
        answers(okJson(promptJson(REMOTE_TEXT)));

        StepVerifier.create(source(Duration.ofMinutes(1)).active())
                .assertNext(active -> assertThat(active.version()).isEqualTo("v2-lf3"))
                .verifyComplete();
        langfuse.verify(getRequestedFor(urlEqualTo(PROMPT_URL)));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 404, 500})
    void usesTheFileWhenLangfuseAnswersAnError(int status) {
        answers(aResponse().withStatus(status));

        expectTheFile();
    }

    @Test
    void usesTheFileWhenLangfuseIsSlow() {
        answers(okJson(promptJson(REMOTE_TEXT)).withFixedDelay(2_000));

        expectTheFile();
    }

    @Test
    void usesTheFileWhenTheTextDoesNotFollowTheFormat() {
        answers(okJson(promptJson("Only instructions")));

        expectTheFile();
    }

    @Test
    void usesTheFileWhenTheAnswerIsEmptyOrIncomplete() {
        answers(aResponse().withStatus(200));
        expectTheFile();

        answers(okJson("{\"version\":1}"));
        expectTheFile();
    }

    @Test
    void asksOncePerPeriodWhetherItWentWellOrNot() {
        answers(aResponse().withStatus(500));
        LangfusePromptSource source = source(Duration.ofMinutes(1));

        StepVerifier.create(source.active().then(source.active()))
                .expectNext(LOCAL)
                .verifyComplete();

        assertThat(langfuse.findAll(getRequestedFor(anyUrl()))).hasSize(1);
    }

    @Test
    void asksAgainWhenThePeriodEnds() {
        answers(okJson(promptJson(REMOTE_TEXT)));
        LangfusePromptSource source = source(Duration.ofMillis(50));

        StepVerifier.create(source.active()
                        .delayElement(Duration.ofMillis(200))
                        .then(source.active()))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(langfuse.findAll(getRequestedFor(anyUrl()))).hasSize(2);
    }

    private void expectTheFile() {
        StepVerifier.create(source(Duration.ofMinutes(1)).active())
                .expectNext(LOCAL)
                .verifyComplete();
    }

    private static void answers(ResponseDefinitionBuilder response) {
        langfuse.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo(PROMPT_URL))
                .willReturn(response));
    }

    private static String promptJson(String text) {
        return JsonMapper.builder().build().writeValueAsString(Map.of(
                "type", "text", "name", "recomendaciones-v2", "version", 3, "prompt", text));
    }

    private static LangfusePromptSource source(Duration timeToLive) {
        return new LangfusePromptSource(
                WebClient.create(langfuse.baseUrl()), "recomendaciones-v2", LOCAL, Duration.ofSeconds(1), timeToLive);
    }
}
