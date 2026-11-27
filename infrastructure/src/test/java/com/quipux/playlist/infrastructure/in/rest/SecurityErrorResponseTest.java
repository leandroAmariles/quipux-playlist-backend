package com.quipux.playlist.infrastructure.in.rest;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class SecurityErrorResponseTest extends ControllerTestSupport {

    @Test
    void answers401AsAProblemWithoutSayingWhy() {
        expectProblem(webTestClient.get().uri("/lists")
                        .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                        .exchange(),
                HttpStatus.UNAUTHORIZED, "AUTH_ERROR_002")
                .jsonPath("$.detail").isEqualTo("Authentication is required");
    }

    @Test
    void sendsOnlyTheBearerChallenge() {
        webTestClient.get().uri("/lists")
                .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .exchange()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }

    @Test
    void answers400AsAProblemForARejectedPath() {
        expectProblem(webTestClient.get().uri(URI.create("/lists/%252F")).exchange(),
                HttpStatus.BAD_REQUEST, "PLAYLIST_GE_00001");
    }

    private static WebTestClient.BodyContentSpec expectProblem(
            WebTestClient.ResponseSpec response, HttpStatus status, String code) {
        WebTestClient.BodyContentSpec body = response
                .expectStatus().isEqualTo(status)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody();
        body.jsonPath("$.status").isEqualTo(status.value()).jsonPath("$.code").isEqualTo(code);
        return body;
    }
}
