# Data Model: Recomendaciones sobre un catálogo (RAG)

Cambios sobre [el modelo de HU-12](../12-ai-recommendations/data-model.md). `+` nuevo, `~` cambia, `-` desaparece.

## Dominio

| Tipo | Campos | Reglas |
|---|---|---|
| `+ CatalogSong` | `id` (UUID), `title`, `artist`, `album`, `releaseYear`, `genre` | `id` derivado de los cinco campos, cada uno precedido de su longitud |
| `+ SongCatalog` | `songs`, `version` | Búsqueda por `id`; cuenta cuántas canciones suyas están en una lista. `version`: 12 caracteres hexadecimales de SHA-256 de los `id` ordenados y el modelo de embeddings |
| `+ CatalogRules` | — | Validez del fichero (tabla inferior) |
| `+ ModelChoice` | `catalogId` (UUID), `reason` | Lo único que se toma del modelo |
| `+ CatalogIndexPlan` | `toIndex`, `toRemove`, `kept` | Compara el catálogo con lo indexado (`id` → modelo) |
| `+ IndexReport` | `added`, `kept`, `removed` | Resultado de una indexación |
| `~ Recommendation` | `+ catalogId`, `title`, `artist`, `genre`, `reason` | `catalogId` vacío en las de respaldo |
| `~ RecommendationOrigin` | `AI` (`IA`), `+ SIMILARITY` (`SIMILITUD`), `FALLBACK` (`RESPALDO`) | |
| `~ RecommendationSet` | sin cambios de campos | `promptVersion` vacío salvo en `AI`; nueva fábrica para `SIMILARITY` |
| `~ RecommendationCacheKey` | `+ catalogVersion` | Huella de las canciones, prompt, modelo de chat y catálogo (que incluye el de embeddings) |
| `~ RecommendationRules` | — | Reglas nuevas (abajo). `- validate` de sugerencias libres |
| `~ RecommendationConstraints` | `+ SIMILARITY_REASON`, `+ MAX_CANDIDATES`, límites del catálogo | |
| `+ SongCatalogUnavailableException` | motivo | Sin causa ni mensaje externos |

### Validez del catálogo

| Regla | Valor |
|---|---|
| Tamaño | 200 a 500 canciones |
| Campos | Los cinco presentes y no en blanco |
| Longitudes | Las de una canción de una lista |
| Año | Cuatro dígitos |
| Género | De `local-genres.properties` |
| Repetidos | Ninguno por título y artista (sin mayúsculas ni espacios de los extremos) |

### Reglas de recomendación

| Regla | Entrada → salida |
|---|---|
| Candidatas | `id` ordenados por cercanía + catálogo + canciones de la lista → hasta 20 del catálogo vigente que no estén en la lista completa |
| Elección del modelo | `ModelChoice` + candidatas → descarta desconocidos y repetidos, conserva el orden, máximo 5, motivo recortado a 120; datos de la candidata. Puede quedar vacío |
| Similitud | candidatas → las 5 primeras con el motivo fijo |
| Respaldo | sin cambios |

## Puertos

| Puerto | Operaciones | Notas |
|---|---|---|
| `~ RecommendationProviderPort` | `identity()`; `choose(songs, candidates)` → `Mono<List<ModelChoice>>` | Sustituye a `recommend(songs)` |
| `+ SongCatalogPort` | `catalog()`; `nearest(songs, limit)` → `Mono<List<UUID>>` | `catalog()` es un dato fijo, sin E/S |
| `+ CatalogIndexPort` | `indexed()` → `Mono<Map<UUID, String>>`; `save(songs)`; `remove(ids)` | Solo lo usa la indexación |
| `+ IndexCatalogPort` (entrada) | `index()` → `Mono<IndexReport>` | |

## Caso de uso `GetRecommendationsUseCase`

```text
cuota → lista → ¿sin canciones? ── sí ─→ RESPALDO
                     │
                caché(canciones, prompt, modelo, catálogo) ── acierto ─→ IA
                     │
                nearest(…) → candidatas ── error o vacío ─→ RESPALDO
                     │
                choose(…) con el tiempo restante ── error, tiempo o nada válido ─→ SIMILITUD
                     │
                guardar → IA
```

Recibe además el tiempo total. Registra origen, uso de caché, modelo, prompt, versión del catálogo, número de
candidatas, tiempo de recuperación, latencia y motivo de cada degradación.

## Caso de uso `IndexCatalogUseCase`

`indexed()` → plan → `save` por lotes de 50 → `remove` de las sobrantes → `IndexReport`. Un fallo detiene el proceso y
conserva lo ya guardado.

## Persistencia

H2 no cambia. PostgreSQL: una tabla, descrita en [contracts/catalog.md](contracts/catalog.md).
