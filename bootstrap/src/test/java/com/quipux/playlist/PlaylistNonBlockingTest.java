package com.quipux.playlist;

import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.infrastructure.in.rest.AuthController;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaylistNonBlockingTest extends ApiTestSupport {

    private WebClient userClient;

    // Startup and one-time class loading read from jar files. The warm-up leaves them out of the measurement:
    // H2, for instance, reads its error messages the first time a name is duplicated.
    @BeforeAll
    void installBlockHoundAfterWarmingUp() {
        createClient();
        StepVerifier.create(tokenOf(DemoUser.USER.username(), userPassword))
                .assertNext(token -> userClient = clientWith(token))
                .verifyComplete();
        StepVerifier.create(everyOperation("warm-up").concatWith(everySecurityOutcome()))
                .expectNextCount(1)
                .thenConsumeWhile(status -> true)
                .verifyComplete();
        BlockHound.install();
    }

    @Test
    void detectsABlockingCallOnANonBlockingThread() {
        Mono<Void> blocking = Mono.<Void>fromRunnable(PlaylistNonBlockingTest::sleep)
                .subscribeOn(Schedulers.parallel());

        StepVerifier.create(blocking)
                .expectError(BlockingOperationError.class)
                .verify();
    }

    @Test
    void servesEveryOperationWithoutBlocking() {
        StepVerifier.create(everyOperation("non-blocking"))
                .expectNext(201, 409, 400, 200, 200, 204, 404, 404)
                .verifyComplete();
    }

    @Test
    void authenticatesAndAuthorizesWithoutBlocking() {
        StepVerifier.create(everySecurityOutcome())
                .expectNext(200, 401, 401, 401, 403)
                .verifyComplete();
    }

    private Flux<Integer> everySecurityOutcome() {
        URI all = URI.create(baseUrl() + "/lists");
        return Flux.concat(
                login(DemoUser.ADMIN.username(), adminPassword),
                login(DemoUser.ADMIN.username(), "wrong-password"),
                login("unknown", "wrong-password"),
                anonymousClient.get().uri(all)
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(response.statusCode().value())),
                userClient.delete().uri(URI.create(baseUrl() + "/lists/any"))
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(response.statusCode().value())));
    }

    private Mono<Integer> login(String username, String password) {
        return anonymousClient.post().uri(AuthController.LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", username, "password", password))
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }

    private Flux<Integer> everyOperation(String name) {
        Map<String, Object> body = playlist(name, Map.of("titulo", "Uno"), Map.of("titulo", "Dos"));
        URI all = URI.create(baseUrl() + "/lists");
        URI one = URI.create(baseUrl() + "/lists/" + name);

        return Flux.concat(
                create(body),
                create(body),
                create(playlist(" ")),
                status(HttpMethod.GET, all),
                status(HttpMethod.GET, one),
                status(HttpMethod.DELETE, one),
                status(HttpMethod.GET, one),
                status(HttpMethod.DELETE, one));
    }

    private Mono<Integer> status(HttpMethod method, URI uri) {
        return webClient.method(method).uri(uri)
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }

    private static void sleep() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
