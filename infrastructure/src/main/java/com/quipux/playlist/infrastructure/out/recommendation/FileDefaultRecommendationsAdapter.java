package com.quipux.playlist.infrastructure.out.recommendation;

import com.quipux.playlist.application.port.out.DefaultRecommendationsPort;
import com.quipux.playlist.domain.model.DefaultRecommendations;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import reactor.core.publisher.Mono;

/**
 * Default recommendations from a properties file, read and validated once when the application starts.
 */
public class FileDefaultRecommendationsAdapter implements DefaultRecommendationsPort {

    private static final Pattern KEY =
            Pattern.compile("recommendation\\.(\\d{1,4})\\.(titulo|artista|genero|motivo)");

    private final DefaultRecommendations defaults;

    public FileDefaultRecommendationsAdapter(Resource file, GenreCatalog allowedGenres) {
        this.defaults = new DefaultRecommendations(entriesOf(load(file), file), allowedGenres);
    }

    @Override
    public Mono<DefaultRecommendations> defaults() {
        return Mono.just(defaults);
    }

    private static Properties load(Resource file) {
        try {
            return PropertiesLoaderUtils.loadProperties(new EncodedResource(file, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException(messageFor(file), exception);
        }
    }

    private static List<Recommendation> entriesOf(Properties properties, Resource file) {
        Map<Integer, Map<String, String>> entries = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            Matcher matcher = KEY.matcher(key);
            if (!matcher.matches()) {
                throw new IllegalStateException(messageFor(file));
            }
            entries.computeIfAbsent(Integer.valueOf(matcher.group(1)), number -> new TreeMap<>())
                    .put(matcher.group(2), properties.getProperty(key));
        }
        return entries.values().stream()
                .map(fields -> new Recommendation(
                        fields.get("titulo"), fields.get("artista"), fields.get("genero"), fields.get("motivo")))
                .toList();
    }

    private static String messageFor(Resource file) {
        return ErrorMessageEnum.DEFAULT_RECOMMENDATIONS_INVALID.withCode(file.getDescription());
    }
}
