package com.quipux.playlist.infrastructure.event;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.time.Duration;

public record PlaylistEventSettings(String topic, String consumerGroup, Duration publishTimeout) {

    public PlaylistEventSettings {
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException(ErrorMessageEnum.INVALID_EVENT_SETTING.withCode("topic"));
        }
        if (consumerGroup == null || consumerGroup.isBlank()) {
            throw new IllegalStateException(ErrorMessageEnum.INVALID_EVENT_SETTING.withCode("consumer-group"));
        }
        if (publishTimeout == null || publishTimeout.isZero() || publishTimeout.isNegative()) {
            throw new IllegalStateException(ErrorMessageEnum.INVALID_EVENT_DURATION.withCode("publish-timeout"));
        }
    }
}
