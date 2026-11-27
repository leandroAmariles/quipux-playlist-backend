package com.quipux.playlist.infrastructure.in.rest.mapper;

import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.infrastructure.in.rest.dto.GenresResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface GenreDtoMapper {

    GenresResponseDto toDto(GenreCatalog catalog);
}
