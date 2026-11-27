# Data Model: Caché de géneros en Redis

No hay tablas nuevas, cambios de esquema R2DBC/H2 ni modificaciones en los DTOs HTTP de `GET /genres` o `POST /lists`.

## 1. Entrada cacheada en Redis

| Aspecto | Definición | Reglas |
|---|---|---|
| Clave | `playlist.genres.cache.key` (por defecto `playlist:genres:spotify`) | No vacía ni solo espacios cuando la caché está habilitada |
| Tipo en Redis | String UTF-8 | Almacenada y leída de forma reactiva |
| Contenido | Arreglo JSON de nombres de género, p. ej. `["acoustic","rock"]` | Solo contiene géneros ya saneados por `GenreCatalog` (no nulos, no vacíos, `<= 100` caracteres, sin duplicados ignorando mayúsculas); **nunca** vacío ni de origen `LOCAL` |
| Origen asociado | `GenreSource.SPOTIFY` | Toda entrada válida recuperada desde caché corresponde al catálogo de Spotify |
| TTL | `playlist.genres.cache.ttl` (por defecto `24h`) | Debe ser `> 0`; al expirar, Redis elimina la clave automáticamente |

### Transiciones de estado de la entrada cacheada

| Estado actual | Evento | Nuevo estado en Redis | Respuesta al cliente |
|---|---|---|---|
| Ausente | `GET /genres` o `POST /lists` (con géneros) + Spotify OK con géneros válidos | Presente con TTL configurado | `SPOTIFY` (o validación contra catálogo) |
| Ausente | `GET /genres` o `POST /lists` (con géneros) + Spotify falla o vacío | **Permanece ausente** (nunca cachea `LOCAL`) | `LOCAL` (o validación contra lista local) |
| Presente (TTL vigente) | `GET /genres` o `POST /lists` (con géneros) | Presente (sin cambio de TTL ni llamada a Spotify) | `SPOTIFY` desde caché |
| Presente (TTL expirado) | `GET /genres` o `POST /lists` (con géneros) + Spotify OK | Renovada con nuevo catálogo y nuevo TTL | `SPOTIFY` |
| Presente (TTL expirado) | `GET /genres` o `POST /lists` (con géneros) + Spotify falla | **Ausente** (no se escribe respaldo) | `LOCAL` |
| Corrupta / ilegible / vacía | Lectura en `GET /genres` o `POST /lists` | Se ignora; si Spotify OK, se sobrescribe; si Spotify falla, permanece como fallo de caché | `SPOTIFY` (si responde) o `LOCAL` |
| Cualquiera (Redis caído/lento) | Lectura o escritura excede `1s` o falla conexión | Sin efecto sobre la disponibilidad de la API | `SPOTIFY` o `LOCAL` según Spotify |

## 2. Errores y códigos de trazabilidad

### `ResponseCodeEnum` (`domain`)

| Enum | Código | Uso |
|---|---|---|
| `GENRE_PROVIDER_UNAVAILABLE` | `GENRE_ERROR_001` | Existente (HU-04): fallo de Spotify y aviso de respaldo local |
| `INVALID_GENRE_CONFIGURATION` | `GENRE_ERROR_002` | Existente (HU-04): reutilizado también si `ttl <= 0`, `timeout <= 0` o clave vacía con caché habilitada |
| `GENRE_CACHE_UNAVAILABLE` | `GENRE_ERROR_003` | **Nuevo (HU-10)**: avisos internos de indisponibilidad, timeout o contenido ilegible de Redis (nunca llega como error HTTP al cliente) |

### `ErrorMessageEnum` (`domain`)

| Enum | `ResponseCodeEnum` | Cuándo se usa |
|---|---|---|
| `GENRE_CACHE_UNAVAILABLE` | `GENRE_ERROR_003` | Error de conexión o comando al leer o escribir en Redis |
| `GENRE_CACHE_TIMEOUT` | `GENRE_ERROR_003` | Operación en Redis superó el tiempo máximo configurado |
| `GENRE_CACHE_UNREADABLE` | `GENRE_ERROR_003` | Valor en Redis corrupto, mal formado o sin géneros usables |
| `INVALID_GENRE_CACHE_DURATION` | `GENRE_ERROR_002` | `ttl` o `timeout` nulo, cero o negativo al arrancar (impide arrancar) |
| `INVALID_GENRE_CACHE_KEY` | `GENRE_ERROR_002` | Clave de caché vacía cuando la caché está habilitada (impide arrancar) |

## 3. Configuración (`application.yml` y variables de entorno)

| Propiedad Spring | Variable de entorno | Valor por defecto | Regla |
|---|---|---|---|
| `playlist.genres.cache.enabled` | `GENRES_CACHE_ENABLED` | `true` (`false` en perfil de test base) | Activa o desactiva la caché Redis |
| `playlist.genres.cache.key` | `GENRES_CACHE_KEY` | `playlist:genres:spotify` | No vacía si `enabled=true` |
| `playlist.genres.cache.ttl` | `GENRES_CACHE_TTL` | `24h` | Estrictamente positiva (`> 0`) |
| `playlist.genres.cache.timeout` | `GENRES_CACHE_TIMEOUT` | `1s` | Estrictamente positiva (`> 0`) |
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` | Host del servidor Redis |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` | Puerto del servidor Redis |
| `spring.data.redis.password` | `REDIS_PASSWORD` | vacío | Opcional; solo vía entorno |
| `spring.data.redis.timeout` | `REDIS_TIMEOUT` | `1s` | Timeout de comando del cliente Redis |
| `spring.data.redis.connect-timeout` | `REDIS_CONNECT_TIMEOUT` | `1s` | Timeout de conexión inicial a Redis |
| `management.health.redis.enabled` | — | `false` | Desactiva Redis en `/actuator/health/*` para proteger sondas de K8s |
