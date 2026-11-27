package com.quipux.playlist;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ApiTestSupport {

    @Autowired
    protected DatabaseClient databaseClient;

    @Value("${local.server.port}")
    private int port;

    protected WebClient webClient;

    @BeforeEach
    void createClient() {
        webClient = WebClient.create(baseUrl());
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
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
