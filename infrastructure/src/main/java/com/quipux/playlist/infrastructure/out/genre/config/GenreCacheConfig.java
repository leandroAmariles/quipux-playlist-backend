package com.quipux.playlist.infrastructure.out.genre.config;

import com.quipux.playlist.application.port.out.GenreCachePort;
import com.quipux.playlist.infrastructure.out.genre.GenreCacheSettings;
import com.quipux.playlist.infrastructure.out.genre.NoOpGenreCacheAdapter;
import com.quipux.playlist.infrastructure.out.genre.RedisGenreCacheAdapter;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class GenreCacheConfig {

    @Value("${playlist.genres.cache.enabled:true}")
    private boolean enabled;

    @Value("${playlist.genres.cache.key:playlist:genres:spotify}")
    private String key;

    @Value("${playlist.genres.cache.ttl:24h}")
    private Duration ttl;

    @Value("${playlist.genres.cache.timeout:1s}")
    private Duration timeout;

    @Bean
    GenreCachePort genreCachePort(
            ObjectProvider<ReactiveStringRedisTemplate> redisTemplate,
            ObjectMapper objectMapper) {
        if (!enabled) {
            return new NoOpGenreCacheAdapter();
        }
        GenreCacheSettings settings = new GenreCacheSettings(key, ttl, timeout);
        return new RedisGenreCacheAdapter(redisTemplate.getObject(), objectMapper, settings);
    }
}
