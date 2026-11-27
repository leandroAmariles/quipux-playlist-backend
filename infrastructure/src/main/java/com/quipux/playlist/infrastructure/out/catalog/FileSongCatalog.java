package com.quipux.playlist.infrastructure.out.catalog;

import com.quipux.playlist.domain.model.CatalogRules;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The catalog from a JSON file, read and validated once when the application starts.
 */
public class FileSongCatalog {

    private final SongCatalog catalog;

    public FileSongCatalog(Resource file, GenreCatalog allowedGenres, String embeddingModel) {
        List<CatalogSong> songs = songsOf(read(file), file);
        CatalogRules.requireValid(songs, allowedGenres);
        this.catalog = new SongCatalog(songs, embeddingModel);
    }

    public SongCatalog catalog() {
        return catalog;
    }

    private static String read(Resource file) {
        try {
            return file.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(messageFor(file), exception);
        }
    }

    private static List<CatalogSong> songsOf(String content, Resource file) {
        JsonNode entries = parse(content, file);
        if (!entries.isArray() || !entries.valueStream().allMatch(JsonNode::isObject)) {
            throw new IllegalStateException(messageFor(file));
        }
        return entries.valueStream()
                .map(entry -> CatalogSong.of(
                        textOf(entry, "titulo"), textOf(entry, "artista"), textOf(entry, "album"),
                        textOf(entry, "anno"), textOf(entry, "genero")))
                .toList();
    }

    private static JsonNode parse(String content, Resource file) {
        try {
            return JsonMapper.builder()
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build()
                    .readTree(content);
        } catch (JacksonException exception) {
            throw new IllegalStateException(messageFor(file), exception);
        }
    }

    private static String textOf(JsonNode entry, String field) {
        return entry.path(field).isString() ? entry.path(field).asString() : null;
    }

    private static String messageFor(Resource file) {
        return ErrorMessageEnum.CATALOG_INVALID.withCode(file.getDescription());
    }
}
