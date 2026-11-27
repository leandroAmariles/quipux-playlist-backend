package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import reactor.test.StepVerifier;

class GenresWithoutCredentialsApiTest extends ApiTestSupport {

    @Test
    void answersWithTheLocalList() {
        StepVerifier.create(webClient.get().uri("/genres")
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { }))
                .assertNext(body -> assertThat(body).containsEntry("origen", "LOCAL").containsKey("generos"))
                .verifyComplete();
    }
}
