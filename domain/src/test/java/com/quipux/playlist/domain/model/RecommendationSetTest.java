package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.model.enums.RecommendationOrigin;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecommendationSetTest {

    @Test
    void keepsThePromptVersionOfAnAiAnswer() {
        RecommendationSet set = RecommendationSet.fromAi("Mix", "v1", songs(1));

        assertThat(set.origin()).isEqualTo(RecommendationOrigin.AI);
        assertThat(set.promptVersion()).isEqualTo("v1");
        assertThat(set.playlistName()).isEqualTo("Mix");
    }

    @Test
    void leavesThePromptVersionEmptyOutsideAnAiAnswer() {
        RecommendationSet set = new RecommendationSet("Mix", RecommendationOrigin.FALLBACK, "v1", songs(1));

        assertThat(set.promptVersion()).isEmpty();
        assertThat(RecommendationSet.fallback("Mix", List.of()).promptVersion()).isEmpty();
    }

    @Test
    void holdsAtMostFiveRecommendations() {
        RecommendationSet set = RecommendationSet.fromAi("Mix", "v1", songs(7));

        assertThat(set.recommendations()).containsExactlyElementsOf(songs(5));
    }

    @Test
    void doesNotChangeWhenTheSourceListDoes() {
        List<Recommendation> source = new ArrayList<>(songs(2));
        RecommendationSet set = RecommendationSet.fallback("Mix", source);

        source.clear();

        assertThat(set.recommendations()).hasSize(2);
    }

    @Test
    void exposesTheExternalValueOfEachOrigin() {
        assertThat(RecommendationOrigin.SIMILARITY.externalValue()).isEqualTo("SIMILITUD");
        assertThat(RecommendationOrigin.AI.externalValue()).isEqualTo("IA");
        assertThat(RecommendationOrigin.FALLBACK.externalValue()).isEqualTo("RESPALDO");
    }

    @Test
    void hasNoPromptVersionWhenTheSongsComeFromTheSearch() {
        RecommendationSet set = RecommendationSet.similar("Mix", songs(7));

        assertThat(set.origin()).isEqualTo(RecommendationOrigin.SIMILARITY);
        assertThat(set.promptVersion()).isEmpty();
        assertThat(set.recommendations()).containsExactlyElementsOf(songs(5));
    }

    private static List<Recommendation> songs(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new Recommendation("Title " + index, "Artist", "rock", "Reason"))
                .toList();
    }
}
