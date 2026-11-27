package com.quipux.playlist.application.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.out.PasswordHasherPort;
import com.quipux.playlist.application.port.out.UserRepositoryPort;
import com.quipux.playlist.domain.model.User;
import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.domain.model.enums.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RegisterDemoUsersUseCaseTest {

    @Mock
    private UserRepositoryPort repository;

    @Mock
    private PasswordHasherPort passwordHasher;

    @Test
    void savesEveryDemoUserWithTheHashOfItsPassword() {
        when(passwordHasher.hash(anyString()))
                .thenAnswer(invocation -> Mono.just("hash-of-" + invocation.getArgument(0)));
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.<User>getArgument(0)));

        StepVerifier.create(new RegisterDemoUsersUseCase(repository, passwordHasher)
                        .register(Map.of(DemoUser.USER, "one", DemoUser.ADMIN, "two")))
                .verifyComplete();

        verify(repository).save(new User("user", "hash-of-one", Role.USER));
        verify(repository).save(new User("admin", "hash-of-two", Role.ADMIN));
    }
}
