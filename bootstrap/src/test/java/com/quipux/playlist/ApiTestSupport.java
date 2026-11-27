package com.quipux.playlist;

import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.infrastructure.in.rest.AuthController;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ApiTestSupport {

    @Autowired
    protected DatabaseClient databaseClient;

    @Value("${local.server.port}")
    private int port;

    @Value("${playlist.security.demo-users.user-password}")
    protected String userPassword;

    @Value("${playlist.security.demo-users.admin-password}")
    protected String adminPassword;

    protected WebClient anonymousClient;

    protected WebClient webClient;

    @BeforeEach
    void createClient() {
        anonymousClient = WebClient.create(baseUrl());
        StepVerifier.create(tokenOf(DemoUser.ADMIN.username(), adminPassword))
                .assertNext(token -> webClient = clientWith(token))
                .verifyComplete();
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected WebClient clientWith(String token) {
        return anonymousClient.mutate().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
    }

    protected Mono<String> tokenOf(String username, String password) {
        return anonymousClient.post().uri(AuthController.LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", username, "password", password))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { })
                .map(body -> String.valueOf(body.get("accessToken")));
    }

    protected Mono<Integer> create(Object body) {
        return webClient.post().uri("/lists")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }

    protected static Map<String, Object> playlist(String name, Object... songs) {
        return Map.of("nombre", name, "canciones", java.util.List.of(songs));
    }
}
