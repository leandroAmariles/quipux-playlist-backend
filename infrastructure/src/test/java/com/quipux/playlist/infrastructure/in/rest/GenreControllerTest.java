package com.quipux.playlist.infrastructure.in.rest;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.in.GetGenresPort;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.User;
import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.domain.model.enums.GenreSource;
import com.quipux.playlist.infrastructure.in.rest.config.JacksonStrictTypesConfig;
import com.quipux.playlist.infrastructure.in.rest.config.SecurityConfig;
import com.quipux.playlist.infrastructure.in.rest.mapper.GenreDtoMapperImpl;
import com.quipux.playlist.infrastructure.out.security.JwtTokenIssuerAdapter;
import com.quipux.playlist.infrastructure.security.config.JwtConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@WebFluxTest(GenreController.class)
@Import({
        JacksonStrictTypesConfig.class, SecurityConfig.class, JwtConfig.class, JwtTokenIssuerAdapter.class,
        GenreDtoMapperImpl.class})
class GenreControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenIssuerAdapter tokenIssuer;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private GetGenresPort getGenres;

    @ParameterizedTest
    @EnumSource(DemoUser.class)
    void returnsTheGenresAndTheirSourceToBothRoles(DemoUser demoUser) {
        when(getGenres.getGenres()).thenReturn(Mono.just(
                new GenreCatalog(GenreSource.SPOTIFY, List.of("rock", "pop"))));

        StepVerifier.create(tokenIssuer.issue(new User(demoUser.username(), "unused-hash", demoUser.role())))
                .assertNext(token -> get(token.value())
                        .expectStatus().isOk()
                        .expectBody().json("{\"generos\":[\"rock\",\"pop\"],\"origen\":\"SPOTIFY\"}",
                                JsonCompareMode.STRICT))
                .verifyComplete();
    }

    @Test
    void returns401WithoutAToken() {
        webTestClient.get().uri("/genres").exchange().expectStatus().isUnauthorized();

        verifyNoInteractions(getGenres);
    }

    @Test
    void returns403ToATokenWithoutAKnownRole() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("someone")
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        get(token).expectStatus().isForbidden();

        verifyNoInteractions(getGenres);
    }

    private WebTestClient.ResponseSpec get(String token) {
        return webTestClient.get().uri("/genres")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
    }
}
