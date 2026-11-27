package com.quipux.playlist.infrastructure.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.DockerClientFactory;

/**
 * The tests against pgvector are skipped where there is no Docker. On the CI server that would hide them.
 */
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class DockerRequiredOnCiTest {

    @Test
    void hasDockerOnTheCiServer() {
        assertThat(DockerClientFactory.instance().isDockerAvailable()).isTrue();
    }
}
