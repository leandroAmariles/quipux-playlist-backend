package com.quipux.playlist.application.port.in;

import com.quipux.playlist.domain.model.Playlist;
import reactor.core.publisher.Flux;

public interface GetPlaylistsPort {

    Flux<Playlist> getAll();
}
