package com.quipux.playlist.infrastructure.out.security;

import com.quipux.playlist.application.port.out.TokenIssuerPort;
import com.quipux.playlist.domain.model.AccessToken;
import com.quipux.playlist.domain.model.User;
import com.quipux.playlist.infrastructure.security.TokenClaim;
import com.quipux.playlist.infrastructure.security.TokenLifetime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class JwtTokenIssuerAdapter implements TokenIssuerPort {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration lifetime;

    public JwtTokenIssuerAdapter(JwtEncoder encoder, Clock clock, TokenLifetime tokenLifetime) {
        this.encoder = encoder;
        this.clock = clock;
        this.lifetime = tokenLifetime.duration();
    }

    @Override
    public Mono<AccessToken> issue(User user) {
        return Mono.fromSupplier(() -> sign(user));
    }

    private AccessToken sign(User user) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.username())
                .claim(TokenClaim.ROLE.claimName(), user.role().name())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(lifetime))
                .build();
        JwtEncoderParameters parameters =
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims);
        return new AccessToken(encoder.encode(parameters).getTokenValue(), lifetime.toSeconds());
    }
}
