package com.quipux.playlist.infrastructure.in.rest.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DocumentationSchedulingFilterTest {

    private static final String FILE = "documentation/paths.properties";
    private static final String ELASTIC_THREAD = "boundedElastic";

    private final DocumentationSchedulingFilter filter = new DocumentationSchedulingFilter();

    @ParameterizedTest
    @MethodSource("documentationPaths")
    void servesTheDocumentationOnAnElasticThread(String path) {
        StepVerifier.create(threadThatServes(path))
                .assertNext(thread -> assertThat(thread).startsWith(ELASTIC_THREAD))
                .verifyComplete();
    }

    @ParameterizedTest
    @MethodSource("otherPaths")
    void keepsEveryOtherRequestOnItsThread(String path) {
        String current = Thread.currentThread().getName();

        StepVerifier.create(threadThatServes(path))
                .expectNext(current)
                .verifyComplete();
    }

    private Mono<String> threadThatServes(String path) {
        AtomicReference<String> thread = new AtomicReference<>();
        WebFilterChain chain = exchange -> Mono.fromRunnable(() -> thread.set(Thread.currentThread().getName()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());

        return filter.filter(exchange, chain).then(Mono.fromSupplier(thread::get));
    }

    private static Stream<String> documentationPaths() {
        return paths("documentation.");
    }

    private static Stream<String> otherPaths() {
        return paths("other.");
    }

    private static Stream<String> paths(String prefix) {
        try {
            Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource(FILE));
            return properties.stringPropertyNames().stream()
                    .filter(key -> key.startsWith(prefix))
                    .sorted()
                    .map(properties::getProperty);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
