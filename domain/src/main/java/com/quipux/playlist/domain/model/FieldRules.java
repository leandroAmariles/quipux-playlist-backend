package com.quipux.playlist.domain.model;

import com.quipux.playlist.domain.exception.InvalidPlaylistException;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.FieldNameEnum;

final class FieldRules {

    private FieldRules() {
    }

    static void requireMaxLength(String value, int maximum, FieldNameEnum field) {
        if (value != null && value.length() > maximum) {
            throw new InvalidPlaylistException(ErrorMessageEnum.FIELD_TOO_LONG, field.label(), maximum);
        }
    }
}
