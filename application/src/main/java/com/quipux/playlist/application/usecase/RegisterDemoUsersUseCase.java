package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.RegisterDemoUsersPort;
import com.quipux.playlist.application.port.out.PasswordHasherPort;
import com.quipux.playlist.application.port.out.UserRepositoryPort;
import com.quipux.playlist.domain.model.User;
import com.quipux.playlist.domain.model.enums.DemoUser;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RegisterDemoUsersUseCase implements RegisterDemoUsersPort {

    private final UserRepositoryPort repository;
    private final PasswordHasherPort passwordHasher;

    public RegisterDemoUsersUseCase(UserRepositoryPort repository, PasswordHasherPort passwordHasher) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public Mono<Void> register(Map<DemoUser, String> passwords) {
        return Flux.fromArray(DemoUser.values())
                .concatMap(demoUser -> passwordHasher.hash(passwords.get(demoUser))
                        .map(hash -> new User(demoUser.username(), hash, demoUser.role())))
                .concatMap(repository::save)
                .then();
    }
}
