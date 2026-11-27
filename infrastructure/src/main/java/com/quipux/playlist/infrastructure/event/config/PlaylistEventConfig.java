package com.quipux.playlist.infrastructure.event.config;

import com.quipux.playlist.application.port.out.PlaylistEventPublisherPort;
import com.quipux.playlist.infrastructure.event.PlaylistEventSettings;
import com.quipux.playlist.infrastructure.in.event.KafkaPlaylistAuditConsumer;
import com.quipux.playlist.infrastructure.out.event.KafkaPlaylistEventPublisherAdapter;
import com.quipux.playlist.infrastructure.out.event.NoOpPlaylistEventPublisherAdapter;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class PlaylistEventConfig {

    @Bean
    @ConditionalOnProperty(prefix = "playlist.events", name = "enabled", havingValue = "false")
    PlaylistEventPublisherPort noOpPlaylistEventPublisherPort() {
        return new NoOpPlaylistEventPublisherAdapter();
    }

    @Configuration
    @EnableKafka
    @ConditionalOnProperty(prefix = "playlist.events", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class KafkaEnabledConfiguration {

        @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
        private String bootstrapServers;

        @Value("${playlist.events.topic:playlist.events}")
        private String topic;

        @Value("${playlist.events.consumer-group:playlist-audit}")
        private String consumerGroup;

        @Value("${playlist.events.publish-timeout:2s}")
        private Duration publishTimeout;

        @Bean
        PlaylistEventSettings playlistEventSettings() {
            return new PlaylistEventSettings(topic, consumerGroup, publishTimeout);
        }

        @Bean
        ProducerFactory<String, String> playlistEventProducerFactory(PlaylistEventSettings settings) {
            int timeoutMs = Math.toIntExact(settings.publishTimeout().toMillis());
            Map<String, Object> properties = new HashMap<>();
            properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, timeoutMs);
            properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs);
            properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, timeoutMs);
            properties.put(ProducerConfig.LINGER_MS_CONFIG, 0);
            properties.put(ProducerConfig.RETRIES_CONFIG, 0);
            properties.put(ProducerConfig.ACKS_CONFIG, "1");
            return new DefaultKafkaProducerFactory<>(properties);
        }

        @Bean
        KafkaTemplate<String, String> playlistEventKafkaTemplate(
                ProducerFactory<String, String> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }

        @Bean
        PlaylistEventPublisherPort kafkaPlaylistEventPublisherPort(
                KafkaTemplate<String, String> kafkaTemplate,
                ObjectMapper objectMapper,
                PlaylistEventSettings settings) {
            return new KafkaPlaylistEventPublisherAdapter(kafkaTemplate, objectMapper, settings);
        }

        @Bean
        ConsumerFactory<String, String> playlistAuditConsumerFactory(PlaylistEventSettings settings) {
            Map<String, Object> properties = new HashMap<>();
            properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, settings.consumerGroup());
            properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            return new DefaultKafkaConsumerFactory<>(properties);
        }

        @Bean(name = "playlistAuditListenerContainerFactory")
        ConcurrentKafkaListenerContainerFactory<String, String> playlistAuditListenerContainerFactory(
                ConsumerFactory<String, String> consumerFactory) {
            ConcurrentKafkaListenerContainerFactory<String, String> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory);
            factory.getContainerProperties().setMissingTopicsFatal(false);
            factory.setAutoStartup(true);
            return factory;
        }

        @Bean
        KafkaPlaylistAuditConsumer kafkaPlaylistAuditConsumer(ObjectMapper objectMapper) {
            return new KafkaPlaylistAuditConsumer(objectMapper);
        }
    }
}
