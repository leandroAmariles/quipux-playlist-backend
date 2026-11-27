package com.quipux.playlist.config;

import com.quipux.playlist.application.port.in.CreatePlaylistPort;
import com.quipux.playlist.application.port.in.DeletePlaylistPort;
import com.quipux.playlist.application.port.in.GetPlaylistPort;
import com.quipux.playlist.application.port.in.GetPlaylistsPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.application.usecase.CreatePlaylistUseCase;
import com.quipux.playlist.application.usecase.DeletePlaylistUseCase;
import com.quipux.playlist.application.usecase.GetPlaylistUseCase;
import com.quipux.playlist.application.usecase.GetPlaylistsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    CreatePlaylistPort createPlaylistPort(PlaylistRepositoryPort repository) {
        return new CreatePlaylistUseCase(repository);
    }

    @Bean
    GetPlaylistsPort getPlaylistsPort(PlaylistRepositoryPort repository) {
        return new GetPlaylistsUseCase(repository);
    }

    @Bean
    GetPlaylistPort getPlaylistPort(PlaylistRepositoryPort repository) {
        return new GetPlaylistUseCase(repository);
    }

    @Bean
    DeletePlaylistPort deletePlaylistPort(PlaylistRepositoryPort repository) {
        return new DeletePlaylistUseCase(repository);
    }
}
