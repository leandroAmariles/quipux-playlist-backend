package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;

/**
 * Deliberately without a cause: what the model gateway or the database answers may repeat data or secrets.
 */
public class SongCatalogUnavailableException extends BusinessException {

    public SongCatalogUnavailableException(ErrorMessageEnum message, Object... values) {
        super(message, values);
    }
}
