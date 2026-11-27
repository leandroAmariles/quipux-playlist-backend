package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;
import com.quipux.playlist.infrastructure.security.TokenClaim;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RoleAccessApiTest extends ApiTestSupport {

    @Autowired
    private JwtEncoder encoder;

    private WebClient userClient;

    @BeforeEach
    void createUserClient() {
        StepVerifier.create(tokenOf(DemoUser.USER.username(), userPassword))
                .assertNext(token -> userClient = clientWith(token))
                .verifyComplete();
    }

    @Test
    void letsAUserReadThePlaylists() {
        Flux<Integer> statuses = Flux.concat(
                create(playlist("role-read")),
                status(userClient, HttpMethod.GET, "/lists"),
                status(userClient, HttpMethod.HEAD, "/lists"),
                status(userClient, HttpMethod.GET, "/lists/role-read"),
                status(userClient, HttpMethod.HEAD, "/lists/role-read"),
                status(userClient, HttpMethod.GET, "/lists/role-unknown"));

        StepVerifier.create(statuses)
                .expectNext(201, 200, 200, 200, 200, 404)
                .verifyComplete();
    }

    @Test
    void doesNotLetAUserCreateOrDelete() {
        Flux<Integer> statuses = Flux.concat(
                create(playlist("role-kept", Map.of("titulo", "Uno"))),
                create(userClient, playlist("role-forbidden")),
                status(userClient, HttpMethod.DELETE, "/lists/role-kept"),
                status(userClient, HttpMethod.PUT, "/lists/role-kept"),
                status(webClient, HttpMethod.GET, "/lists/role-forbidden"));

        StepVerifier.create(statuses)
                .expectNext(201, 403, 403, 403, 404)
                .verifyComplete();
        StepVerifier.create(databaseClient
                        .sql("SELECT COUNT(*) FROM song s JOIN playlist p ON p.id = s.playlist_id "
                                + "WHERE p.name = 'role-kept'")
                        .map(row -> row.get(0, Long.class))
                        .one())
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void checksTheRoleBeforeTheResourceAndTheBody() {
        Flux<Integer> statuses = Flux.concat(
                status(userClient, HttpMethod.DELETE, "/lists/role-unknown"),
                create(userClient, playlist(" ")));

        StepVerifier.create(statuses)
                .expectNext(403, 403)
                .verifyComplete();
    }

    @Test
    void answers403AsAProblem() {
        Mono<Map<String, Object>> body = userClient.delete().uri("/lists/role-unknown")
                .exchangeToMono(response -> response.bodyToMono(
                        new ParameterizedTypeReference<Map<String, Object>>() { }));

        StepVerifier.create(body)
                .assertNext(problem -> assertThat(problem)
                        .containsEntry("status", 403)
                        .containsEntry("code", ResponseCodeEnum.ACCESS_DENIED.code()))
                .verifyComplete();
    }

    @ParameterizedTest
    @CsvSource({
            "role-matrix-a, /lists;x=1/role-matrix-a",
            "role-matrix-b, /lists/role-matrix-b;x=1",
            "role-matrix-c, /%6Cists/role-matrix-c",
            "role-matrix-d, /lists/role-matrix-d/"})
    void doesNotLetAUserDeleteThroughAnotherSpellingOfThePath(String name, String path) {
        Flux<Integer> statuses = Flux.concat(
                create(playlist(name)),
                status(userClient, HttpMethod.DELETE, path),
                status(webClient, HttpMethod.GET, "/lists/" + name));

        StepVerifier.create(statuses)
                .expectNext(201, 403, 200)
                .verifyComplete();
    }

    @ParameterizedTest
    @CsvSource({"role-admin;a, /lists/role-admin%3Ba", "role-admin%b, /lists/role-admin%25b"})
    void letsAnAdministratorCreateReadAndDelete(String name, String location) {
        Flux<Integer> statuses = Flux.concat(
                create(playlist(name)),
                status(webClient, HttpMethod.GET, location),
                status(webClient, HttpMethod.DELETE, location));

        StepVerifier.create(statuses)
                .expectNext(201, 200, 204)
                .verifyComplete();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"ROOT", "admin"})
    void answers403ToAValidTokenWithoutAKnownRole(String role) {
        StepVerifier.create(status(clientWith(tokenWithRole(role)), HttpMethod.GET, "/lists"))
                .expectNext(403)
                .verifyComplete();
    }

    private String tokenWithRole(String role) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(DemoUser.USER.username())
                .expiresAt(Instant.now().plusSeconds(60));
        if (role != null) {
            claims.claim(TokenClaim.ROLE.claimName(), role);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static Mono<Integer> create(WebClient client, Object body) {
        return client.post().uri("/lists")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }

    private Mono<Integer> status(WebClient client, HttpMethod method, String path) {
        return client.method(method).uri(URI.create(baseUrl() + path))
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }
}
