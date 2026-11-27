package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;

public class DuplicatePlaylistException extends BusinessException {

    public DuplicatePlaylistException() {
        super(ResponseCodeEnum.PLAYLIST_ALREADY_EXISTS, "A playlist with that name already exists");
    }
}
