# Composición de la IA: cambios en infra

Base: [HU-12](../../12-ai-recommendations/contracts/ai-compose.md). `compose.yaml` y `compose.ai.gpu.yaml` no cambian.

## Archivos

```text
compose/compose.ai.yaml          ~ catalog-db, catalog-index, entorno de api y ollama, volumen catalog-data
compose/catalog-db/init.sql      + esquema (contracts/catalog.md)
compose/litellm/config.yaml      ~ additional_drop_params en recomendador-embeddings
compose/.env.example             ~ CATALOG_DB_PASSWORD=
README.md                        ~
```

## Servicios

| Servicio | Contrato |
|---|---|
| `catalog-db` (nuevo) | `pgvector/pgvector:0.8.7-pg18-trixie` fijada por digest (research R11). Base y usuario `catalog`; contraseña `${CATALOG_DB_PASSWORD:?…}`. Volumen `catalog-data` en `/var/lib/postgresql`; `init.sql` en solo lectura en `/docker-entrypoint-initdb.d/`. Puerto solo `127.0.0.1:${CATALOG_DB_PORT:-5433}`. Salud: `pg_isready -h 127.0.0.1 -U catalog -d catalog` |
| `catalog-index` (nuevo) | Construye la misma imagen que `api`; argumento `index-catalog`; `restart: "no"`. Espera `catalog-db` y `litellm` sanos. Entorno: `AI_PROVIDER=openai`, `AI_BASE_URL`, `AI_API_KEY`, `AI_EMBEDDING_TIMEOUT=120s`, `CATALOG_DB_URL`, `CATALOG_DB_PASSWORD` |
| `ollama` | `+ OLLAMA_CONTEXT_LENGTH=16384` |
| `api` | `+ CATALOG_DB_URL=jdbc:postgresql://catalog-db:5432/catalog`, `CATALOG_DB_PASSWORD`, `AI_EMBEDDING_MODEL`, `CATALOG_RETRIEVAL_TIMEOUT`. Sin `depends_on` de los servicios de IA ni del catálogo |

## LiteLLM

```yaml
  - model_name: recomendador-embeddings
    litellm_params:
      model: os.environ/LITELLM_EMBEDDING_MODEL
      api_base: http://ollama:11434
      timeout: 120
      # El SDK siempre lo envía y Ollama no lo admite.
      additional_drop_params: ["encoding_format"]
```

## Actualización desde HU-12

1. Añadir `CATALOG_DB_PASSWORD` a `compose/.env`.
2. `up -d --build`: se crea el volumen, se indexa y se recrean `ollama` y `litellm`.

El esquema solo se crea en un volumen vacío. Cambiar la contraseña después exige cambiarla también en la base.
