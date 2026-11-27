package com.quipux.playlist.infrastructure.out.recommendation;

import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the shape of what the model answered: exactly one JSON object with the suggestions. An entry with a field of
 * another type is discarded on its own. What may be delivered is decided by the domain.
 */
public class LlmResponseParser {

    private static final String SUGGESTIONS = "recomendaciones";
    private static final List<String> FIELDS = List.of("titulo", "artista", "genero", "motivo");

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public List<Recommendation> parse(String content) {
        if (content == null || content.isBlank()) {
            throw unavailable(ErrorMessageEnum.RECOMMENDATION_PROVIDER_EMPTY);
        }
        JsonNode entries = read(content).path(SUGGESTIONS);
        if (!entries.isArray()) {
            throw unavailable(ErrorMessageEnum.RECOMMENDATION_PROVIDER_UNREADABLE);
        }
        return entries.valueStream()
                .flatMap(LlmResponseParser::suggestionOf)
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

    private static Stream<Recommendation> suggestionOf(JsonNode entry) {
        boolean wellTyped = entry.isObject() && FIELDS.stream()
                .map(entry::path)
                .allMatch(field -> field.isString() || field.isNull() || field.isMissingNode());
        if (!wellTyped) {
            return Stream.empty();
        }
        return Stream.of(new Recommendation(
                textOf(entry, FIELDS.get(0)), textOf(entry, FIELDS.get(1)),
                textOf(entry, FIELDS.get(2)), textOf(entry, FIELDS.get(3))));
    }

    private static String textOf(JsonNode entry, String field) {
        return entry.path(field).isString() ? entry.path(field).asString() : null;
    }

    private static RecommendationProviderUnavailableException unavailable(ErrorMessageEnum reason) {
        return new RecommendationProviderUnavailableException(reason);
    }
}
