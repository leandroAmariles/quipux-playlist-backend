package com.quipux.playlist.infrastructure.out.recommendation;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.infrastructure.out.recommendation.dto.LangfusePromptDto;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The prompt that Langfuse labels as {@code production}. The file of the repository takes its place whenever it cannot
 * be had: Langfuse never stops a recommendation. What is obtained, or the failure to obtain it, is kept for a while,
 * so a Langfuse that is down delays one request in each period.
 */
public class LangfusePromptSource {

    private static final Logger LOG = LoggerFactory.getLogger(LangfusePromptSource.class);
    private static final String PATH = "/api/public/v2/prompts/{name}";
    private static final String LABEL = "label";
    private static final String PRODUCTION = "production";
    private static final String REMOTE_VERSION = "%s-lf%d";

    private final Mono<ActivePrompt> active;

    public LangfusePromptSource(
            WebClient client, String name, ActivePrompt local, Duration timeout, Duration timeToLive) {
        this.active = Mono.defer(() -> client.get()
                        .uri(uri -> uri.path(PATH).queryParam(LABEL, PRODUCTION).build(name))
                        .retrieve()
                        .bodyToMono(LangfusePromptDto.class)
                        .timeout(timeout)
                        // Reading the template loads classes: kept away from the event loop.
                        .publishOn(Schedulers.boundedElastic())
                        .map(remote -> activeOf(remote, local.version()))
                        // Only the type of the failure: its text could carry the address or the answer.
                        .doOnError(error -> warn(error.getClass().getSimpleName()))
                        .onErrorReturn(local)
                        .defaultIfEmpty(local))
                .cache(timeToLive);
    }

    public Mono<ActivePrompt> active() {
        return active;
    }

    // A text without the data blocks of the format is rejected by the template.
    private static ActivePrompt activeOf(LangfusePromptDto remote, String localVersion) {
        PromptTemplate template = new PromptTemplate(
                new ByteArrayResource(remote.prompt().getBytes(StandardCharsets.UTF_8)));
        return new ActivePrompt(REMOTE_VERSION.formatted(localVersion, remote.version()), template);
    }

    private static void warn(String reason) {
        LOG.warn(ErrorMessageEnum.PROMPT_REMOTE_UNAVAILABLE.withCode(reason));
    }
}
