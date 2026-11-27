package com.quipux.playlist.infrastructure.in.rest.config;

import com.quipux.playlist.domain.model.enums.Role;
import com.quipux.playlist.infrastructure.in.rest.AuthController;
import com.quipux.playlist.infrastructure.in.rest.PlaylistController;
import com.quipux.playlist.infrastructure.in.rest.security.SecurityErrorHandlers;
import com.quipux.playlist.infrastructure.in.rest.security.TokenExpiryValidator;
import com.quipux.playlist.infrastructure.security.SigningKey;
import com.quipux.playlist.infrastructure.security.TokenClaim;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.firewall.ServerWebExchangeFirewall;
import org.springframework.security.web.server.firewall.StrictServerWebExchangeFirewall;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PLAYLIST_PATHS = {PlaylistController.PATH, PlaylistController.PATH + "/**"};
    private static final String AUTHORITY_PREFIX = "ROLE_";

    @Bean
    @Order(1)
    SecurityWebFilterChain loginSecurityFilterChain(ServerHttpSecurity http) {
        return stateless(http)
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, AuthController.LOGIN_PATH))
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityWebFilterChain apiSecurityFilterChain(ServerHttpSecurity http, SecurityErrorHandlers errorHandlers) {
        return stateless(http)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, PLAYLIST_PATHS)
                        .hasAnyRole(Role.USER.name(), Role.ADMIN.name())
                        .pathMatchers(HttpMethod.HEAD, PLAYLIST_PATHS)
                        .hasAnyRole(Role.USER.name(), Role.ADMIN.name())
                        .pathMatchers(PLAYLIST_PATHS)
                        .hasRole(Role.ADMIN.name())
                        .anyExchange()
                        .authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleAuthenticationConverter())))
                .build();
    }

    // As a bean it also replaces the handler of the requests rejected by the firewall.
    @Bean
    SecurityErrorHandlers securityErrorHandlers(ServerCodecConfigurer codecs) {
        return new SecurityErrorHandlers(codecs);
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(SigningKey signingKey, Clock clock) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(signingKey.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new TokenExpiryValidator(clock));
        return decoder;
    }

    @Bean
    ServerWebExchangeFirewall serverWebExchangeFirewall() {
        // Playlist names may contain ';' and '%'. Every other protection keeps its default.
        StrictServerWebExchangeFirewall firewall = new StrictServerWebExchangeFirewall();
        firewall.setAllowSemicolon(true);
        firewall.setAllowUrlEncodedPercent(true);
        return firewall;
    }

    private static ReactiveJwtAuthenticationConverterAdapter roleAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenClaim.ROLE.claimName());
        authorities.setAuthorityPrefix(AUTHORITY_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    private static ServerHttpSecurity stateless(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()));
    }
}
