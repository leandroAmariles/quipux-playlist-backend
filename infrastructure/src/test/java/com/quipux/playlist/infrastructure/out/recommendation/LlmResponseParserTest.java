package com.quipux.playlist.infrastructure.out.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.ModelChoice;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LlmResponseParserTest {

    private static final List<CatalogSong> SONGS = IntStream.range(0, 3)
            .mapToObj(number -> CatalogSong.of("Song " + number, "Artist", "Album", "2000", "rock"))
            .toList();
    private static final CandidateAliases CANDIDATES = CandidateAliases.of(SONGS);

    private final LlmResponseParser parser = new LlmResponseParser();

    @Test
    void readsTheChoicesInTheOrderOfTheModel() {
        String content = "{\"recomendaciones\":[{\"idCatalogo\":\"c3\",\"motivo\":\"Third\"},"
                + "{\"idCatalogo\":\"c1\",\"motivo\":\"First\"}]}";

        assertThat(parse(content)).containsExactly(
                new ModelChoice(SONGS.get(2).id(), "Third"), new ModelChoice(SONGS.get(0).id(), "First"));
    }

    @Test
    void takesOnlyTheIdentifierAndTheReason() {
        String content = " {\"extra\":1,\"recomendaciones\":[{\"idCatalogo\":\"c2\",\"titulo\":\"Made up\","
                + "\"artista\":5,\"motivo\":null}]}\n";

        assertThat(parse(content)).containsExactly(new ModelChoice(SONGS.get(1).id(), null));
    }

    @Test
    void acceptsAChoiceWithoutReason() {
        assertThat(parse("{\"recomendaciones\":[{\"idCatalogo\":\"c1\"}]}"))
                .containsExactly(new ModelChoice(SONGS.get(0).id(), null));
    }

    @Test
    void discardsOnlyTheEntriesWithWrongTypesOrUnknownIdentifiers() {
        String content = "{\"recomendaciones\":[1,null,\"c1\",[],{\"motivo\":\"No identifier\"},"
                + "{\"idCatalogo\":1},{\"idCatalogo\":null},{\"idCatalogo\":{}},"
                + "{\"idCatalogo\":\"c1\",\"motivo\":false},{\"idCatalogo\":\"c9\"},"
                + "{\"idCatalogo\":\"" + SONGS.get(0).id() + "\"},{\"idCatalogo\":\"c2\"}]}";

        assertThat(parse(content)).extracting(ModelChoice::catalogId).containsExactly(SONGS.get(1).id());
    }

    @Test
    void acceptsAnEmptyList() {
        assertThat(parse("{\"recomendaciones\":[]}")).isEmpty();
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

    private List<ModelChoice> parse(String content) {
        return parser.parse(content, CANDIDATES);
    }

    private void assertReason(String content, ErrorMessageEnum reason) {
        assertThatThrownBy(() -> parse(content))
                .isInstanceOf(RecommendationProviderUnavailableException.class)
                .hasMessage(reason.format())
                .hasNoCause();
    }
}
