package com.quipux.playlist.infrastructure.out.genre;

import com.quipux.playlist.application.port.out.GenreProviderPort;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.GenreSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import reactor.core.publisher.Mono;

public class LocalGenreProviderAdapter implements GenreProviderPort {

    private final List<String> genres;

    public LocalGenreProviderAdapter(Resource file) {
        this.genres = load(file);
    }

    /**
     * The same list as a catalog, for what needs it while the application starts.
     */
    public GenreCatalog catalog() {
        return new GenreCatalog(GenreSource.LOCAL, genres);
    }

    @Override
    public Mono<List<String>> genres() {
        return Mono.just(genres);
    }

    private static List<String> load(Resource file) {
        try {
            Properties properties =
                    PropertiesLoaderUtils.loadProperties(new EncodedResource(file, StandardCharsets.UTF_8));
            List<String> genres = properties.stringPropertyNames().stream()
                    .sorted()
                    .map(properties::getProperty)
                    .toList();
            if (new GenreCatalog(GenreSource.LOCAL, genres).isEmpty()) {
                throw new IllegalStateException(messageFor(file));
            }
            return genres;
        } catch (IOException exception) {
            throw new IllegalStateException(messageFor(file), exception);
        }
    }

    private static String messageFor(Resource file) {
        return ErrorMessageEnum.LOCAL_GENRES_EMPTY.withCode(file.getDescription());
    }
}
