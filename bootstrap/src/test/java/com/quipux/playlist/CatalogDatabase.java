package com.quipux.playlist;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The database of the catalog as the composition runs it.
 */
final class CatalogDatabase {

    // The image of compose.ai.yaml in quipux-playlist-infra: change both together.
    private static final String IMAGE = "pgvector/pgvector:0.8.7-pg18-trixie"
            + "@sha256:ab49524d18df7200b74ac9a475e1db6297fc1eb57da4c214c63d10ecc4b754fa";
    // The same script the tests of the infrastructure module use.
    private static final String SCRIPT = "../infrastructure/src/test/resources/catalog/init.sql";
    static final String PASSWORD = "fake-catalog-password-for-tests";

    private CatalogDatabase() {
    }

    static PostgreSQLContainer create() {
        return new PostgreSQLContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
                .withPassword(PASSWORD)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(SCRIPT), "/docker-entrypoint-initdb.d/init.sql");
    }
}
