package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;

public class InvalidLoginRequestException extends BusinessException {

    public InvalidLoginRequestException(ErrorMessageEnum message, Object... values) {
        super(message, values);
    }
}
