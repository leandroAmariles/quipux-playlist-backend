package com.quipux.playlist.application.port.out;

import com.quipux.playlist.domain.model.User;
import reactor.core.publisher.Mono;

public interface UserRepositoryPort {

    Mono<User> findByUsername(String username);

    Mono<User> save(User user);
}
