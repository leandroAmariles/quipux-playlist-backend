package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.infrastructure.in.event.KafkaPlaylistAuditConsumer;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class PlaylistEventsKafkaIntegrationTest extends ApiTestSupport {

    private static final String TOPIC = "playlist.events.it";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.0.0"));

    private final ListAppender<ILoggingEvent> auditAppender = new ListAppender<>();
    private Logger auditLogger;
    private WebClient userClient;

    @DynamicPropertySource
    static void configureKafka(DynamicPropertyRegistry registry) {
        registry.add("playlist.events.enabled", () -> "true");
        registry.add("playlist.events.topic", () -> TOPIC);
        registry.add("playlist.events.consumer-group", () -> "playlist-audit-it");
        registry.add("playlist.events.publish-timeout", () -> "5s");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.r2dbc.url", () -> "r2dbc:h2:mem:///playlist-events-it?options=DB_CLOSE_DELAY=-1");
    }

    @BeforeAll
    static void createTopic() throws Exception {
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        } catch (java.util.concurrent.ExecutionException exception) {
            if (!(exception.getCause() instanceof TopicExistsException)) {
                throw exception;
            }
        }
    }

    @BeforeEach
    void setupAuditCaptureAndUserClient() {
        auditLogger = (Logger) LoggerFactory.getLogger(KafkaPlaylistAuditConsumer.class);
        auditAppender.start();
        auditLogger.addAppender(auditAppender);
        StepVerifier.create(tokenOf(DemoUser.USER.username(), userPassword))
                .assertNext(token -> userClient = clientWith(token))
                .verifyComplete();
    }

    @AfterEach
    void detachAuditAppender() {
        auditLogger.detachAppender(auditAppender);
        auditAppender.stop();
        auditAppender.list.clear();
    }

    @Test
    void publishesAndAuditsCreatedAndDeletedEventsAfterCommitAndSkipsFailures() throws Exception {
        String name = "Música & Rock 1";
        String encodedName = UriUtils.encodePathSegment(name, java.nio.charset.StandardCharsets.UTF_8);

        try (KafkaConsumer<String, String> verifier = newVerifierConsumer()) {
            verifier.subscribe(List.of(TOPIC));
            drainExistingRecords(verifier);
            auditAppender.list.clear();

            StepVerifier.create(create(playlist(name, Map.of("titulo", "Uno", "genero", "rock"))))
                    .expectNext(201)
                    .verifyComplete();

            // Failed requests must not publish any events.
            StepVerifier.create(create(playlist(name))).expectNext(409).verifyComplete();
            StepVerifier.create(create(playlist(" "))).expectNext(400).verifyComplete();
            StepVerifier.create(create(playlist("bad-genre", Map.of("titulo", "X", "genero", "no-existe"))))
                    .expectNext(400)
                    .verifyComplete();
            StepVerifier.create(anonymousClient.post().uri("/lists")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(playlist("anon"))
                            .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())))
                    .expectNext(401)
                    .verifyComplete();
            StepVerifier.create(userClient.post().uri("/lists")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(playlist("forbidden"))
                            .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())))
                    .expectNext(403)
                    .verifyComplete();
            StepVerifier.create(webClient.get().uri("/lists")
                            .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())))
                    .expectNext(200)
                    .verifyComplete();
            StepVerifier.create(webClient.delete().uri("/lists/no-such-list")
                            .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())))
                    .expectNext(404)
                    .verifyComplete();

            StepVerifier.create(webClient.delete().uri(URI.create(baseUrl() + "/lists/" + encodedName))
                            .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())))
                    .expectNext(204)
                    .verifyComplete();

            StepVerifier.create(create(playlist(name)))
                    .expectNext(201)
                    .verifyComplete();

            List<ConsumerRecord<String, String>> records = pollRecords(verifier, 3, Duration.ofSeconds(15));
            assertThat(records).hasSize(3);

            JsonNode first = OBJECT_MAPPER.readTree(records.get(0).value());
            assertThat(records.get(0).key()).isEqualTo(name);
            assertThat(first.path("eventType").asString()).isEqualTo("PlaylistCreated");
            assertThat(first.path("playlistName").asString()).isEqualTo(name);
            assertThat(first.path("songCount").asInt()).isEqualTo(1);
            assertThat(first.path("eventId").asString()).isNotBlank();
            assertThat(first.path("occurredAt").asString()).isNotBlank();

            JsonNode second = OBJECT_MAPPER.readTree(records.get(1).value());
            assertThat(records.get(1).key()).isEqualTo(name);
            assertThat(second.path("eventType").asString()).isEqualTo("PlaylistDeleted");
            assertThat(second.path("playlistName").asString()).isEqualTo(name);
            assertThat(second.has("songCount")).isFalse();

            JsonNode third = OBJECT_MAPPER.readTree(records.get(2).value());
            assertThat(records.get(2).key()).isEqualTo(name);
            assertThat(third.path("eventType").asString()).isEqualTo("PlaylistCreated");
            assertThat(third.path("songCount").asInt()).isZero();
            assertThat(third.path("eventId").asString()).isNotEqualTo(first.path("eventId").asString());

            awaitAuditLogs(3, Duration.ofSeconds(15));
            assertThat(auditAppender.list).hasSize(3);
            assertThat(auditAppender.list).allSatisfy(entry -> assertThat(entry.getLevel()).isEqualTo(Level.INFO));
            assertThat(auditAppender.list.get(0).getFormattedMessage())
                    .contains("PlaylistCreated", name, "songCount=1");
            assertThat(auditAppender.list.get(1).getFormattedMessage())
                    .contains("PlaylistDeleted", name);
            assertThat(auditAppender.list.get(2).getFormattedMessage())
                    .contains("PlaylistCreated", name, "songCount=0");
        }
    }

    private static KafkaConsumer<String, String> newVerifierConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "verifier-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"));
    }

    private static void drainExistingRecords(KafkaConsumer<String, String> consumer) {
        consumer.poll(Duration.ofMillis(200));
    }

    private static List<ConsumerRecord<String, String>> pollRecords(
            KafkaConsumer<String, String> consumer, int expectedCount, Duration timeout) {
        List<ConsumerRecord<String, String>> collected = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (collected.size() < expectedCount && System.nanoTime() < deadline) {
            ConsumerRecords<String, String> batch = consumer.poll(Duration.ofMillis(250));
            batch.forEach(collected::add);
        }
        return collected;
    }

    private void awaitAuditLogs(int expectedCount, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (auditAppender.list.size() < expectedCount && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
    }
}
