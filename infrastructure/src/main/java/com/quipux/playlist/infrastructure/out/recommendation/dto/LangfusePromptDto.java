package com.quipux.playlist.infrastructure.out.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LangfusePromptDto(Integer version, String prompt) {
}
