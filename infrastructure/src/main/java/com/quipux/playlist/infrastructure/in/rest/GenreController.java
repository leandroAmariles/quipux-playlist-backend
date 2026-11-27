package com.quipux.playlist.infrastructure.in.rest;

import com.quipux.playlist.application.port.in.GetGenresPort;
import com.quipux.playlist.infrastructure.in.rest.dto.GenresResponseDto;
import com.quipux.playlist.infrastructure.in.rest.mapper.GenreDtoMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class GenreController {

    public static final String PATH = "/genres";

    private final GetGenresPort getGenres;
    private final GenreDtoMapper mapper;

    public GenreController(GetGenresPort getGenres, GenreDtoMapper mapper) {
        this.getGenres = getGenres;
        this.mapper = mapper;
    }

    @GetMapping(PATH)
    public Mono<GenresResponseDto> getGenres() {
        return getGenres.getGenres().map(mapper::toDto);
    }
}
