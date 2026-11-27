# Data Model: Géneros

No hay tablas nuevas ni cambios de esquema.

## `GenreCatalog` (domain)

| Campo | Tipo | Regla |
|---|---|---|
| `source` | `GenreSource` | `SPOTIFY` o `LOCAL` |
| `genres` | lista de textos | conserva el orden; descarta nulos, vacíos, solo espacios, de más de 100 caracteres y repetidos sin distinguir mayúsculas (queda el primero) |

- Un catálogo vacío tras el filtrado no se entrega: Spotify pasa al respaldo y la lista local vacía impide arrancar.
- `includesAll(nombres)`: cada nombre coincide con un género sin distinguir mayúsculas; los espacios cuentan.

## `Playlist` (domain, ampliado)

- `declaredGenres()`: géneros de sus canciones que no son nulos ni están en blanco.

## Errores

| `ErrorMessageEnum` | Código | HTTP |
|---|---|---|
| `UNKNOWN_GENRE` | `PLAYLIST_GE_00001` | `400` |
| `GENRE_PROVIDER_*` | `GENRE_ERROR_001` | no llega al cliente: activa el respaldo |
| `LOCAL_GENRES_EMPTY` | `GENRE_ERROR_002` | impide arrancar |

## Configuración

| Propiedad | Variable | Por defecto |
|---|---|---|
| `playlist.spotify.client-id` | `SPOTIFY_CLIENT_ID` | vacío |
| `playlist.spotify.client-secret` | `SPOTIFY_CLIENT_SECRET` | vacío |
| `playlist.spotify.accounts-base-url` | `SPOTIFY_ACCOUNTS_BASE_URL` | `https://accounts.spotify.com` |
| `playlist.spotify.api-base-url` | `SPOTIFY_API_BASE_URL` | `https://api.spotify.com` |
| `playlist.spotify.timeout` | `SPOTIFY_TIMEOUT` | `3s` |
| `playlist.genres.local-file` | — | `classpath:local-genres.properties` |
