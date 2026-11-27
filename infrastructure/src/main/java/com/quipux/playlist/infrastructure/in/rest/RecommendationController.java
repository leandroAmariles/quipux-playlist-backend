package com.quipux.playlist.infrastructure.in.rest;

import com.quipux.playlist.application.port.in.GetRecommendationsPort;
import com.quipux.playlist.infrastructure.in.rest.dto.RecommendationsResponseDto;
import com.quipux.playlist.infrastructure.in.rest.mapper.RecommendationDtoMapper;
import java.util.Objects;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class RecommendationController {

    public static final String PATH = PlaylistController.PLAYLIST_PATH + "/recommendations";

    private final GetRecommendationsPort getRecommendations;
    private final RecommendationDtoMapper mapper;

    public RecommendationController(GetRecommendationsPort getRecommendations, RecommendationDtoMapper mapper) {
        this.getRecommendations = getRecommendations;
        this.mapper = mapper;
    }

    @GetMapping(PATH)
    public Mono<RecommendationsResponseDto> getRecommendations(
            @PathVariable("listName") String listName, @AuthenticationPrincipal Jwt token) {
        return getRecommendations.recommend(Objects.toString(token.getSubject(), ""), listName)
                .map(mapper::toDto);
    }
}
