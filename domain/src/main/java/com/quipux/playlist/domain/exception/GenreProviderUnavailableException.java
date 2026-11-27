package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;

public class GenreProviderUnavailableException extends BusinessException {

    public GenreProviderUnavailableException(ErrorMessageEnum message, Object... values) {
        super(message, values);
    }
}
