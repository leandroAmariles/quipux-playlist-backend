package com.quipux.playlist.infrastructure.out.recommendation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import tools.jackson.databind.json.JsonMapper;

/**
 * The versioned prompt. What precedes its data block are the instructions; the block carries the songs as JSON data.
 */
public class PromptTemplate {

    private static final String DATA_BLOCK = "<lista>{{lista}}</lista>";
    private static final String DATA_OPENING = "<lista>";
    private static final String DATA_CLOSING = "</lista>";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String instructions;

    public PromptTemplate(Resource file) {
        String text = read(file).strip();
        if (!text.endsWith(DATA_BLOCK) || text.length() == DATA_BLOCK.length()) {
            throw new IllegalStateException(messageFor(file));
        }
        this.instructions = text.substring(0, text.length() - DATA_BLOCK.length()).strip();
    }

    public Messages render(List<Song> songs) {
        // Without angle brackets no field can close the block that marks it as data.
        String data = mapper.writeValueAsString(songs.stream().map(SongData::of).toList())
                .replace("<", "\\u003c")
                .replace(">", "\\u003e");
        return new Messages(instructions, DATA_OPENING + data + DATA_CLOSING);
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
}
