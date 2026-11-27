package com.quipux.playlist.infrastructure.in.rest;

import static org.mockito.Mockito.when;

import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PlaylistControllerDeleteTest extends ControllerTestSupport {

    @Test
    void returns204WithoutBody() {
        when(deletePlaylist.delete("Mis favoritas")).thenReturn(Mono.empty());

        webTestClient.delete().uri("/lists/{listName}", "Mis favoritas").exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
    }

    @Test
    void returns404WhenThePlaylistDoesNotExist() {
        when(deletePlaylist.delete("Jazz")).thenReturn(Mono.error(new PlaylistNotFoundException()));

        webTestClient.delete().uri("/lists/{listName}", "Jazz").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("PLAYLIST_GE_00002");
    }
}
