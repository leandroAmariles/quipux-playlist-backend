package com.quipux.playlist.infrastructure.out.persistence;

import com.quipux.playlist.application.port.out.UserRepositoryPort;
import com.quipux.playlist.domain.model.User;
import com.quipux.playlist.infrastructure.out.persistence.entity.UserEntity;
import com.quipux.playlist.infrastructure.out.persistence.mapper.UserEntityMapper;
import com.quipux.playlist.infrastructure.out.persistence.repository.UserR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final UserR2dbcRepository repository;
    private final UserEntityMapper mapper;

    public UserPersistenceAdapter(UserR2dbcRepository repository, UserEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Mono<User> findByUsername(String username) {
        return repository.findByUsername(username).map(mapper::toDomain);
    }

    @Override
    public Mono<User> save(User user) {
        return repository.findByUsername(user.username())
                .map(UserEntity::id)
                .map(id -> mapper.toEntity(user, id))
                .defaultIfEmpty(mapper.toEntity(user, null))
                .flatMap(repository::save)
                .map(mapper::toDomain);
    }
}
