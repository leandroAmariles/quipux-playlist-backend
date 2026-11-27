package com.quipux.playlist.infrastructure.in.rest.security;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.infrastructure.in.rest.handler.ProblemDetails;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.firewall.ServerExchangeRejectedException;
import org.springframework.security.web.server.firewall.ServerExchangeRejectedHandler;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class SecurityErrorHandlers
        implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler, ServerExchangeRejectedHandler {

    private static final String CHALLENGE = "Bearer";

    private final ServerResponse.Context responseContext;

    public SecurityErrorHandlers(ServerCodecConfigurer codecs) {
        this.responseContext = new ServerResponse.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return codecs.getWriters();
            }

            @Override
            public List<ViewResolver> viewResolvers() {
                return List.of();
            }
        };
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE);
        return write(exchange, ErrorMessageEnum.AUTHENTICATION_REQUIRED);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException exception) {
        return write(exchange, ErrorMessageEnum.ACCESS_DENIED);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, ServerExchangeRejectedException exception) {
        return write(exchange, ErrorMessageEnum.PATH_NOT_ALLOWED);
    }

    private Mono<Void> write(ServerWebExchange exchange, ErrorMessageEnum error) {
        ProblemDetail detail = ProblemDetails.of(error.responseCode(), error.format());
        return ServerResponse.status(detail.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(detail)
                .flatMap(response -> response.writeTo(exchange, responseContext));
    }
}
