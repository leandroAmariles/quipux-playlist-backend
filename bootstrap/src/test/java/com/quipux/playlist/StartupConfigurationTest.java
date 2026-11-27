package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class StartupConfigurationTest {

    private static final String LONG_PASSWORD = "long-fake-password-".repeat(4);

    @ParameterizedTest(name = "{0} = \"{1}\"")
    @MethodSource("invalidSettings")
    void refusesToStartNamingTheVariableAndHidingItsValue(
            String variable, String value, String property, CapturedOutput output) {
        String[] arguments = {
                "--server.port=0",
                "--spring.r2dbc.url=r2dbc:h2:mem:///startup-test?options=DB_CLOSE_DELAY=-1",
                "--" + property + "=" + value};

        assertThatThrownBy(() -> SpringApplication.run(PlaylistApplication.class, arguments).close())
                .hasStackTraceContaining(variable);
        if (value.length() > 10) {
            assertThat(output).doesNotContain(value);
        }
    }

    @Test
    void refusesToStartWithoutLocalGenres() {
        String[] arguments = {
                "--server.port=0",
                "--spring.r2dbc.url=r2dbc:h2:mem:///startup-genres-test?options=DB_CLOSE_DELAY=-1",
                "--playlist.genres.local-file=classpath:empty-genres.properties"};

        assertThatThrownBy(() -> SpringApplication.run(PlaylistApplication.class, arguments).close())
                .hasStackTraceContaining("GENRE_ERROR_002");
    }

    @ParameterizedTest(name = "{0} = \"{1}\"")
    @MethodSource("invalidCacheSettings")
    void refusesToStartWithInvalidGenreCacheSettings(String property, String value) {
        String[] arguments = {
                "--server.port=0",
                "--spring.r2dbc.url=r2dbc:h2:mem:///startup-cache-test?options=DB_CLOSE_DELAY=-1",
                "--playlist.genres.cache.enabled=true",
                "--" + property + "=" + value};

        assertThatThrownBy(() -> SpringApplication.run(PlaylistApplication.class, arguments).close())
                .hasStackTraceContaining("GENRE_ERROR_002");
    }

    private static Stream<Arguments> invalidCacheSettings() {
        return Stream.of(
                Arguments.of("playlist.genres.cache.ttl", "0s"),
                Arguments.of("playlist.genres.cache.ttl", "-1s"),
                Arguments.of("playlist.genres.cache.timeout", "0s"),
                Arguments.of("playlist.genres.cache.key", "   "));
    }

    private static Stream<Arguments> invalidSettings() {
        String secret = "playlist.security.jwt.secret";
        String userPassword = "playlist.security.demo-users.user-password";
        String adminPassword = "playlist.security.demo-users.admin-password";
        return Stream.of(
                Arguments.of("JWT_SECRET", "", secret),
                Arguments.of("JWT_SECRET", "short-fake-signing-key", secret),
                Arguments.of("DEMO_USER_PASSWORD", "", userPassword),
                Arguments.of("DEMO_USER_PASSWORD", "   ", userPassword),
                Arguments.of("DEMO_USER_PASSWORD", LONG_PASSWORD, userPassword),
                Arguments.of("DEMO_ADMIN_PASSWORD", "", adminPassword),
                Arguments.of("DEMO_ADMIN_PASSWORD", LONG_PASSWORD, adminPassword),
                Arguments.of("JWT_TTL", "0s", "playlist.security.jwt.ttl"));
    }
}
