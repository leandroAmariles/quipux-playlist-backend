package com.quipux.playlist.config;

import com.quipux.playlist.application.port.in.CreatePlaylistPort;
import com.quipux.playlist.application.port.in.DeletePlaylistPort;
import com.quipux.playlist.application.port.in.GetGenresPort;
import com.quipux.playlist.application.port.in.GetPlaylistPort;
import com.quipux.playlist.application.port.in.GetPlaylistsPort;
import com.quipux.playlist.application.port.in.GetRecommendationsPort;
import com.quipux.playlist.application.port.in.LoginPort;
import com.quipux.playlist.application.port.in.RegisterDemoUsersPort;
import com.quipux.playlist.application.port.out.DefaultRecommendationsPort;
import com.quipux.playlist.application.port.out.GenreProviderPort;
import com.quipux.playlist.application.port.out.PasswordHasherPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.application.port.out.RecommendationCachePort;
import com.quipux.playlist.application.port.out.RecommendationProviderPort;
import com.quipux.playlist.application.port.out.RequestQuotaPort;
import com.quipux.playlist.application.port.out.SongCatalogPort;
import com.quipux.playlist.application.port.out.TokenIssuerPort;
import com.quipux.playlist.application.port.out.UserRepositoryPort;
import com.quipux.playlist.application.usecase.CreatePlaylistUseCase;
import com.quipux.playlist.application.usecase.DeletePlaylistUseCase;
import com.quipux.playlist.application.usecase.GetGenresUseCase;
import com.quipux.playlist.application.usecase.GetPlaylistUseCase;
import com.quipux.playlist.application.usecase.GetPlaylistsUseCase;
import com.quipux.playlist.application.usecase.GetRecommendationsUseCase;
import com.quipux.playlist.application.usecase.LoginUseCase;
import com.quipux.playlist.application.usecase.RegisterDemoUsersUseCase;
import com.quipux.playlist.domain.model.constants.RecommendationConstraints;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    CreatePlaylistPort createPlaylistPort(PlaylistRepositoryPort repository, GetGenresPort getGenres) {
        return new CreatePlaylistUseCase(repository, getGenres);
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

    @Bean
    LoginPort loginPort(
            UserRepositoryPort repository, PasswordHasherPort passwordHasher, TokenIssuerPort tokenIssuer) {
        return new LoginUseCase(repository, passwordHasher, tokenIssuer);
    }

    @Bean
    RegisterDemoUsersPort registerDemoUsersPort(UserRepositoryPort repository, PasswordHasherPort passwordHasher) {
        return new RegisterDemoUsersUseCase(repository, passwordHasher);
    }

    @Bean
    GetRecommendationsPort getRecommendationsPort(
            PlaylistRepositoryPort repository,
            RecommendationProviderPort provider,
            SongCatalogPort catalog,
            DefaultRecommendationsPort defaultRecommendations,
            RecommendationCachePort cache,
            RequestQuotaPort quota,
            @Value("${playlist.catalog.candidates:" + RecommendationConstraints.MAX_CANDIDATES + "}")
            int maxCandidates,
            @Value("${playlist.ai.timeout:30s}") Duration timeout) {
        return new GetRecommendationsUseCase(
                repository, provider, catalog, defaultRecommendations, cache, quota, maxCandidates, timeout);
    }

    @Bean
    GetGenresPort getGenresPort(
            @Qualifier("spotifyGenreProvider") GenreProviderPort spotify,
            @Qualifier("localGenreProvider") GenreProviderPort local) {
        return new GetGenresUseCase(spotify, local);
    }
}
