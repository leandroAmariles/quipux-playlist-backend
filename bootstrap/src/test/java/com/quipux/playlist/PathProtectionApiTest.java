package com.quipux.playlist;

import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

class PathProtectionApiTest extends ApiTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {"/lists/%252F", "//lists", "/lists//rock"})
    void answers400ToAPathThatTheProtectionBlocks(String path) {
        StepVerifier.create(webClient.get().uri(URI.create(baseUrl() + path))
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(response.statusCode().value())))
                .expectNext(400)
                .verifyComplete();
    }
}
