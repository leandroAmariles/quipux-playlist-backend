package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;

public class InvalidPlaylistException extends BusinessException {

    public InvalidPlaylistException(String message) {
        super(ResponseCodeEnum.INVALID_REQUEST, message);
    }
}
