package com.quipux.playlist.application.port.in;

import com.quipux.playlist.domain.model.AccessToken;
import com.quipux.playlist.domain.model.Credentials;
import reactor.core.publisher.Mono;

public interface LoginPort {

    Mono<AccessToken> login(Credentials credentials);
}
