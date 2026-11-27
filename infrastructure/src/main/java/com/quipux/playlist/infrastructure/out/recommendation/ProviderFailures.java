package com.quipux.playlist.infrastructure.out.recommendation;

import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Turns the failure of a call into the domain error. Neither its message nor its cause is kept: what the gateway
 * answers may repeat the prompt.
 */
final class ProviderFailures {

    private static final int MAX_CAUSE_DEPTH = 20;

    private ProviderFailures() {
    }

    static RecommendationProviderUnavailableException translate(Throwable error) {
        if (error instanceof RecommendationProviderUnavailableException known) {
            return known;
        }
        List<Throwable> causes = Stream.iterate(error, Objects::nonNull, Throwable::getCause)
                .limit(MAX_CAUSE_DEPTH)
                .toList();
        if (error instanceof CallNotPermittedException) {
            return unavailable(ErrorMessageEnum.RECOMMENDATION_PROVIDER_CIRCUIT_OPEN);
        }
        for (Throwable cause : causes) {
            if (cause instanceof OpenAIServiceException response) {
                return new RecommendationProviderUnavailableException(
                        ErrorMessageEnum.RECOMMENDATION_PROVIDER_ERROR_RESPONSE, response.statusCode());
            }
        }
        // The time limit of the client arrives wrapped, sometimes as invalid data: it is looked for first.
        if (causes.stream().anyMatch(cause ->
                cause instanceof TimeoutException || cause instanceof InterruptedIOException)) {
            return unavailable(ErrorMessageEnum.RECOMMENDATION_PROVIDER_TIMEOUT);
        }
        if (causes.stream().anyMatch(OpenAIInvalidDataException.class::isInstance)) {
            return unavailable(ErrorMessageEnum.RECOMMENDATION_PROVIDER_UNREADABLE);
        }
        return unavailable(ErrorMessageEnum.RECOMMENDATION_PROVIDER_UNREACHABLE);
    }

    private static RecommendationProviderUnavailableException unavailable(ErrorMessageEnum reason) {
        return new RecommendationProviderUnavailableException(reason);
    }
}
