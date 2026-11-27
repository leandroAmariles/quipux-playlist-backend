package com.quipux.playlist.infrastructure.in.rest;

import com.quipux.playlist.application.port.in.LoginPort;
import com.quipux.playlist.infrastructure.in.rest.dto.LoginRequestDto;
import com.quipux.playlist.infrastructure.in.rest.dto.TokenResponseDto;
import com.quipux.playlist.infrastructure.in.rest.mapper.AuthDtoMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class AuthController {

    public static final String LOGIN_PATH = "/auth/login";
    private static final String TOKEN_TYPE = "Bearer";

    private final LoginPort login;
    private final AuthDtoMapper mapper;

    public AuthController(LoginPort login, AuthDtoMapper mapper) {
        this.login = login;
        this.mapper = mapper;
    }

    @PostMapping(path = LOGIN_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<TokenResponseDto> login(@RequestBody Mono<LoginRequestDto> request) {
        return request
                .map(mapper::toDomain)
                .flatMap(login::login)
                .map(token -> mapper.toDto(token, TOKEN_TYPE));
    }
}
