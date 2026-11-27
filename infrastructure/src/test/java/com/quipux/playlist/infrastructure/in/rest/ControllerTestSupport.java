package com.quipux.playlist.infrastructure.in.rest;

import com.quipux.playlist.application.port.in.CreatePlaylistPort;
import com.quipux.playlist.application.port.in.DeletePlaylistPort;
import com.quipux.playlist.application.port.in.GetPlaylistPort;
import com.quipux.playlist.application.port.in.GetPlaylistsPort;
import com.quipux.playlist.infrastructure.in.rest.config.JacksonStrictTypesConfig;
import com.quipux.playlist.infrastructure.in.rest.mapper.PlaylistDtoMapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Web layer with the input ports replaced by mocks.
 */
@WebFluxTest(PlaylistController.class)
@Import({JacksonStrictTypesConfig.class, PlaylistDtoMapperImpl.class})
abstract class ControllerTestSupport {

    @Autowired
    protected WebTestClient webTestClient;

    @MockitoBean
    protected CreatePlaylistPort createPlaylist;

    @MockitoBean
    protected GetPlaylistsPort getPlaylists;

    @MockitoBean
    protected GetPlaylistPort getPlaylist;

    @MockitoBean
    protected DeletePlaylistPort deletePlaylist;
}
