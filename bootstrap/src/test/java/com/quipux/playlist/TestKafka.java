package com.quipux.playlist;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** The Kafka broker of the integration tests. */
final class TestKafka {

    private static final String IMAGE = "apache/kafka-native:4.0.0";
    private static final int STARTUP_ATTEMPTS = 3;

    private TestKafka() {
    }

    static KafkaContainer container() {
        // The native binary of the image sometimes crashes (segfault) while it starts: another container does start.
        return new KafkaContainer(DockerImageName.parse(IMAGE)).withStartupAttempts(STARTUP_ATTEMPTS);
    }
}
