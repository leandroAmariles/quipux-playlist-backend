package com.quipux.playlist.infrastructure.out.event;

import com.quipux.playlist.application.port.out.PlaylistEventPublisherPort;
import com.quipux.playlist.domain.exception.EventPublishException;
import com.quipux.playlist.domain.model.PlaylistEvent;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.infrastructure.event.PlaylistEventSettings;
import com.quipux.playlist.infrastructure.event.dto.PlaylistEventDto;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class KafkaPlaylistEventPublisherAdapter implements PlaylistEventPublisherPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PlaylistEventSettings settings;

    public KafkaPlaylistEventPublisherAdapter(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            PlaylistEventSettings settings) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.settings = settings;
    }

    @Override
    public Mono<Void> publish(PlaylistEvent event) {
        String eventTypeName = event.eventType().externalName();
        return Mono.fromCallable(() -> serialize(event))
                .flatMap(payload -> Mono.fromFuture(
                        () -> kafkaTemplate.send(settings.topic(), event.playlistName(), payload)))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(settings.publishTimeout())
                .onErrorMap(TimeoutException.class, error -> new EventPublishException(
                        ErrorMessageEnum.EVENT_PUBLISH_TIMEOUT, eventTypeName, event.playlistName()))
                .onErrorMap(error -> !(error instanceof EventPublishException), error -> new EventPublishException(
                        ErrorMessageEnum.EVENT_PUBLISH_UNAVAILABLE, eventTypeName, event.playlistName()))
                .then();
    }

    private String serialize(PlaylistEvent event) {
        PlaylistEventDto dto = new PlaylistEventDto(
                event.eventId().toString(),
                event.eventType().externalName(),
                event.playlistName(),
                event.songCount(),
                event.occurredAt().toString());
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JacksonException exception) {
            throw new EventPublishException(
                    ErrorMessageEnum.EVENT_SERIALIZATION_FAILED, event.eventType().externalName());
        }
    }
}
