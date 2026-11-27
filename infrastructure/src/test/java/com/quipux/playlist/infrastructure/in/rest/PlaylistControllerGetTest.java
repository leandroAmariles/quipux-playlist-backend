package com.quipux.playlist.infrastructure.in.rest;

import static org.mockito.Mockito.when;

import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
import com.quipux.playlist.domain.model.Playlist;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import reactor.core.publisher.Mono;

class PlaylistControllerGetTest extends ControllerTestSupport {

    @Test
    void returns200WithThePlaylistOfTheDecodedName() {
        when(getPlaylist.getByName("Mis favoritas"))
                .thenReturn(Mono.just(new Playlist("Mis favoritas", "Demo", List.of())));

        webTestClient.get().uri("/lists/{listName}", "Mis favoritas").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"nombre\":\"Mis favoritas\",\"descripcion\":\"Demo\",\"canciones\":[]}",
                        JsonCompareMode.STRICT);
    }

    @Test
    void returns404WhenThePlaylistDoesNotExist() {
        when(getPlaylist.getByName("Jazz")).thenReturn(Mono.error(new PlaylistNotFoundException()));

        webTestClient.get().uri("/lists/{listName}", "Jazz").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PLAYLIST_GE_00002")
                .jsonPath("$.detail").isEqualTo("The playlist does not exist");
    }
}
