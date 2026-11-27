package com.quipux.playlist.infrastructure.out.security;

import com.quipux.playlist.application.port.out.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public Mono<String> hash(String password) {
        return Mono.fromCallable(() -> encoder.encode(password))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> matches(String password, String passwordHash) {
        return Mono.fromCallable(() -> encoder.matches(password, passwordHash))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
