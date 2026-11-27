package com.quipux.playlist.infrastructure.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("PLAYLIST")
public record PlaylistEntity(@Id Long id, String name, String description) {
}
