package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;

public class GenreCacheUnavailableException extends BusinessException {

    public GenreCacheUnavailableException(ErrorMessageEnum message, Object... values) {
        super(message, values);
    }
}
