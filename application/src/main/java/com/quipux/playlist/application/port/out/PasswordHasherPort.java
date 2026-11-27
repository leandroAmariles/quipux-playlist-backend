package com.quipux.playlist.application.port.out;

import reactor.core.publisher.Mono;

public interface PasswordHasherPort {

    Mono<String> hash(String password);

    Mono<Boolean> matches(String password, String passwordHash);
}
