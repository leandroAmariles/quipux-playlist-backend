# Contrato del catálogo

## Fichero `catalog/songs.json`

En `infrastructure/src/main/resources`, UTF-8, configurable con `playlist.catalog.file`.

```json
[
  { "titulo": "Take Five", "artista": "The Dave Brubeck Quartet", "album": "Time Out", "anno": "1959", "genero": "jazz" }
]
```

Sin identificadores: se derivan al cargar. Reglas en [data-model.md](../data-model.md). Si no se cumplen, la
aplicación no arranca y nombra la regla y la posición.

## Comprobación contra MusicBrainz

`tools/catalog/verify_catalog.py` (Python, sin dependencias, de ejecución manual):

| Aspecto | Contrato |
|---|---|
| Entrada | `songs.json` |
| Contacto | `MUSICBRAINZ_CONTACT`, obligatorio; sin él no hace peticiones |
| Ritmo | 1 petición por segundo como máximo |
| Compara | Título, artista, álbum y año. No lee ni copia etiquetas ni géneros |
| Salida | Informe `docs/catalog-verification.md`: por canción, identificador de MusicBrainz o motivo de no coincidencia; código distinto de 0 si queda alguna sin comprobar |

Una canción sin coincidencia se corrige o se retira; una comprobación a mano se anota en el informe con su fuente.

## Esquema de PostgreSQL

`compose/catalog-db/init.sql` (infra) y su copia en `infrastructure/src/test/resources/catalog/init.sql`:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE TABLE IF NOT EXISTS catalog.song_embedding (
    id        uuid PRIMARY KEY,
    content   text NOT NULL,
    metadata  jsonb NOT NULL,
    embedding vector(768) NOT NULL
);
CREATE INDEX IF NOT EXISTS song_embedding_hnsw
    ON catalog.song_embedding USING hnsw (embedding vector_cosine_ops);
```

| Columna | Contenido |
|---|---|
| `id` | El de la canción del catálogo |
| `content` | `título \| artista \| álbum \| año \| género` |
| `metadata` | `{"model": "<alias de embeddings>"}` |
| `embedding` | Vector de ese texto |

## Indexación: argumento `index-catalog`

```bash
docker compose -p quipux-ia -f compose.yaml -f compose.ai.yaml run --rm catalog-index
```

| Aspecto | Contrato |
|---|---|
| Éxito | Código 0 y registro `Catalog indexed: version=…, added=…, kept=…, removed=…` |
| Fallo | Código 1 y motivo sin claves, contraseñas ni respuestas externas |
| Requiere | IA activada, clave, conexión al catálogo y su contraseña; no usa secretos JWT ni H2 |
| Repetición | Sin cambios: `added=0`, ninguna petición de embeddings |
| Desde cero | `TRUNCATE catalog.song_embedding` o borrar el volumen, volver a indexar y reiniciar la API |
