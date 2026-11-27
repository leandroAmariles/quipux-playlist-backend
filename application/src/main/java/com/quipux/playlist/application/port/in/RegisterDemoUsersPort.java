package com.quipux.playlist.application.port.in;

import com.quipux.playlist.domain.model.enums.DemoUser;
import java.util.Map;
import reactor.core.publisher.Mono;

public interface RegisterDemoUsersPort {

    Mono<Void> register(Map<DemoUser, String> passwords);
}
