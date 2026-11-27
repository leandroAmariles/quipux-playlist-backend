package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;

public class InvalidPlaylistException extends BusinessException {

    public InvalidPlaylistException(ErrorMessageEnum message, Object... values) {
        super(message, values);
    }
}
