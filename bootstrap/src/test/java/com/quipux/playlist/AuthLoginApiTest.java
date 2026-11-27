package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthLoginApiTest extends ApiTestSupport {

    @ParameterizedTest
    @CsvSource({"user, USER", "admin, ADMIN"})
    void givesEachDemoUserATokenWithItsRole(String username, String role) {
        String password = "user".equals(username) ? userPassword : adminPassword;

        StepVerifier.create(login(username, password))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(200);
                    assertThat(response.getBody()).containsEntry("tokenType", "Bearer").containsEntry("expiresIn", 1800);
                    assertThat(payloadOf(String.valueOf(response.getBody().get("accessToken"))))
                            .contains("\"sub\":\"" + username + "\"", "\"role\":\"" + role + "\"");
                    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
                    assertThat(response.getHeaders().getCacheControl()).contains("no-store");
                })
                .verifyComplete();
    }

    @Test
    void answersTheSameToAnUnknownUserAndToAWrongPassword() {
        StepVerifier.create(Mono.zip(login("admin", "wrong-password"), login("nobody", adminPassword)))
                .assertNext(responses -> {
                    assertThat(responses.getT1().getStatusCode().value()).isEqualTo(401);
                    assertThat(responses.getT2().getStatusCode().value()).isEqualTo(401);
                    assertThat(responses.getT1().getBody()).isEqualTo(responses.getT2().getBody())
                            .containsEntry("code", "AUTH_ERROR_001");
                })
                .verifyComplete();
    }

    @Test
    void answers401ToAPasswordLongerThanBCryptReads() {
        StepVerifier.create(login("admin", "p".repeat(73)))
                .assertNext(response -> assertThat(response.getStatusCode().value()).isEqualTo(401))
                .verifyComplete();
    }

    @Test
    void ignoresAnInvalidTokenSentToTheLogin() {
        Mono<Integer> status = anonymousClient.post().uri("/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer abc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin", "password", adminPassword))
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));

        StepVerifier.create(status)
                .expectNext(200)
                .verifyComplete();
    }

    @Test
    void storesOnlyBCryptHashes() {
        StepVerifier.create(databaseClient.sql("SELECT password_hash FROM app_user")
                        .map(row -> row.get(0, String.class))
                        .all()
                        .collectList())
                .assertNext(hashes -> assertThat(hashes).hasSize(2).allSatisfy(hash -> assertThat(hash)
                        .startsWith("$2")
                        .doesNotContain(userPassword)
                        .doesNotContain(adminPassword)))
                .verifyComplete();
    }

    private Mono<ResponseEntity<Map<String, Object>>> login(String username, String password) {
        return anonymousClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", username, "password", password))
                .exchangeToMono(response -> response.toEntity(
                        new ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    private static String payloadOf(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }
}
