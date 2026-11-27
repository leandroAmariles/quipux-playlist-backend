package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;

public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(ErrorMessageEnum.INVALID_CREDENTIALS);
    }
}
