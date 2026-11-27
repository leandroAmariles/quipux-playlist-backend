package com.quipux.playlist.infrastructure.in.rest;

import com.quipux.playlist.application.port.in.CreatePlaylistPort;
import com.quipux.playlist.application.port.in.DeletePlaylistPort;
import com.quipux.playlist.application.port.in.GetPlaylistPort;
import com.quipux.playlist.application.port.in.GetPlaylistsPort;
import com.quipux.playlist.domain.model.User;
import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.infrastructure.in.rest.config.JacksonStrictTypesConfig;
import com.quipux.playlist.infrastructure.in.rest.config.SecurityConfig;
import com.quipux.playlist.infrastructure.in.rest.mapper.PlaylistDtoMapperImpl;
import com.quipux.playlist.infrastructure.out.security.JwtTokenIssuerAdapter;
import com.quipux.playlist.infrastructure.security.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;


@WebFluxTest(PlaylistController.class)
@Import({
        JacksonStrictTypesConfig.class, SecurityConfig.class, JwtConfig.class, JwtTokenIssuerAdapter.class,
        PlaylistDtoMapperImpl.class})
abstract class ControllerTestSupport {

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    private JwtTokenIssuerAdapter tokenIssuer;

    @MockitoBean
    protected CreatePlaylistPort createPlaylist;

    @MockitoBean
    protected GetPlaylistsPort getPlaylists;

    @MockitoBean
    protected GetPlaylistPort getPlaylist;

    @MockitoBean
    protected DeletePlaylistPort deletePlaylist;

    @BeforeEach
    void authenticateAsAdministrator() {
        StepVerifier.create(tokenIssuer.issue(
                        new User(DemoUser.ADMIN.username(), "unused-hash", DemoUser.ADMIN.role())))
                .assertNext(token -> webTestClient = webTestClient.mutate()
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.value())
                        .build())
                .verifyComplete();
    }
}
