package com.quipux.playlist.infrastructure.in.rest.doc;

import java.util.Arrays;
import java.util.List;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class DocumentationSchedulingFilter implements WebFilter {

    private final List<PathPattern> documentation = Arrays.stream(DocumentationPath.values())
            .map(path -> PathPatternParser.defaultInstance.parse(path.pattern()))
            .toList();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        PathContainer requested = exchange.getRequest().getPath().pathWithinApplication();
        Mono<Void> next = chain.filter(exchange);

        return documentation.stream().anyMatch(pattern -> pattern.matches(requested))
                ? next.subscribeOn(Schedulers.boundedElastic())
                : next;
    }
}
