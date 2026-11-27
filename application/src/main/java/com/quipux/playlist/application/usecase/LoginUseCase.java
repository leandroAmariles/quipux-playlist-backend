package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.LoginPort;
import com.quipux.playlist.application.port.out.PasswordHasherPort;
import com.quipux.playlist.application.port.out.TokenIssuerPort;
import com.quipux.playlist.application.port.out.UserRepositoryPort;
import com.quipux.playlist.domain.exception.InvalidCredentialsException;
import com.quipux.playlist.domain.model.AccessToken;
import com.quipux.playlist.domain.model.Credentials;
import com.quipux.playlist.domain.model.User;
import java.util.List;
import java.util.UUID;
import reactor.core.publisher.Mono;

public class LoginUseCase implements LoginPort {

    private final UserRepositoryPort repository;
    private final PasswordHasherPort passwordHasher;
    private final TokenIssuerPort tokenIssuer;
    private final Mono<String> decoyHash;

    public LoginUseCase(UserRepositoryPort repository, PasswordHasherPort passwordHasher, TokenIssuerPort tokenIssuer) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        String decoyPassword = UUID.randomUUID().toString();
        this.decoyHash = Mono.defer(() -> passwordHasher.hash(decoyPassword)).cache();
    }

    @Override
    public Mono<AccessToken> login(Credentials credentials) {
        if (credentials.passwordExceedsLimit()) {
            return Mono.error(new InvalidCredentialsException());
        }
        return repository.findByUsername(credentials.username())
                .map(user -> new Candidate(Mono.just(user.passwordHash()), Mono.just(user)))
                .defaultIfEmpty(new Candidate(decoyHash, Mono.empty()))
                .flatMap(candidate -> authenticate(credentials.password(), candidate))
                .flatMap(tokenIssuer::issue);
    }

    private Mono<User> authenticate(String password, Candidate candidate) {
        return candidate.passwordHash()
                .flatMap(hash -> passwordHasher.matches(password, hash))
                .flatMap(matches -> matches ? candidate.user() : Mono.empty())
                .switchIfEmpty(Mono.error(InvalidCredentialsException::new));
    }

    private record Candidate(Mono<String> passwordHash, Mono<User> user) {
    }
}
