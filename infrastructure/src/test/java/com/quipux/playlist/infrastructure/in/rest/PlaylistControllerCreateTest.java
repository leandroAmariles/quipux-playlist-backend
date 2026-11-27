package com.quipux.playlist.infrastructure.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quipux.playlist.domain.exception.DuplicatePlaylistException;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.Song;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class PlaylistControllerCreateTest extends ControllerTestSupport {

    @Test
    void returns201WithLocationAndTheCreatedPlaylist() {
        createsWhateverItReceives();

        post("""
                {"nombre":"Mis favoritas","descripcion":"Demo","canciones":[
                  {"titulo":"Uno","artista":"A","album":"B","anno":"1999","genero":"rock"},
                  {"titulo":"Dos"}]}
                """)
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/lists/Mis%20favoritas")
                .expectBody().json("""
                        {"nombre":"Mis favoritas","descripcion":"Demo","canciones":[
                          {"titulo":"Uno","artista":"A","album":"B","anno":"1999","genero":"rock"},
                          {"titulo":"Dos","artista":null,"album":null,"anno":null,"genero":null}]}
                        """, JsonCompareMode.STRICT);

        verify(createPlaylist).create(new Playlist("Mis favoritas", "Demo", List.of(
                new Song("Uno", "A", "B", "1999", "rock"),
                new Song("Dos", null, null, null, null))));
    }

    @Test
    void returnsEmptySongsWhenTheyAreMissingAndIgnoresUnknownFields() {
        createsWhateverItReceives();

        post("{\"nombre\":\"Rock\",\"id\":7}")
                .expectStatus().isCreated()
                .expectBody().json("{\"nombre\":\"Rock\",\"descripcion\":null,\"canciones\":[]}",
                        JsonCompareMode.STRICT);
    }

    @Test
    void encodesEveryReservedCharacterOfTheLocation() {
        createsWhateverItReceives();

        post("{\"nombre\":\"a;b+c&d?e#f%g\"}")
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/lists/a%3Bb%2Bc%26d%3Fe%23f%25g");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void returns400ForInvalidRequests(String description, String body) {
        expectProblem(post(body), HttpStatus.BAD_REQUEST, "PLAYLIST_GE_00001");

        verifyNoInteractions(createPlaylist);
    }

    private static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("null body", "null"),
                Arguments.of("malformed JSON", "{\"nombre\":"),
                Arguments.of("array instead of object", "[]"),
                Arguments.of("missing name", "{\"descripcion\":\"x\"}"),
                Arguments.of("null name", "{\"nombre\":null}"),
                Arguments.of("empty name", "{\"nombre\":\"\"}"),
                Arguments.of("blank name", "{\"nombre\":\"   \"}"),
                Arguments.of("name with slash", "{\"nombre\":\"a/b\"}"),
                Arguments.of("name with backslash", "{\"nombre\":\"a\\\\b\"}"),
                Arguments.of("name with control character", "{\"nombre\":\"a\\tb\"}"),
                Arguments.of("dot segment name", "{\"nombre\":\"..\"}"),
                Arguments.of("name too long", "{\"nombre\":\"" + "n".repeat(256) + "\"}"),
                Arguments.of("number as name", "{\"nombre\":5}"),
                Arguments.of("decimal as name", "{\"nombre\":1.5}"),
                Arguments.of("boolean as description", "{\"nombre\":\"a\",\"descripcion\":true}"),
                Arguments.of("number as year", "{\"nombre\":\"a\",\"canciones\":[{\"anno\":1999}]}"),
                Arguments.of("object as songs", "{\"nombre\":\"a\",\"canciones\":{}}"),
                Arguments.of("text as songs", "{\"nombre\":\"a\",\"canciones\":\"x\"}"),
                Arguments.of("text as song", "{\"nombre\":\"a\",\"canciones\":[\"x\"]}"),
                Arguments.of("null song", "{\"nombre\":\"a\",\"canciones\":[null]}"),
                Arguments.of("genre too long",
                        "{\"nombre\":\"a\",\"canciones\":[{\"genero\":\"" + "g".repeat(101) + "\"}]}"));
    }

    @Test
    void returns400WithoutBody() {
        expectProblem(webTestClient.post().uri("/lists").contentType(MediaType.APPLICATION_JSON).exchange(),
                HttpStatus.BAD_REQUEST, "PLAYLIST_GE_00001");

        verifyNoInteractions(createPlaylist);
    }

    @Test
    void returns409ForADuplicatedName() {
        when(createPlaylist.create(any())).thenReturn(Mono.error(new DuplicatePlaylistException()));

        expectProblem(post("{\"nombre\":\"Rock\"}"), HttpStatus.CONFLICT, "PLAYLIST_GE_00003")
                .jsonPath("$.detail").isEqualTo("A playlist with that name already exists")
                .jsonPath("$.instance").isEqualTo("/lists");
    }

    @Test
    void returns415ForAnotherContentType() {
        webTestClient.post().uri("/lists").contentType(MediaType.TEXT_PLAIN).bodyValue("Rock").exchange()
                .expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);

        verifyNoInteractions(createPlaylist);
    }

    @Test
    void returns500WithoutInternalDetails() {
        when(createPlaylist.create(any())).thenReturn(Mono.error(new IllegalStateException("secret table name")));

        expectProblem(post("{\"nombre\":\"Rock\"}"), HttpStatus.INTERNAL_SERVER_ERROR, "PLAYLIST_GE_99999")
                .consumeWith(result -> assertThat(new String(result.getResponseBody()))
                        .doesNotContain("secret")
                        .doesNotContain("IllegalStateException"));
    }

    private void createsWhateverItReceives() {
        when(createPlaylist.create(any())).thenAnswer(invocation -> Mono.just(invocation.<Playlist>getArgument(0)));
    }

    private WebTestClient.ResponseSpec post(String body) {
        return webTestClient.post().uri("/lists")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private static WebTestClient.BodyContentSpec expectProblem(
            WebTestClient.ResponseSpec response, HttpStatus status, String code) {
        WebTestClient.BodyContentSpec body = response
                .expectStatus().isEqualTo(status)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody();
        body.jsonPath("$.status").isEqualTo(status.value()).jsonPath("$.code").isEqualTo(code);
        return body;
    }
}
