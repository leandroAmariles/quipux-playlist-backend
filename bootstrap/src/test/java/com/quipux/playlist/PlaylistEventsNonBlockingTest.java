package com.quipux.playlist;

import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.blockhound.BlockHound;
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
        BlockHound.install();
    }

    @AfterAll
    void stopKafka() {
        if (KAFKA.isRunning()) {
            KAFKA.stop();
        }
    }

    @Test
    void publishesCreatedAndDeletedEventsWithoutBlocking() {
        StepVerifier.create(createAndDelete("non-blocking"))
                .expectNext(201, 204)
                .verifyComplete();
    }

    private Flux<Integer> createAndDelete(String name) {
        return Flux.concat(
                create(playlist(name, Map.of("titulo", "Uno", "genero", "rock"))),
                webClient.delete().uri("/lists/" + name)
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(response.statusCode().value())));
    }

    private static KafkaContainer startKafka() {
        KafkaContainer container = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.0.0"));
        container.start();
        return container;
    }
}
