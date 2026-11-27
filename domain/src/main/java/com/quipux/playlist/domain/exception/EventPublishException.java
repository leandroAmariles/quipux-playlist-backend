package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;

public class EventPublishException extends BusinessException {

    public EventPublishException(ErrorMessageEnum message, Object... values) {
        super(message, values);
    }
}
