package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;

public class DuplicatePlaylistException extends BusinessException {

    public DuplicatePlaylistException() {
        super(ErrorMessageEnum.PLAYLIST_ALREADY_EXISTS);
    }
}
