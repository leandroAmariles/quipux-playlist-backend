package com.quipux.playlist.infrastructure.out.catalog;

import com.quipux.playlist.application.port.out.CatalogIndexPort;
import com.quipux.playlist.application.port.out.SongCatalogPort;

/**
 * Everything the application needs from the catalog: with a database or without one.
 */
public interface SongCatalogAdapter extends SongCatalogPort, CatalogIndexPort {
}
