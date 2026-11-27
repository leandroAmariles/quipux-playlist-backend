package com.quipux.playlist.infrastructure.in.event;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.PlaylistEventType;
import com.quipux.playlist.infrastructure.event.dto.PlaylistEventDto;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class KafkaPlaylistAuditConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaPlaylistAuditConsumer.class);

    private final ObjectMapper objectMapper;

    public KafkaPlaylistAuditConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${playlist.events.topic:playlist.events}",
            groupId = "${playlist.events.consumer-group:playlist-audit}",
            containerFactory = "playlistAuditListenerContainerFactory")
    public void onMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            LOG.warn(ErrorMessageEnum.EVENT_MESSAGE_UNREADABLE.withCode("blank message"));
            return;
        }
        try {
            PlaylistEventDto dto = objectMapper.readValue(rawMessage, PlaylistEventDto.class);
            if (dto == null) {
                LOG.warn(ErrorMessageEnum.EVENT_MESSAGE_UNREADABLE.withCode("empty payload"));
                return;
            }
            PlaylistEventType eventType = PlaylistEventType.fromExternalName(dto.eventType())
                    .orElseThrow(() -> new IllegalArgumentException("unknown eventType"));
            UUID eventId = parseUuid(dto.eventId());
            if (dto.playlistName() == null || dto.playlistName().isBlank()) {
                throw new IllegalArgumentException("blank playlistName");
            }
            Instant occurredAt = parseInstant(dto.occurredAt());
            logAuditEvent(eventType, dto, eventId, occurredAt);
        } catch (JacksonException | IllegalArgumentException exception) {
            LOG.warn(ErrorMessageEnum.EVENT_MESSAGE_UNREADABLE.withCode(reasonOf(exception)));
        }
    }

    private static void logAuditEvent(
            PlaylistEventType eventType,
            PlaylistEventDto dto,
            UUID eventId,
            Instant occurredAt) {
        if (eventType == PlaylistEventType.PLAYLIST_CREATED) {
            if (dto.songCount() == null || dto.songCount() < 0) {
                throw new IllegalArgumentException("invalid songCount");
            }
            LOG.info("Playlist audit event: eventType={} playlistName={} songCount={} eventId={} occurredAt={}",
                    eventType.externalName(), dto.playlistName(), dto.songCount(), eventId, occurredAt);
            return;
        }
        LOG.info("Playlist audit event: eventType={} playlistName={} eventId={} occurredAt={}",
                eventType.externalName(), dto.playlistName(), eventId, occurredAt);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("blank eventId");
        }
        return UUID.fromString(value);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("blank occurredAt");
        }
        return Instant.parse(value);
    }

    private static String reasonOf(Throwable error) {
        return error.getMessage() != null && !error.getMessage().isBlank()
                ? error.getMessage()
                : error.getClass().getSimpleName();
    }
}
