package com.quipux.playlist.infrastructure.out.catalog;

import com.quipux.playlist.infrastructure.out.catalog.config.CatalogSettings;
import java.time.Duration;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The database of the catalog as the composition runs it: the same image, fixed by digest, and the same script.
 */
final class PgVectorContainer {

    // The image of compose.ai.yaml in quipux-playlist-infra: change both together.
    private static final String IMAGE = "pgvector/pgvector:0.8.7-pg18-trixie"
            + "@sha256:ab49524d18df7200b74ac9a475e1db6297fc1eb57da4c214c63d10ecc4b754fa";

    private PgVectorContainer() {
    }

    static PostgreSQLContainer create() {
        return new PostgreSQLContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("catalog/init.sql"),
                        "/docker-entrypoint-initdb.d/init.sql");
    }

    static CatalogSettings settingsOf(PostgreSQLContainer database) {
        return new CatalogSettings(database.getJdbcUrl(), database.getUsername(), database.getPassword(), 4,
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10));
    }
}
