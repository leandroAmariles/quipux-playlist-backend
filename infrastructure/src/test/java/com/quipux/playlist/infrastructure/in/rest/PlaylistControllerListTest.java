package com.quipux.playlist.infrastructure.in.rest;

import static org.mockito.Mockito.when;

import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.Song;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonCompareMode;
import reactor.core.publisher.Flux;

class PlaylistControllerListTest extends ControllerTestSupport {

    @Test
    void returns200WithEveryFieldAndNoInternalIdentifiers() {
        when(getPlaylists.getAll()).thenReturn(Flux.just(
                new Playlist("Rock", "loud", List.of(new Song("Uno", null, null, null, null))),
                new Playlist("Jazz", null, List.of())));

        webTestClient.get().uri("/lists").exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        [{"nombre":"Rock","descripcion":"loud","canciones":[
                           {"titulo":"Uno","artista":null,"album":null,"anno":null,"genero":null}]},
                         {"nombre":"Jazz","descripcion":null,"canciones":[]}]
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void returns200WithAnEmptyArrayWithoutPlaylists() {
        when(getPlaylists.getAll()).thenReturn(Flux.empty());

        webTestClient.get().uri("/lists").exchange()
                .expectStatus().isOk()
                .expectBody().json("[]", JsonCompareMode.STRICT);
    }
}
