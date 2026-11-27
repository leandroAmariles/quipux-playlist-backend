package com.quipux.playlist.infrastructure.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

class KafkaPlaylistAuditConsumerTest {

    private final KafkaPlaylistAuditConsumer consumer = new KafkaPlaylistAuditConsumer(new ObjectMapper());
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(KafkaPlaylistAuditConsumer.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void logsAuditInfoForValidCreatedAndDeletedEvents() {
        consumer.onMessage("""
                {"eventId":"550e8400-e29b-41d4-a716-446655440000","eventType":"PlaylistCreated",\
                "playlistName":"Mis favoritas","songCount":2,"occurredAt":"2026-11-27T10:00:00Z"}""");
        consumer.onMessage("""
                {"eventId":"550e8400-e29b-41d4-a716-446655440001","eventType":"PlaylistDeleted",\
                "playlistName":"Mis favoritas","occurredAt":"2026-11-27T10:05:00Z"}""");

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("PlaylistCreated", "Mis favoritas", "songCount=2", "550e8400-e29b-41d4-a716-446655440000");
        assertThat(appender.list.get(1).getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.get(1).getFormattedMessage())
                .contains("PlaylistDeleted", "Mis favoritas", "550e8400-e29b-41d4-a716-446655440001");
    }

    @Test
    void warnsAndSkipsInvalidOrUnknownPayloadsWithoutThrowing() {
        consumer.onMessage(" ");
        consumer.onMessage("{not-json");
        consumer.onMessage("""
                {"eventId":"550e8400-e29b-41d4-a716-446655440000","eventType":"PlaylistUpdated",\
                "playlistName":"Rock","occurredAt":"2026-11-27T10:00:00Z"}""");
        consumer.onMessage("""
                {"eventId":"550e8400-e29b-41d4-a716-446655440000","eventType":"PlaylistCreated",\
                "playlistName":"Rock","occurredAt":"2026-11-27T10:00:00Z"}""");
        consumer.onMessage("""
                {"eventId":"not-a-uuid","eventType":"PlaylistDeleted",\
                "playlistName":"Rock","occurredAt":"2026-11-27T10:00:00Z"}""");

        assertThat(appender.list).hasSize(5);
        assertThat(appender.list).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("EVENT_ERROR_001");
        });
    }

    @Test
    void warnsAndSkipsAnUnparseableOccurredAtWithoutEchoingIt() {
        consumer.onMessage("""
                {"eventId":"550e8400-e29b-41d4-a716-446655440000","eventType":"PlaylistDeleted",\
                "playlistName":"Rock","occurredAt":"yesterday"}""");

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains("EVENT_ERROR_001", "invalid occurredAt")
                    .doesNotContain("yesterday");
        });
    }
}
