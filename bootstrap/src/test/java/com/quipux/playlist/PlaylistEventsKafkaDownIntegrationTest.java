package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.quipux.playlist.infrastructure.in.event.KafkaPlaylistAuditConsumer;
import com.quipux.playlist.infrastructure.in.rest.health.HealthPath;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlaylistEventsKafkaDownIntegrationTest extends ApiTestSupport {

    private static final String TOPIC = "playlist.events.down";
    private static final KafkaContainer KAFKA = startKafka();

    private final ListAppender<ILoggingEvent> auditAppender = new ListAppender<>();
    private Logger auditLogger;

    @DynamicPropertySource
    static void configureKafka(DynamicPropertyRegistry registry) {
        registry.add("playlist.events.enabled", () -> "true");
        registry.add("playlist.events.topic", () -> TOPIC);
        registry.add("playlist.events.consumer-group", () -> "playlist-audit-down");
        registry.add("playlist.events.publish-timeout", () -> "500ms");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.r2dbc.url", () -> "r2dbc:h2:mem:///playlist-events-down-it?options=DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void attachAuditAppender() {
        auditLogger = (Logger) LoggerFactory.getLogger(KafkaPlaylistAuditConsumer.class);
        auditAppender.start();
        auditLogger.addAppender(auditAppender);
    }

    @AfterEach
    void detachAuditAppender() {
        auditLogger.detachAppender(auditAppender);
        auditAppender.stop();
        auditAppender.list.clear();
    }

    @AfterAll
    static void stopKafkaIfRunning() {
        if (KAFKA.isRunning()) {
            KAFKA.stop();
        }
    }

    @Test
    @Order(1)
    void succeedsWhenKafkaIsPausedAndResumesAuditingWhenUnpaused() throws Exception {
        ensureTopicExists();

        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
        try {
            Duration createElapsed = StepVerifier.create(create(playlist("paused-kafka")))
                    .expectNext(201)
                    .verifyComplete();
            assertThat(createElapsed).isLessThan(Duration.ofSeconds(3));

            Duration deleteElapsed = StepVerifier.create(webClient.delete().uri("/lists/paused-kafka")
                            .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())))
                    .expectNext(204)
                    .verifyComplete();
            assertThat(deleteElapsed).isLessThan(Duration.ofSeconds(3));
        } finally {
            KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
        }

        auditAppender.list.clear();
        StepVerifier.create(create(playlist("recovered-kafka")))
                .expectNext(201)
                .verifyComplete();

        awaitAuditLog("recovered-kafka", Duration.ofSeconds(15));
        assertThat(auditAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage()).contains("PlaylistCreated", "recovered-kafka");
                });
    }

    @Test
    @Order(2)
    void keepsServingPlaylistsAndHealthProbesWhenKafkaIsStopped() {
        KAFKA.stop();

        Duration createElapsed = StepVerifier.create(create(playlist("stopped-kafka")))
                .expectNext(201)
                .verifyComplete();
        assertThat(createElapsed).isLessThan(Duration.ofSeconds(3));

        Duration deleteElapsed = StepVerifier.create(webClient.delete().uri("/lists/stopped-kafka")
                        .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())))
                .expectNext(204)
                .verifyComplete();
        assertThat(deleteElapsed).isLessThan(Duration.ofSeconds(3));

        for (String path : HealthPath.publicPatterns()) {
            StepVerifier.create(anonymousClient.get().uri(path).retrieve().bodyToMono(JsonNode.class))
                    .assertNext(body -> assertThat(body.path("status").asString()).isEqualTo("UP"))
                    .verifyComplete();
        }
    }

    private void awaitAuditLog(String playlistName, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            boolean found = auditAppender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains(playlistName));
            if (found) {
                return;
            }
            Thread.sleep(100);
        }
    }

    private static void ensureTopicExists() throws Exception {
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        } catch (java.util.concurrent.ExecutionException exception) {
            if (!(exception.getCause() instanceof TopicExistsException)) {
                throw exception;
            }
        }
    }

    private static KafkaContainer startKafka() {
        KafkaContainer container = TestKafka.container();
        container.start();
        return container;
    }
}
