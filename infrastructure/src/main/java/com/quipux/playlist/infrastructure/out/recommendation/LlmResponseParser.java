package com.quipux.playlist.infrastructure.out.recommendation;

import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.model.ModelChoice;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the shape of what the model answered: exactly one JSON object with its choices. Only the identifier and the
 * reason are taken. An entry with a field of another type, or with an identifier that was not offered, is discarded
 * on its own. What may be delivered is decided by the domain.
 */
public class LlmResponseParser {

    private static final String CHOICES = "recomendaciones";
    private static final String ID = "idCatalogo";
    private static final String REASON = "motivo";

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public List<ModelChoice> parse(String content, CandidateAliases candidates) {
        if (content == null || content.isBlank()) {
            throw unavailable(ErrorMessageEnum.RECOMMENDATION_PROVIDER_EMPTY);
        }
        JsonNode entries = read(content).path(CHOICES);
        if (!entries.isArray()) {
            throw unavailable(ErrorMessageEnum.RECOMMENDATION_PROVIDER_UNREADABLE);
        }
        return entries.valueStream()
                .flatMap(entry -> choiceOf(entry, candidates))
                .toList();
    }

    private JsonNode read(String content) {
        try {
            return mapper.readTree(content);
        } catch (JacksonException exception) {
            // The cause is dropped on purpose: it quotes the content, which may repeat the prompt.
            throw unavailable(ErrorMessageEnum.RECOMMENDATION_PROVIDER_UNREADABLE);
        }
    }

    private static Stream<ModelChoice> choiceOf(JsonNode entry, CandidateAliases candidates) {
        JsonNode id = entry.path(ID);
        JsonNode reason = entry.path(REASON);
        boolean wellTyped = id.isString() && (reason.isString() || reason.isNull() || reason.isMissingNode());
        if (!wellTyped) {
            return Stream.empty();
        }
        return candidates.idOf(id.asString())
                .map(catalogId -> new ModelChoice(catalogId, reason.isString() ? reason.asString() : null))
                .stream();
    }

    private static RecommendationProviderUnavailableException unavailable(ErrorMessageEnum reason) {
        return new RecommendationProviderUnavailableException(reason);
    }
}
