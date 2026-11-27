package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.stream.Stream;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TokenRequiredApiTest extends ApiTestSupport {

    @Value("${playlist.security.jwt.secret}")
    private String secret;

    @Test
    void answers401ToEveryOperationWithoutATokenAndChangesNothing() {
        URI one = uri("/lists/protected");
        Flux<Integer> statuses = Flux.concat(
                create(playlist("protected")),
                status(HttpMethod.GET, uri("/lists"), null),
                status(HttpMethod.GET, one, null),
                anonymousClient.post().uri("/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(playlist("never-created"))
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(response.statusCode().value())),
                status(HttpMethod.DELETE, one, null),
                status(HttpMethod.GET, one, bearer(validToken())),
                status(HttpMethod.GET, uri("/lists/never-created"), bearer(validToken())));

        StepVerifier.create(statuses)
                .expectNext(201, 401, 401, 401, 401, 200, 404)
                .verifyComplete();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedCredentials")
    void answers401ToATokenThatIsNotValid(String description, String authorization) {
        StepVerifier.create(status(HttpMethod.GET, uri("/lists"), resolve(authorization)))
                .expectNext(401)
                .verifyComplete();
    }

    private static Stream<Arguments> rejectedCredentials() {
        return Stream.of(
                Arguments.of("unreadable token", "Bearer abc"),
                Arguments.of("tampered token", "TAMPERED"),
                Arguments.of("signed with another key", "OTHER_KEY"),
                Arguments.of("unsigned token", "UNSIGNED"),
                Arguments.of("expired token", "EXPIRED"),
                Arguments.of("token without expiry", "NO_EXPIRY"),
                Arguments.of("empty bearer", "Bearer "),
                Arguments.of("another scheme", "Basic dXNlcjpwYXNzd29yZA=="));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/lists/unknown", "/unknown"})
    void answers401BeforeLookingForTheResource(String path) {
        StepVerifier.create(Flux.concat(
                        status(HttpMethod.GET, uri(path), null),
                        status(HttpMethod.DELETE, uri(path), null),
                        status(HttpMethod.GET, uri(path), bearer(validToken()))))
                .expectNext(401, 401, 404)
                .verifyComplete();
    }

    @Test
    void doesNotCreateASession() {
        StepVerifier.create(webClient.get().uri("/lists")
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(response.headers().asHttpHeaders())))
                .assertNext(headers -> assertThat(headers.get(HttpHeaders.SET_COOKIE)).isNull())
                .verifyComplete();
    }

    private String resolve(String authorization) {
        Instant inAnHour = Instant.now().plusSeconds(3600);
        return switch (authorization) {
            case "TAMPERED" -> bearer(tamper(validToken()));
            case "OTHER_KEY" -> bearer(sign("another-fake-key-of-at-least-32-bytes", inAnHour));
            case "UNSIGNED" -> bearer(base64("{\"alg\":\"none\"}") + "."
                    + base64("{\"sub\":\"admin\",\"role\":\"ADMIN\",\"exp\":" + inAnHour.getEpochSecond() + "}") + ".");
            case "EXPIRED" -> bearer(sign(secret, Instant.now().minusSeconds(1)));
            case "NO_EXPIRY" -> bearer(sign(secret, null));
            default -> authorization;
        };
    }

    private String validToken() {
        return sign(secret, Instant.now().plusSeconds(3600));
    }

    private static String sign(String key, Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().subject("admin").claim("role", "ADMIN");
        if (expiresAt != null) {
            claims.issuedAt(expiresAt.minusSeconds(60)).expiresAt(expiresAt);
        }
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private static String tamper(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return parts[0] + "." + base64(payload.replace("\"admin\"", "\"intruder\"")) + "." + parts[2];
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private URI uri(String path) {
        return URI.create(baseUrl() + path);
    }

    private Mono<Integer> status(HttpMethod method, URI uri, String authorization) {
        return anonymousClient.method(method).uri(uri)
                .headers(headers -> {
                    if (authorization != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                })
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }
}
