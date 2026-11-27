package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;

public abstract class BusinessException extends RuntimeException {

    private final ResponseCodeEnum responseCode;

    protected BusinessException(ErrorMessageEnum message, Object... values) {
        super(message.format(values));
        this.responseCode = message.responseCode();
    }

    public ResponseCodeEnum responseCode() {
        return responseCode;
    }
}
