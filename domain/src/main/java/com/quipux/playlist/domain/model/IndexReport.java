package com.quipux.playlist.domain.model;

/**
 * The result of indexing the catalog: how many songs were indexed, were already there and were removed.
 */
public record IndexReport(int added, int kept, int removed) {
}
