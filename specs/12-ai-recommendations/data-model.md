# Data Model: Recomendaciones con IA

Sin tablas nuevas ni cambios en `schema.sql`. Todo lo nuevo vive en memoria o en archivos del repositorio. Nombres en
inglés; el contrato HTTP, en español.

## Dominio (`domain/model`)

| Tipo | Campos | Reglas |
|---|---|---|
| `Recommendation` | `title`, `artist`, `genre`, `reason` | Recién llegada del modelo puede traer nulos; solo sale validada |
| `RecommendationSet` | `playlistName`, `origin`, `promptVersion`, `recommendations` | Máximo 5; `promptVersion` vacío si `origin` no es `AI` |
| `RecommendationOrigin` (enum) | `AI` (`IA`), `FALLBACK` (`RESPALDO`) | `SIMILITUD` se añade en HU-13 |
| `ProviderIdentity` | `promptVersion`, `model` | Forma parte de la clave de caché |
| `RecommendationCacheKey` | `songsFingerprint`, `promptVersion`, `model` | Huella SHA-256 de todas las canciones, con cada campo delimitado por su longitud |
| `CachedRecommendations` | `promptVersion`, `recommendations` | Sin nombre de lista: se pone el pedido |
| `QuotaDecision` | `allowed`, `retryAfter` | `retryAfter` solo si no se permite |
| `SongSample` | `maxSongs`, `maxFieldLength` | Primeras canciones, campos recortados sin partir caracteres |
| `RecommendationRules` | — | Reglas puras de validación y respaldo |
| `DefaultRecommendations` | `recommendations` | Mínimo 5, sin repetidos, campos completos, géneros del catálogo local |

Constantes en `RecommendationConstraints`: máximo 5; `title` y `artist` 255 y `genre` 100 (los de `PlaylistConstraints`,
para que una sugerencia se pueda añadir a una lista); `reason` 120.

### Identidad de una canción

Título y artista, sin espacios en los extremos y sin distinguir mayúsculas (`Locale.ROOT`). Una canción de la lista sin
título o artista no coincide con ninguna sugerencia.

### Validación de la respuesta del modelo (en este orden)

1. Descartar sin `title` o `artist` (nulos o en blanco).
2. Descartar si `title`, `artist` o `genre` superan su tamaño. `genre` y `reason` ausentes quedan como texto vacío.
3. Recortar `reason` a 120.
4. Descartar las que ya están en la lista **completa**.
5. Quitar repetidas, conservando la primera.
6. Quedarse con las 5 primeras.
7. Si no queda ninguna, usar el respaldo.

El respaldo aplica los pasos 4 y 6 a la lista por defecto y puede quedar vacío.

## Excepciones y códigos

| Excepción | Código | Estado | Uso |
|---|---|---|---|
| `RecommendationRateLimitException` (con `retryAfterSeconds`) | `RECOMMENDATION_ERROR_001` | `429` | Llega al cliente |
| `RecommendationProviderUnavailableException` | `RECOMMENDATION_ERROR_002` | nunca sale | La captura el caso de uso |
| Configuración no válida (lista por defecto o prompt) | `RECOMMENDATION_ERROR_003` | no arranca | Arranque |

Motivos de `RecommendationProviderUnavailableException` (en `ErrorMessageEnum`, sin texto del proveedor): `DISABLED`,
`NOT_CONFIGURED`, `TIMEOUT`, `ERROR_RESPONSE` (con el estado), `UNREACHABLE`, `EMPTY`, `UNREADABLE`,
`CIRCUIT_OPEN`, `TOO_MANY_CALLS`. El caso de uso añade `NO_VALID_SUGGESTIONS`.

## Puertos (`application`)

| Puerto | Firma | Adaptador |
|---|---|---|
| `GetRecommendationsPort` (entrada) | `Mono<RecommendationSet> recommend(String requester, String playlistName)` | `GetRecommendationsUseCase` |
| `RecommendationProviderPort` | `ProviderIdentity identity()`; `Mono<List<Recommendation>> recommend(List<Song> songs)` | `LlmRecommendationProviderAdapter` o `SimulatedRecommendationProviderAdapter` |
| `DefaultRecommendationsPort` | `Mono<DefaultRecommendations> defaults()` | Archivo `.properties` |
| `RecommendationCachePort` | `Mono<CachedRecommendations> find(key)`; `Mono<Void> save(key, value)` | Caffeine |
| `RequestQuotaPort` | `Mono<QuotaDecision> consume(String requester)` | Ventana deslizante en memoria |
| `PlaylistRepositoryPort` (existente) | `findByName` | R2DBC |

El adaptador simulado señala `DISABLED` o `NOT_CONFIGURED`; así el caso de uso responde con la lista por defecto y
`RESPALDO`, que es lo que ve quien consulta.

## Flujo del caso de uso

```text
cuota ── no ──► 429
  │ sí
buscar lista ── no existe ──► 404        (un fallo de H2 sigue siendo 500)
  │
¿sin canciones? ── sí ──► respaldo
  │
¿en caché? ── sí ──► AI (nombre pedido)
  │
proveedor(primeras 50) ── cualquier error ──► respaldo (se registra el motivo)
  │
validar contra la lista completa ── vacío ──► respaldo
  │
guardar ──► AI
```

## Estados en memoria

| Estado | Transiciones |
|---|---|
| Cortacircuitos | cerrado → abierto (≥ 50 % de 10, mínimo 5) → semiabierto (30 s, en la siguiente llamada) → cerrado si va bien, abierto si falla |
| Permisos | 2; se toman antes de llamar y se devuelven al terminar la llamada al proveedor, aunque el cliente se haya ido |
| Cuota por usuario | hasta 10 marcas de los últimos 60 s |
| Caché | entra solo una respuesta `AI`; sale a las 24 h o por tamaño |

Todo se pierde al reiniciar.
