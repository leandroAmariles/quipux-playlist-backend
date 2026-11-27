# Configuración: cambios sobre HU-12

Base: [HU-12](../../12-ai-recommendations/contracts/configuration.md). Bloques nuevos en `application.yml`.

| Propiedad | Variable | Por defecto |
|---|---|---|
| `spring.ai.model.embedding` (cambia) | `AI_PROVIDER` | `none` |
| `spring.ai.openai.embedding.model` | — | `${playlist.ai.embedding-model}` |
| `spring.ai.openai.embedding.metadata-mode` | — | `none` |
| `spring.ai.openai.embedding.timeout` | `AI_EMBEDDING_TIMEOUT` | `5s` |
| `spring.ai.openai.chat.response-format` (se quita) | — | El esquema va en cada petición |
| `playlist.ai.embedding-model` | `AI_EMBEDDING_MODEL` | `recomendador-embeddings` |
| `playlist.ai.prompt-version` (cambia) | — | `v2` |
| `playlist.catalog.file` | — | `classpath:catalog/songs.json` |
| `playlist.catalog.jdbc-url` | `CATALOG_DB_URL` | vacío: catálogo sin configurar |
| `playlist.catalog.username` | `CATALOG_DB_USER` | `catalog` |
| `playlist.catalog.password` | `CATALOG_DB_PASSWORD` | vacío: catálogo sin configurar |
| `playlist.catalog.candidates` | — | `20` |
| `playlist.catalog.retrieval-timeout` | `CATALOG_RETRIEVAL_TIMEOUT` | `5s` |
| `playlist.catalog.index-batch-size` | — | `50` |
| `playlist.catalog.pool.max-size` | — | `4` |
| `playlist.catalog.pool.connection-timeout` | — | `2s` |
| `playlist.catalog.pool.socket-timeout` | — | `4s` |
| `playlist.catalog.pool.query-timeout` | — | `3s` |

`playlist.ai.timeout` (30 s) pasa a ser el máximo total de recuperación y modelo.

## Qué se activa

| `AI_PROVIDER` | Clave | Catálogo configurado | Proveedor | Catálogo | Respuesta habitual |
|---|---|---|---|---|---|
| `none` | — | — | Simulado | No disponible | `RESPALDO` |
| `openai` | vacía | — | Simulado, con aviso | No disponible | `RESPALDO` |
| `openai` | presente | no | Real | No disponible, con aviso | `RESPALDO` |
| `openai` | presente | sí | Real | pgvector | `IA` |

El fichero del catálogo se carga y valida siempre, esté la IA activada o no.
