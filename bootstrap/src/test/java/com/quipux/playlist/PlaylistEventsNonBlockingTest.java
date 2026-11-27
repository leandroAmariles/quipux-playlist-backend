package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.quipux.playlist.infrastructure.in.event.KafkaPlaylistAuditConsumer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaylistEventsNonBlockingTest extends ApiTestSupport {

    private static final String TOPIC = "playlist.events.nb";
    private static final KafkaContainer KAFKA = startKafka();

    @DynamicPropertySource
    static void configureKafka(DynamicPropertyRegistry registry) {
        registry.add("playlist.events.enabled", () -> "true");
        registry.add("playlist.events.topic", () -> TOPIC);
        registry.add("playlist.events.consumer-group", () -> "playlist-audit-nb");
        registry.add("playlist.events.publish-timeout", () -> "2s");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.r2dbc.url", () -> "r2dbc:h2:mem:///playlist-events-nb?options=DB_CLOSE_DELAY=-1");
    }

    @BeforeAll
    void installBlockHoundAfterWarmingUp() throws Exception {
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        } catch (java.util.concurrent.ExecutionException exception) {
            if (!(exception.getCause() instanceof TopicExistsException)) {
                throw exception;
            }
        }
        createClient();
        StepVerifier.create(createAndDelete("warm-up"))
                .expectNext(201, 204)
                .verifyComplete();
        installBlockHound();
    }

    @AfterAll
    void stopKafka() {
        if (KAFKA.isRunning()) {
            KAFKA.stop();
        }
    }

    /**
     * Publishing is best effort: a blocking call detected by BlockHound is swallowed and the request still succeeds.
     * Only the arrival of both events at the audit consumer shows that the publication ran without blocking.
     */
    @Test
    void publishesCreatedAndDeletedEventsWithoutBlocking() throws InterruptedException {
        Logger auditLogger = (Logger) LoggerFactory.getLogger(KafkaPlaylistAuditConsumer.class);
        ListAppender<ILoggingEvent> auditAppender = new ListAppender<>();
        auditAppender.list = new CopyOnWriteArrayList<>();
        auditAppender.start();
        auditLogger.addAppender(auditAppender);
        try {
            StepVerifier.create(createAndDelete("non-blocking"))
                    .expectNext(201, 204)
                    .verifyComplete();

            awaitAuditEntries(auditAppender, "playlistName=non-blocking ", 2, Duration.ofSeconds(15));
            assertThat(auditAppender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .filteredOn(message -> message.contains("playlistName=non-blocking "))
                    .satisfiesExactly(
                            created -> assertThat(created).contains("eventType=PlaylistCreated"),
                            deleted -> assertThat(deleted).contains("eventType=PlaylistDeleted"));
        } finally {
            auditLogger.detachAppender(auditAppender);
            auditAppender.stop();
        }
    }

    private static void awaitAuditEntries(
            ListAppender<ILoggingEvent> appender, String fragment, int expectedCount, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline
                && appender.list.stream()
                        .filter(event -> event.getFormattedMessage().contains(fragment))
                        .count() < expectedCount) {
            Thread.sleep(100);
        }
    }

    private Flux<Integer> createAndDelete(String name) {
        return Flux.concat(
                create(playlist(name, Map.of("titulo", "Uno", "genero", "rock"))),
                webClient.delete().uri("/lists/" + name)
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(response.statusCode().value())));
    }

    private static KafkaContainer startKafka() {
        KafkaContainer container = TestKafka.container();
        container.start();
        return container;
    }
}
