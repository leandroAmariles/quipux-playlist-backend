package com.quipux.playlist.domain.exception;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;

/**
 * Deliberately without a cause: what a model provider answers may repeat the prompt.
 */
public class RecommendationProviderUnavailableException extends BusinessException {

    public RecommendationProviderUnavailableException(ErrorMessageEnum message, Object... values) {
        super(message, values);
    }
}
