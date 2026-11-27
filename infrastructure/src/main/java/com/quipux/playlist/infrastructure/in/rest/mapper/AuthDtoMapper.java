package com.quipux.playlist.infrastructure.in.rest.mapper;

import com.quipux.playlist.domain.model.AccessToken;
import com.quipux.playlist.domain.model.Credentials;
import com.quipux.playlist.infrastructure.in.rest.dto.LoginRequestDto;
import com.quipux.playlist.infrastructure.in.rest.dto.TokenResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuthDtoMapper {

    Credentials toDomain(LoginRequestDto dto);

    @Mapping(target = "accessToken", source = "token.value")
    @Mapping(target = "expiresIn", source = "token.expiresInSeconds")
    @Mapping(target = "tokenType", source = "tokenType")
    TokenResponseDto toDto(AccessToken token, String tokenType);
}
