package com.quipux.playlist.application.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.out.PasswordHasherPort;
import com.quipux.playlist.application.port.out.TokenIssuerPort;
import com.quipux.playlist.application.port.out.UserRepositoryPort;
import com.quipux.playlist.domain.exception.InvalidCredentialsException;
import com.quipux.playlist.domain.model.AccessToken;
import com.quipux.playlist.domain.model.Credentials;
import com.quipux.playlist.domain.model.User;
import com.quipux.playlist.domain.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginUseCaseTest {

    private static final User USER = new User("user", "stored-hash", Role.USER);

    @Mock
    private UserRepositoryPort repository;

    @Mock
    private PasswordHasherPort passwordHasher;

    @Mock
    private TokenIssuerPort tokenIssuer;

    private LoginUseCase useCase;

    @BeforeEach
    void createUseCase() {
        when(passwordHasher.hash(anyString())).thenReturn(Mono.just("decoy-hash"));
        when(passwordHasher.matches(anyString(), anyString())).thenReturn(Mono.just(false));
        when(repository.findByUsername("user")).thenReturn(Mono.just(USER));
        when(repository.findByUsername("nobody")).thenReturn(Mono.empty());
        useCase = new LoginUseCase(repository, passwordHasher, tokenIssuer);
    }

    @Test
    void issuesATokenForTheRightPassword() {
        AccessToken token = new AccessToken("token", 60);
        when(passwordHasher.matches("right", "stored-hash")).thenReturn(Mono.just(true));
        when(tokenIssuer.issue(USER)).thenReturn(Mono.just(token));

        StepVerifier.create(useCase.login(new Credentials("user", "right")))
                .expectNext(token)
                .verifyComplete();
    }

    @Test
    void rejectsAWrongPassword() {
        StepVerifier.create(useCase.login(new Credentials("user", "wrong")))
                .expectError(InvalidCredentialsException.class)
                .verify();

        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void comparesAnUnknownUserAgainstADecoyAndRejectsItEvenIfItMatches() {
        when(passwordHasher.matches("any", "decoy-hash")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.login(new Credentials("nobody", "any")))
                .expectError(InvalidCredentialsException.class)
                .verify();
        StepVerifier.create(useCase.login(new Credentials("nobody", "any")))
                .expectError(InvalidCredentialsException.class)
                .verify();

        verify(passwordHasher, times(2)).matches("any", "decoy-hash");
        verify(passwordHasher).hash(anyString());
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void comparesAPasswordOfExactly72Bytes() {
        String password = "p".repeat(72);

        StepVerifier.create(useCase.login(new Credentials("user", password)))
                .expectError(InvalidCredentialsException.class)
                .verify();

        verify(passwordHasher).matches(password, "stored-hash");
    }

    @ParameterizedTest
    @ValueSource(ints = {37, 73})
    void rejectsALongerPasswordWithoutComparingIt(int characters) {
        StepVerifier.create(useCase.login(new Credentials("user", "ñ".repeat(characters))))
                .expectError(InvalidCredentialsException.class)
                .verify();

        verify(passwordHasher, never()).matches(anyString(), anyString());
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void propagatesFailuresOfThePorts() {
        when(repository.findByUsername("user")).thenReturn(Mono.error(new IllegalStateException("database")));

        StepVerifier.create(useCase.login(new Credentials("user", "right")))
                .expectError(IllegalStateException.class)
                .verify();

        verify(tokenIssuer, never()).issue(any());
    }
}
