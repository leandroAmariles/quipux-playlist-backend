package com.quipux.playlist.infrastructure.out.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LlmResponseParserTest {

    private final LlmResponseParser parser = new LlmResponseParser();

    @Test
    void readsTheSuggestions() {
        String content = "{\"recomendaciones\":[{\"titulo\":\"Song\",\"artista\":\"Artist\","
                + "\"genero\":\"rock\",\"motivo\":\"Reason\"}]}";

        assertThat(parser.parse(content))
                .containsExactly(new Recommendation("Song", "Artist", "rock", "Reason"));
    }

    @Test
    void ignoresExtraFieldsAndTheCatalogIdOfTheModel() {
        String content = " {\"extra\":1,\"recomendaciones\":[{\"titulo\":\"Song\",\"idCatalogo\":\"made-up\","
                + "\"artista\":\"Artist\",\"genero\":null}]}\n";

        assertThat(parser.parse(content)).containsExactly(new Recommendation("Song", "Artist", null, null));
    }

    @Test
    void discardsOnlyTheEntriesWithWrongTypes() {
        String content = "{\"recomendaciones\":[1,null,\"text\",[],"
                + "{\"titulo\":5,\"artista\":\"A\"},{\"titulo\":{},\"artista\":\"B\"},"
                + "{\"titulo\":\"T\",\"artista\":[\"C\"]},{\"titulo\":\"T\",\"artista\":\"D\",\"motivo\":false},"
                + "{\"titulo\":\"Kept\",\"artista\":\"E\"}]}";

        assertThat(parser.parse(content)).extracting(Recommendation::title).containsExactly("Kept");
    }

    @Test
    void acceptsAnEmptyList() {
        assertThat(parser.parse("{\"recomendaciones\":[]}")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n"})
    void reportsAnEmptyAnswer(String content) {
        assertReason(content, ErrorMessageEnum.RECOMMENDATION_PROVIDER_EMPTY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "texto", "null", "[]", "\"recomendaciones\"", "{}", "{\"otra\":[]}", "{\"recomendaciones\":{}}",
            "{\"recomendaciones\":\"x\"}", "{\"recomendaciones\":[", "{\"recomendaciones\":[]} sobra",
            "{\"recomendaciones\":[]}{\"recomendaciones\":[]}", "Aquí tienes: {\"recomendaciones\":[]}",
            "```json\n{\"recomendaciones\":[]}\n```"})
    void reportsAnythingElseAsUnreadable(String content) {
        assertReason(content, ErrorMessageEnum.RECOMMENDATION_PROVIDER_UNREADABLE);
    }

    private void assertReason(String content, ErrorMessageEnum reason) {
        assertThatThrownBy(() -> parser.parse(content))
                .isInstanceOf(RecommendationProviderUnavailableException.class)
                .hasMessage(reason.format())
                .hasNoCause();
    }
}
