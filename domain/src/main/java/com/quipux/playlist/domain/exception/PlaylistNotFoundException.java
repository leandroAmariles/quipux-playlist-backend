package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;

public class PlaylistNotFoundException extends BusinessException {

    public PlaylistNotFoundException() {
        super(ErrorMessageEnum.PLAYLIST_NOT_FOUND);
    }
}
