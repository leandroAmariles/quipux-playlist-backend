package com.quipux.playlist.infrastructure.out.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.model.CatalogSong;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CandidateAliasesTest {

    private static final List<CatalogSong> CANDIDATES = IntStream.range(0, 12)
            .mapToObj(number -> CatalogSong.of("Song " + number, "Artist", "Album", "2000", "rock"))
            .toList();

    private final CandidateAliases aliases = CandidateAliases.of(CANDIDATES);

    @Test
    void numbersTheCandidatesInTheirOrder() {
        assertThat(aliases.aliases())
                .containsExactly("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10", "c11", "c12");
        assertThat(aliases.songs().keySet()).containsExactlyElementsOf(aliases.aliases());
        assertThat(aliases.songs().values()).containsExactlyElementsOf(CANDIDATES);
    }

    @Test
    void givesBackTheIdentifierOfAnAlias() {
        assertThat(aliases.idOf("c3")).contains(CANDIDATES.get(2).id());
    }

    @Test
    void knowsNoOtherAlias() {
        assertThat(aliases.idOf("c13")).isEmpty();
        assertThat(aliases.idOf("C1")).isEmpty();
        assertThat(aliases.idOf(CANDIDATES.get(0).id().toString())).isEmpty();
        assertThat(aliases.idOf(null)).isEmpty();
    }
}
