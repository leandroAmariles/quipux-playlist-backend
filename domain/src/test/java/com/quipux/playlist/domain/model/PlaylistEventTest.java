package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.model.enums.PlaylistEventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaylistEventTest {

    @Test
    void createsPlaylistCreatedEventWithSongCount() {
        Playlist playlist = new Playlist("Rock", "Desc", List.of(
                new Song("Uno", "Artista", "Album", "2020", "rock"),
                new Song("Dos", "Artista", "Album", "2021", "rock")));

        PlaylistEvent event = PlaylistEvent.created(playlist);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.eventId().version()).isEqualTo(4);
        assertThat(event.eventId().variant()).isEqualTo(2);
        assertThat(event.eventType()).isEqualTo(PlaylistEventType.PLAYLIST_CREATED);
        assertThat(event.playlistName()).isEqualTo("Rock");
        assertThat(event.songCount()).isEqualTo(2);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void createsPlaylistDeletedEventWithoutSongCount() {
        PlaylistEvent event = PlaylistEvent.deleted("Rock");

        assertThat(event.eventId()).isNotNull();
        assertThat(event.eventId().version()).isEqualTo(4);
        assertThat(event.eventId().variant()).isEqualTo(2);
        assertThat(event.eventType()).isEqualTo(PlaylistEventType.PLAYLIST_DELETED);
        assertThat(event.playlistName()).isEqualTo("Rock");
        assertThat(event.songCount()).isNull();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void resolvesExternalEventTypes() {
        assertThat(PlaylistEventType.fromExternalName("PlaylistCreated"))
                .contains(PlaylistEventType.PLAYLIST_CREATED);
        assertThat(PlaylistEventType.fromExternalName("PlaylistDeleted"))
                .contains(PlaylistEventType.PLAYLIST_DELETED);
        assertThat(PlaylistEventType.fromExternalName("Unknown")).isEmpty();
        assertThat(PlaylistEventType.fromExternalName(null)).isEmpty();
    }

    @Test
    void rejectsInvalidEventAttributes() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        assertThatThrownBy(() -> new PlaylistEvent(id, PlaylistEventType.PLAYLIST_CREATED, " ", 0, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaylistEvent(id, PlaylistEventType.PLAYLIST_CREATED, "Rock", null, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaylistEvent(id, PlaylistEventType.PLAYLIST_CREATED, "Rock", -1, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaylistEvent(id, PlaylistEventType.PLAYLIST_DELETED, "Rock", 1, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
