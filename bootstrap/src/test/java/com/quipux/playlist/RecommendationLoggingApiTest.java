package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.model.enums.DemoUser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(OutputCaptureExtension.class)
class RecommendationLoggingApiTest extends AiApiTestSupport {

    private static final String PROMPT_TEXT = "Eres un recomendador";
    private static final String SONG = "Private song title";

    @Test
    void recordsWhatWasServedWithoutThePromptOrAnySecret(CapturedOutput output) {
        modelAnswers(suggestions("Suggested title|Suggested artist"));
        StringBuilder token = new StringBuilder();

        Mono<Map<String, Object>> answer = create(playlist("log-ok", Map.of("titulo", SONG, "artista", "Me")))
                .then(tokenOf(DemoUser.USER.username(), userPassword))
                .doOnNext(token::append)
                .flatMap(value -> clientWith(value).get().uri("/lists/log-ok/recommendations")
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { }));

        StepVerifier.create(answer)
                .assertNext(body -> assertThat(body).containsEntry("origen", "IA"))
                .verifyComplete();

        assertThat(output)
                .contains("origin=IA", "model=recomendador-chat", "promptVersion=v1", "latencyMs=")
                .doesNotContain(AI_KEY, token.toString(), PROMPT_TEXT, SONG, "Suggested title");
    }

    @Test
    void recordsTheReasonOfAFailureWithoutWhatTheGatewayAnswered(CapturedOutput output) {
        gatewayAnswers(aResponse()
                .withStatus(400)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody("{\"error\":{\"message\":\"Bad prompt: " + PROMPT_TEXT + " " + SONG + "\"}}"));

        answersWithTheDefaults("log-error");

        assertThat(output)
                .contains("RECOMMENDATION_ERROR_002", "The AI provider answered 400", "origin=RESPALDO")
                .doesNotContain(AI_KEY, PROMPT_TEXT, SONG);
    }

    @Test
    void doesNotRecordUnreadableContent(CapturedOutput output) {
        modelAnswers("Claro: " + PROMPT_TEXT + " " + SONG);

        answersWithTheDefaults("log-unreadable");

        assertThat(output)
                .contains("The AI provider returned unreadable content")
                .doesNotContain(PROMPT_TEXT, SONG);
    }

    private void answersWithTheDefaults(String name) {
        StepVerifier.create(create(playlist(name, Map.of("titulo", SONG + " " + name, "artista", "Me")))
                        .then(recommendationsOf(name)))
                .assertNext(body -> assertThat(body).containsEntry("origen", "RESPALDO"))
                .verifyComplete();
    }
}
