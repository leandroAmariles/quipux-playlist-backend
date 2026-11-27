package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;

public abstract class BusinessException extends RuntimeException {

    private final ResponseCodeEnum responseCode;

    protected BusinessException(ResponseCodeEnum responseCode, String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public ResponseCodeEnum responseCode() {
        return responseCode;
    }
}
