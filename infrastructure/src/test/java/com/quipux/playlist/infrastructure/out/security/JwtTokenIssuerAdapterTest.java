package com.quipux.playlist.infrastructure.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.quipux.playlist.domain.model.AccessToken;
import com.quipux.playlist.domain.model.User;
import com.quipux.playlist.domain.model.enums.Role;
import com.quipux.playlist.infrastructure.security.SigningKey;
import com.quipux.playlist.infrastructure.security.TokenLifetime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;

class JwtTokenIssuerAdapterTest {

    private static final SigningKey KEY = new SigningKey("fake-key-used-only-by-this-test-000");
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00.750Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(KEY.secretKey()));

    @Test
    void issuesASignedTokenWithUsernameRoleAndExpiry() {
        JwtTokenIssuerAdapter issuer =
                new JwtTokenIssuerAdapter(encoder, CLOCK, new TokenLifetime(Duration.ofMinutes(30)));

        Mono<Tuple2<AccessToken, Jwt>> issued = issuer.issue(new User("admin", "hash", Role.ADMIN))
                .zipWhen(token -> decode(token.value()));

        StepVerifier.create(issued)
                .assertNext(result -> {
                    Jwt jwt = result.getT2();
                    assertThat(result.getT1().expiresInSeconds()).isEqualTo(1800);
                    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
                    assertThat(jwt.getSubject()).isEqualTo("admin");
                    assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
                    assertThat(jwt.getIssuedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
                    assertThat(jwt.getExpiresAt()).isEqualTo(Instant.parse("2026-01-01T10:30:00Z"));
                    assertThat(jwt.getClaims()).containsOnlyKeys("sub", "role", "iat", "exp");
                })
                .verifyComplete();
    }

    private static Mono<Jwt> decode(String token) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(KEY.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        return decoder.decode(token);
    }
}
