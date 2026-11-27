package com.quipux.playlist.infrastructure.out.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quipux.playlist.domain.exception.EventPublishException;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.PlaylistEvent;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.infrastructure.event.PlaylistEventSettings;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class KafkaPlaylistEventPublisherAdapterTest {

    private static final PlaylistEventSettings SETTINGS = new PlaylistEventSettings(
            "playlist.events", "playlist-audit", Duration.ofMillis(200));

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void publishesCreatedAndDeletedJsonEventsKeyedByPlaylistName() throws Exception {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(template.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        KafkaPlaylistEventPublisherAdapter adapter =
                new KafkaPlaylistEventPublisherAdapter(template, objectMapper, SETTINGS);

        PlaylistEvent created = PlaylistEvent.created(new Playlist("Mis favoritas", null, List.of(
                new Song("Uno", "Artista", null, null, "rock"))));
        StepVerifier.create(adapter.publish(created)).verifyComplete();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(template).send(eq("playlist.events"), eq("Mis favoritas"), payloadCaptor.capture());
        JsonNode createdJson = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(createdJson.path("eventType").asString()).isEqualTo("PlaylistCreated");
        assertThat(createdJson.path("playlistName").asString()).isEqualTo("Mis favoritas");
        assertThat(createdJson.path("songCount").asInt()).isEqualTo(1);
        assertThat(createdJson.path("eventId").asString()).isEqualTo(created.eventId().toString());

        PlaylistEvent deleted = PlaylistEvent.deleted("Mis favoritas");
        StepVerifier.create(adapter.publish(deleted)).verifyComplete();

        ArgumentCaptor<String> deletedCaptor = ArgumentCaptor.forClass(String.class);
        verify(template, org.mockito.Mockito.times(2))
                .send(eq("playlist.events"), eq("Mis favoritas"), deletedCaptor.capture());
        JsonNode deletedJson = objectMapper.readTree(deletedCaptor.getValue());
        assertThat(deletedJson.path("eventType").asString()).isEqualTo("PlaylistDeleted");
        assertThat(deletedJson.has("songCount")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsBrokerFailureAndTimeoutToEventPublishException() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(template.send(anyString(), anyString(), anyString())).thenReturn(failed);

        KafkaPlaylistEventPublisherAdapter adapter =
                new KafkaPlaylistEventPublisherAdapter(template, objectMapper, SETTINGS);

        StepVerifier.create(adapter.publish(PlaylistEvent.deleted("Rock")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(EventPublishException.class)
                        .hasMessageContaining("unavailable"))
                .verify();

        when(template.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        StepVerifier.create(adapter.publish(PlaylistEvent.deleted("Rock")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(EventPublishException.class)
                        .hasMessageContaining("timed out"))
                .verify();
    }

    @Test
    void noOpPublisherCompletesImmediately() {
        NoOpPlaylistEventPublisherAdapter noOp = new NoOpPlaylistEventPublisherAdapter();

        StepVerifier.create(noOp.publish(PlaylistEvent.deleted("Rock"))).verifyComplete();
    }
}
