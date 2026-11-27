package com.quipux.playlist.infrastructure.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.quipux.playlist.infrastructure.security.SigningKey;
import com.quipux.playlist.infrastructure.security.TokenLifetime;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

    @Value("${playlist.security.jwt.secret:}")
    private String secret;

    @Value("${playlist.security.jwt.ttl:30m}")
    private Duration lifetime;

    @Bean
    SigningKey signingKey() {
        return new SigningKey(secret);
    }

    @Bean
    TokenLifetime tokenLifetime() {
        return new TokenLifetime(lifetime);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtEncoder jwtEncoder(SigningKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey.secretKey()));
    }
}
