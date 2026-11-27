package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DocumentationAccessApiTest extends ApiTestSupport {

    private static final String FILE = "documentation-routes.properties";
    private static final Pattern LOCAL_ASSET = Pattern.compile("(?:src|href)=\"\\./([^\"]+)\"");

    @ParameterizedTest(name = "{0}")
    @MethodSource("publicRoutes")
    void servesTheDocumentationWithoutAToken(String path, int expected) {
        StepVerifier.create(status(HttpMethod.GET, path))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void servesEveryFileThatTheInterfaceAsksFor() {
        Mono<List<String>> assets = anonymousClient.get().uri("/swagger-ui/index.html")
                .retrieve()
                .bodyToMono(String.class)
                .map(page -> LOCAL_ASSET.matcher(page).results()
                        .map(match -> "/swagger-ui/" + match.group(1))
                        .toList());

        StepVerifier.create(assets.flatMapMany(Flux::fromIterable)
                        .concatMap(asset -> status(HttpMethod.GET, asset))
                        .collectList())
                .assertNext(statuses -> assertThat(statuses).isNotEmpty().containsOnly(200))
                .verifyComplete();
    }

    @Test
    void pointsTheInterfaceToTheContract() {
        StepVerifier.create(anonymousClient.get().uri("/v3/api-docs/swagger-config")
                        .retrieve()
                        .bodyToMono(String.class))
                .assertNext(config -> assertThat(config).contains("\"url\":\"/v3/api-docs\""))
                .verifyComplete();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("closedRoutes")
    void opensNothingElse(String method, String path) {
        StepVerifier.create(status(HttpMethod.valueOf(method), path))
                .expectNext(401)
                .verifyComplete();
    }

    private Mono<Integer> status(HttpMethod method, String path) {
        return anonymousClient.method(method).uri(path)
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }

    private static Stream<Arguments> publicRoutes() {
        return TestProperties.values(FILE, "public.").stream()
                .map(line -> line.split("\\|"))
                .map(parts -> Arguments.of(parts[0], Integer.parseInt(parts[1])));
    }

    private static Stream<Arguments> closedRoutes() {
        return TestProperties.values(FILE, "closed.").stream()
                .map(line -> line.split(" "))
                .map(parts -> Arguments.of(parts[0], parts[1]));
    }
}
