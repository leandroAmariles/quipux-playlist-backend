package com.quipux.playlist.infrastructure.out.persistence.mapper;

import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.infrastructure.out.persistence.entity.PlaylistEntity;
import com.quipux.playlist.infrastructure.out.persistence.entity.PlaylistSongRow;
import com.quipux.playlist.infrastructure.out.persistence.entity.SongEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PlaylistEntityMapper {

    @Mapping(target = "id", ignore = true)
    PlaylistEntity toEntity(Playlist playlist);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "playlistId", source = "playlistId")
    SongEntity toEntity(Song song, Long playlistId);

    Song toSong(PlaylistSongRow row);

    default Playlist toPlaylist(List<PlaylistSongRow> rows) {
        PlaylistSongRow first = rows.get(0);
        List<Song> songs = rows.stream()
                .filter(row -> row.songId() != null)
                .map(this::toSong)
                .toList();
        return new Playlist(first.name(), first.description(), songs);
    }
}
