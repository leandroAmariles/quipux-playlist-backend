package com.quipux.playlist.infrastructure.out.persistence.mapper;

import com.quipux.playlist.domain.model.User;
import com.quipux.playlist.infrastructure.out.persistence.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserEntityMapper {

    UserEntity toEntity(User user, Long id);

    User toDomain(UserEntity entity);
}
