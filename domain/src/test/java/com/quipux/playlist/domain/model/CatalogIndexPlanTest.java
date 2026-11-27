package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogIndexPlanTest {

    private static final CatalogSong NEW = CatalogSong.of("New", "One", "Album", "1990", "rock");
    private static final CatalogSong SAME = CatalogSong.of("Same", "Two", "Album", "1991", "pop");
    private static final CatalogSong OTHER_MODEL = CatalogSong.of("Other", "Three", "Album", "1992", "pop");
    private static final UUID GONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final SongCatalog CATALOG =
            new SongCatalog(List.of(NEW, SAME, OTHER_MODEL), "current-model");

    @Test
    void indexesWhatIsMissingOrFromAnotherModelAndRemovesTheRest() {
        CatalogIndexPlan plan = CatalogIndexPlan.of(CATALOG, Map.of(
                SAME.id(), "current-model",
                OTHER_MODEL.id(), "old-model",
                GONE, "current-model"));

        assertThat(plan.toIndex()).containsExactly(NEW, OTHER_MODEL);
        assertThat(plan.toRemove()).containsExactly(GONE);
        assertThat(plan.kept()).isEqualTo(1);
    }

    @Test
    void indexesEverythingTheFirstTime() {
        CatalogIndexPlan plan = CatalogIndexPlan.of(CATALOG, Map.of());

        assertThat(plan.toIndex()).containsExactly(NEW, SAME, OTHER_MODEL);
        assertThat(plan.toRemove()).isEmpty();
        assertThat(plan.kept()).isZero();
    }

    @Test
    void hasNothingToDoWhenTheIndexIsCurrent() {
        CatalogIndexPlan plan = CatalogIndexPlan.of(CATALOG, Map.of(
                NEW.id(), "current-model", SAME.id(), "current-model", OTHER_MODEL.id(), "current-model"));

        assertThat(plan.toIndex()).isEmpty();
        assertThat(plan.toRemove()).isEmpty();
        assertThat(plan.kept()).isEqualTo(3);
    }
}
