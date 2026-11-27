package com.quipux.playlist.infrastructure.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaylistEventDto(
        String eventId,
        String eventType,
        String playlistName,
        Integer songCount,
        String occurredAt) {
}
