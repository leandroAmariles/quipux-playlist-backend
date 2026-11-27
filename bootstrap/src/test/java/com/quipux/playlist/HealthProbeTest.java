package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.infrastructure.in.rest.health.HealthPath;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;

class HealthProbeTest extends ApiTestSupport {

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
    void servesLivenessAndReadinessWithoutAToken(String path) {
        StepVerifier.create(anonymousClient.get().uri(path)
                        .exchangeToMono(response -> {
                            assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                            return response.bodyToMono(JsonNode.class);
                        }))
                .assertNext(body -> {
                    assertThat(body.path("status").asString()).isEqualTo("UP");
                    assertThat(body.size()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void ignoresInvalidBearerTokenOnHealthProbes() {
        StepVerifier.create(anonymousClient.get().uri(HealthPath.LIVENESS.pattern())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(response.statusCode().value())))
                .expectNext(HttpStatus.OK.value())
                .verifyComplete();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("closedActuatorRoutes")
    void rejectsOtherActuatorRoutesWithoutAToken(HttpMethod method, String path) {
        StepVerifier.create(anonymousClient.method(method).uri(path)
                        .exchangeToMono(response -> {
                            assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                            assertThat(response.headers().contentType())
                                    .contains(MediaType.APPLICATION_PROBLEM_JSON);
                            return response.releaseBody().thenReturn(response.statusCode().value());
                        }))
                .expectNext(HttpStatus.UNAUTHORIZED.value())
                .verifyComplete();
    }

    private static Stream<Arguments> closedActuatorRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/actuator/health"),
                Arguments.of(HttpMethod.GET, "/actuator/info"),
                Arguments.of(HttpMethod.GET, "/actuator/env"),
                Arguments.of(HttpMethod.POST, HealthPath.LIVENESS.pattern()));
    }
}
