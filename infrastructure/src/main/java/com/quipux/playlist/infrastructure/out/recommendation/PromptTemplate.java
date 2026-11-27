package com.quipux.playlist.infrastructure.out.recommendation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import tools.jackson.databind.json.JsonMapper;

/**
 * The versioned prompt. What precedes its data blocks are the instructions; the blocks carry the songs of the playlist
 * and the candidates as JSON data.
 */
public class PromptTemplate {

    private static final String DATA_BLOCKS = "<lista>{{lista}}</lista>\n<candidatas>{{candidatas}}</candidatas>";
    private static final String SONGS = "lista";
    private static final String CANDIDATES = "candidatas";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String instructions;

    public PromptTemplate(Resource file) {
        String text = read(file).strip();
        if (!text.endsWith(DATA_BLOCKS) || text.length() == DATA_BLOCKS.length()) {
            throw new IllegalStateException(messageFor(file));
        }
        this.instructions = text.substring(0, text.length() - DATA_BLOCKS.length()).strip();
    }

    public Messages render(List<Song> songs, CandidateAliases candidates) {
        String offered = block(CANDIDATES, candidates.songs().entrySet().stream()
                .map(entry -> CandidateData.of(entry.getKey(), entry.getValue()))
                .toList());
        return new Messages(instructions, block(SONGS, songs.stream().map(SongData::of).toList()) + "\n" + offered);
    }

    // Without angle brackets no field can close the block that marks it as data.
    private String block(String name, List<?> entries) {
        String data = mapper.writeValueAsString(entries)
                .replace("<", "\\u003c")
                .replace(">", "\\u003e");
        return "<" + name + ">" + data + "</" + name + ">";
    }

    private static String read(Resource file) {
        try {
            return file.getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new IllegalStateException(messageFor(file), exception);
        }
    }

    private static String messageFor(Resource file) {
        return ErrorMessageEnum.PROMPT_UNAVAILABLE.withCode(file.getDescription());
    }

    public record Messages(String system, String user) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SongData(
            @JsonProperty("titulo") String title,
            @JsonProperty("artista") String artist,
            @JsonProperty("album") String album,
            @JsonProperty("anno") String releaseYear,
            @JsonProperty("genero") String genre) {

        private static SongData of(Song song) {
            return new SongData(song.title(), song.artist(), song.album(), song.releaseYear(), song.genre());
        }
    }

    private record CandidateData(
            @JsonProperty("idCatalogo") String id,
            @JsonProperty("titulo") String title,
            @JsonProperty("artista") String artist,
            @JsonProperty("genero") String genre,
            @JsonProperty("anno") String releaseYear) {

        private static CandidateData of(String alias, CatalogSong song) {
            return new CandidateData(alias, song.title(), song.artist(), song.genre(), song.releaseYear());
        }
    }
}
