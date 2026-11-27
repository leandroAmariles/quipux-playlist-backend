# Data Model: Gestión de listas de reproducción

El esquema de `schema.sql` no cambia.

## Dominio (`com.quipux.playlist.domain.model`)

`record` inmutables, sin identificadores ni anotaciones. Validan al construirse y lanzan `InvalidPlaylistException`.

### Playlist

| Campo | Tipo | Reglas |
|---|---|---|
| `name` | texto | Obligatorio y no en blanco (FR-004); máx. 255; sin `/`, `\` ni caracteres de control; distinto de `.` y `..` (FR-010). No se recorta |
| `description` | texto | Opcional, máx. 1000 |
| `songs` | lista de `Song` | `null` se convierte en lista vacía; no admite elementos nulos; copia inmutable; conserva el orden |

### Song

| Campo | Tipo | Máximo |
|---|---|---|
| `title` | texto | 255 |
| `artist` | texto | 255 |
| `album` | texto | 255 |
| `releaseYear` | texto | 10 |
| `genre` | texto | 100 |

Todos opcionales. Los límites viven en `domain.model.constants.PlaylistConstraints`; los nombres reservados (`.` y
`..`) y los caracteres prohibidos (`/` y `\`), en `domain.model.enums.ReservedPlaylistName` y
`ForbiddenNameCharacter`.

La unicidad de `name` (FR-005) no la puede comprobar una sola entidad: la garantiza la persistencia.

## Persistencia (`infrastructure.out.persistence`)

| Clase | Tabla | Campos |
|---|---|---|
| `PlaylistEntity` | `playlist` | `id`, `name`, `description` |
| `SongEntity` | `song` | `id`, `playlistId`, `title`, `artist`, `album`, `releaseYear`, `genre` |
| `PlaylistSongRow` | resultado del `LEFT JOIN` | campos de la lista y de la canción; `songId` nulo si no hay canciones |

**Orden**: listas por `playlist.id` y canciones por `song.id` (research R1).

## DTOs (`infrastructure.in.rest.dto`)

Identificadores en inglés; nombres JSON en español, como fija el ADR. El mismo DTO sirve de entrada y de salida.

| DTO | Campo | JSON | Dominio |
|---|---|---|---|
| `PlaylistDto` | `name` | `nombre` | `Playlist.name` |
| `PlaylistDto` | `description` | `descripcion` | `Playlist.description` |
| `PlaylistDto` | `songs` | `canciones` | `Playlist.songs` |
| `SongDto` | `title` | `titulo` | `Song.title` |
| `SongDto` | `artist` | `artista` | `Song.artist` |
| `SongDto` | `album` | `album` | `Song.album` |
| `SongDto` | `releaseYear` | `anno` | `Song.releaseYear` |
| `SongDto` | `genre` | `genero` | `Song.genre` |

## Ciclo de vida

Una lista se crea completa y se borra completa. No hay estados ni modificaciones.
