package com.quipux.playlist.application.port.in;

import com.quipux.playlist.domain.model.Playlist;
import reactor.core.publisher.Mono;

public interface GetPlaylistPort {

    Mono<Playlist> getByName(String name);
}
