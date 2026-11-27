package com.quipux.playlist.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PlaylistEventSettingsTest {

    @Test
    void acceptsValidSettings() {
        PlaylistEventSettings settings = new PlaylistEventSettings(
                "playlist.events", "playlist-audit", Duration.ofSeconds(2));

        assertThat(settings.topic()).isEqualTo("playlist.events");
        assertThat(settings.consumerGroup()).isEqualTo("playlist-audit");
        assertThat(settings.publishTimeout()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void rejectsBlankTopicAndConsumerGroup() {
        assertThatThrownBy(() -> new PlaylistEventSettings(" ", "playlist-audit", Duration.ofSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EVENT_ERROR_002");
        assertThatThrownBy(() -> new PlaylistEventSettings("playlist.events", null, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EVENT_ERROR_002");
    }

    @Test
    void rejectsNonPositivePublishTimeout() {
        assertThatThrownBy(() -> new PlaylistEventSettings("playlist.events", "playlist-audit", Duration.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EVENT_ERROR_002");
        assertThatThrownBy(() -> new PlaylistEventSettings(
                "playlist.events", "playlist-audit", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EVENT_ERROR_002");
        assertThatThrownBy(() -> new PlaylistEventSettings("playlist.events", "playlist-audit", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EVENT_ERROR_002");
    }
}
