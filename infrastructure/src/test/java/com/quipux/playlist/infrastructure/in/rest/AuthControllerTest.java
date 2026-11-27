package com.quipux.playlist.infrastructure.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.in.LoginPort;
import com.quipux.playlist.domain.exception.InvalidCredentialsException;
import com.quipux.playlist.domain.model.AccessToken;
import com.quipux.playlist.domain.model.Credentials;
import com.quipux.playlist.infrastructure.in.rest.config.JacksonStrictTypesConfig;
import com.quipux.playlist.infrastructure.in.rest.config.SecurityConfig;
import com.quipux.playlist.infrastructure.in.rest.mapper.AuthDtoMapperImpl;
import com.quipux.playlist.infrastructure.security.config.JwtConfig;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(AuthController.class)
@Import({JacksonStrictTypesConfig.class, SecurityConfig.class, JwtConfig.class, AuthDtoMapperImpl.class})
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private LoginPort login;

    @Test
    void returns200WithTheToken() {
        when(login.login(any())).thenReturn(Mono.just(new AccessToken("the-token", 1800)));

        post("{\"username\":\"admin\",\"password\":\"secret\"}")
                .expectStatus().isOk()
                .expectBody().json("{\"accessToken\":\"the-token\",\"tokenType\":\"Bearer\",\"expiresIn\":1800}",
                        JsonCompareMode.STRICT);

        verify(login).login(new Credentials("admin", "secret"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void returns400ForInvalidRequests(String description, String body) {
        expectProblem(post(body), HttpStatus.BAD_REQUEST, "PLAYLIST_GE_00001");

        verifyNoInteractions(login);
    }

    private static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("null body", "null"),
                Arguments.of("malformed JSON", "{\"username\":"),
                Arguments.of("missing username", "{\"password\":\"x\"}"),
                Arguments.of("missing password", "{\"username\":\"x\"}"),
                Arguments.of("null password", "{\"username\":\"x\",\"password\":null}"),
                Arguments.of("blank username", "{\"username\":\" \",\"password\":\"x\"}"),
                Arguments.of("empty password", "{\"username\":\"x\",\"password\":\"\"}"),
                Arguments.of("number as password", "{\"username\":\"x\",\"password\":5}"),
                Arguments.of("object as username", "{\"username\":{},\"password\":\"x\"}"));
    }

    @Test
    void returns400WithoutBody() {
        expectProblem(webTestClient.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON).exchange(),
                HttpStatus.BAD_REQUEST, "PLAYLIST_GE_00001");
    }

    @Test
    void returns401ForInvalidCredentials() {
        when(login.login(any())).thenReturn(Mono.error(new InvalidCredentialsException()));

        expectProblem(post("{\"username\":\"admin\",\"password\":\"wrong\"}"),
                HttpStatus.UNAUTHORIZED, "AUTH_ERROR_001");
    }

    @Test
    void returns415ForAnotherContentType() {
        webTestClient.post().uri("/auth/login").contentType(MediaType.TEXT_PLAIN).bodyValue("admin").exchange()
                .expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        verifyNoInteractions(login);
    }

    private WebTestClient.ResponseSpec post(String body) {
        return webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private static void expectProblem(WebTestClient.ResponseSpec response, HttpStatus status, String code) {
        response.expectStatus().isEqualTo(status)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(status.value())
                .jsonPath("$.code").isEqualTo(code);
    }
}
