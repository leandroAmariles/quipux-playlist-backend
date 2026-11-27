package com.quipux.playlist.infrastructure.in.rest;

import com.quipux.playlist.application.port.in.CreatePlaylistPort;
import com.quipux.playlist.application.port.in.DeletePlaylistPort;
import com.quipux.playlist.application.port.in.GetPlaylistPort;
import com.quipux.playlist.application.port.in.GetPlaylistsPort;
import com.quipux.playlist.infrastructure.in.rest.dto.PlaylistDto;
import com.quipux.playlist.infrastructure.in.rest.mapper.PlaylistDtoMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(PlaylistController.PATH)
public class PlaylistController {

    public static final String PATH = "/lists";
    private static final String PATH_SEPARATOR = "/";
    private static final String ITEM_PATH = PATH_SEPARATOR + "{listName}";
    public static final String PLAYLIST_PATH = PATH + ITEM_PATH;

    private final CreatePlaylistPort createPlaylist;
    private final GetPlaylistsPort getPlaylists;
    private final GetPlaylistPort getPlaylist;
    private final DeletePlaylistPort deletePlaylist;
    private final PlaylistDtoMapper mapper;

    public PlaylistController(
            CreatePlaylistPort createPlaylist,
            GetPlaylistsPort getPlaylists,
            GetPlaylistPort getPlaylist,
            DeletePlaylistPort deletePlaylist,
            PlaylistDtoMapper mapper) {
        this.createPlaylist = createPlaylist;
        this.getPlaylists = getPlaylists;
        this.getPlaylist = getPlaylist;
        this.deletePlaylist = deletePlaylist;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PlaylistDto>> create(@RequestBody Mono<PlaylistDto> request) {
        return request
                .map(mapper::toDomain)
                .flatMap(createPlaylist::create)
                .map(playlist -> ResponseEntity.created(locationOf(playlist.name())).body(mapper.toDto(playlist)));
    }

    @GetMapping
    public Flux<PlaylistDto> getAll() {
        return getPlaylists.getAll().map(mapper::toDto);
    }

    @GetMapping(ITEM_PATH)
    public Mono<PlaylistDto> getByName(@PathVariable("listName") String listName) {
        return getPlaylist.getByName(listName).map(mapper::toDto);
    }

    @DeleteMapping(ITEM_PATH)
    public Mono<ResponseEntity<Void>> delete(@PathVariable("listName") String listName) {
        return deletePlaylist.delete(listName)
                .thenReturn(ResponseEntity.noContent().build());
    }

    private static URI locationOf(String name) {
        return URI.create(PATH + PATH_SEPARATOR + UriUtils.encode(name, StandardCharsets.UTF_8));
    }
}
