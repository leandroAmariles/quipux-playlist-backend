package com.quipux.playlist.infrastructure.in.rest.mapper;

import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.RecommendationSet;
import com.quipux.playlist.domain.model.enums.RecommendationOrigin;
import com.quipux.playlist.infrastructure.in.rest.dto.RecommendationDto;
import com.quipux.playlist.infrastructure.in.rest.dto.RecommendationsResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecommendationDtoMapper {

    RecommendationsResponseDto toDto(RecommendationSet recommendations);

    // There is no catalog yet, so no suggestion is verified against one.
    @Mapping(target = "catalogId", constant = "")
    RecommendationDto toDto(Recommendation recommendation);

    default String originOf(RecommendationOrigin origin) {
        return origin.externalValue();
    }
}
