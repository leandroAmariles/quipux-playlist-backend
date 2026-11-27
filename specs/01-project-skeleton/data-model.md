# Data Model: esquema inicial

HU-01 solo crea las tablas. Las clases de dominio y sus reglas llegan con HU-02.

## playlist

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | BIGINT, identidad | Clave primaria |
| `name` | VARCHAR(255) | Obligatorio, **único** |
| `description` | VARCHAR(1000) | Opcional |

## song

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | BIGINT, identidad | Clave primaria |
| `playlist_id` | BIGINT | Obligatorio, clave foránea a `playlist.id` |
| `title` | VARCHAR(255) | Opcional |
| `artist` | VARCHAR(255) | Opcional |
| `album` | VARCHAR(255) | Opcional |
| `release_year` | VARCHAR(10) | Opcional. Texto, como en el contrato (`anno`) |
| `genre` | VARCHAR(100) | Opcional |

**Relación**: una lista tiene cero o más canciones. Sin borrado en cascada: el ADR exige que el adaptador borre de
forma explícita.

**Pendiente para HU-02**: el enunciado no define longitudes máximas ni qué campos de canción son obligatorios. Las
longitudes de aquí son provisionales.
