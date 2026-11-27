package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import io.r2dbc.spi.ConnectionFactory;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.context.reactive.ReactiveWebApplicationContext;
import org.springframework.context.ApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaylistApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void startsAsReactiveWebApplication() {
        assertThat(context).isInstanceOf(ReactiveWebApplicationContext.class);
    }

    // The one exception is the catalog of songs, with a database of its own that nothing else receives (ADR-0002).
    @Test
    void accessesUsersAndPlaylistsThroughR2dbcOnly() {
        assertThat(context.getBeanNamesForType(ConnectionFactory.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(DataSource.class)).containsExactly("catalogDataSource");
    }
}
