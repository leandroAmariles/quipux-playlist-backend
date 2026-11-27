package com.quipux.playlist.infrastructure.in.rest.mapper;

import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.infrastructure.in.rest.dto.PlaylistDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PlaylistDtoMapper {

    Playlist toDomain(PlaylistDto dto);

    PlaylistDto toDto(Playlist playlist);
}
