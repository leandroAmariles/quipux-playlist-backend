package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;

public class PlaylistNotFoundException extends BusinessException {

    public PlaylistNotFoundException() {
        super(ResponseCodeEnum.PLAYLIST_NOT_FOUND, "The playlist does not exist");
    }
}
