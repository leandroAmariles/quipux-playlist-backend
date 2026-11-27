package com.quipux.playlist.application.port.out;

import com.quipux.playlist.domain.model.AccessToken;
import com.quipux.playlist.domain.model.User;
import reactor.core.publisher.Mono;

public interface TokenIssuerPort {

    Mono<AccessToken> issue(User user);
}
