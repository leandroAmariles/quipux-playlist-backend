# Contrato de Caché de Géneros y API

## 1. Contrato HTTP (sin cambios respecto a HU-04)

Las rutas públicas de la API no cambian su firma, cabeceras, códigos HTTP ni cuerpo JSON:

- **`GET /genres`**: mantiene exactamente el contrato definido en `specs/04-genres/contracts/rest-api.md` (`200 OK` con `{"generos": [...], "origen": "SPOTIFY" | "LOCAL"}`, `401` sin token válido, `403` sin rol válido).
- **`POST /lists`**: mantiene la validación de `genero` contra el catálogo vigente (reutilizando la caché cuando existe entrada de Spotify vigente y sin consultar Redis ni Spotify cuando ninguna canción declara género o fallan reglas previas de seguridad/estructura).
- **`GET /actuator/health/liveness` y `GET /actuator/health/readiness`**: responden siempre `200 {"status":"UP"}` sin token, con independencia de si Redis está disponible o caído.

## 2. Contrato interno del puerto `GenreCachePort` (`application/port/out`)

```java
public interface GenreCachePort {

    Mono<List<String>> find();

    Mono<Void> save(List<String> genres);
}
```

### Semántica del puerto
- `find()`:
  - Emite la lista de géneros almacenada si existe una entrada válida en caché.
  - Emite vacío (`Mono.empty()`) si no hay entrada en caché (o si está deshabilitada mediante `NoOpGenreCacheAdapter`).
  - Emite error `GenreCacheUnavailableException` si Redis está caído, agota el timeout configurado o contiene un valor ilegible/corrupto; el caso de uso captura cualquier error en lectura, registra advertencia (`LOG.warn`) y continúa consultando `GenreProviderPort`.
- `save(List<String> genres)`:
  - Solo debe ser invocado por `GetGenresUseCase` cuando `GenreCatalog` proviene de `GenreSource.SPOTIFY` y contiene al menos un género válido. **Nunca** se invoca para `GenreSource.LOCAL`.
  - Almacena la lista con el TTL configurado (`24h` por defecto) y completa (`Mono<Void>`).
  - Si Redis falla o agota el timeout durante la escritura, el adaptador emite `GenreCacheUnavailableException` (o el caso de uso captura el error con `onErrorResume`), registra advertencia y **nunca** falla la respuesta hacia el usuario.

## 3. Formato almacenado en Redis

- **Clave**: `playlist:genres:spotify` (configurable vía `playlist.genres.cache.key`).
- **Valor**: cadena JSON UTF-8 con arreglo ordenado de strings, por ejemplo:
  ```json
  ["acoustic","afrobeat","rock"]
  ```
- **TTL**: configurado vía `playlist.genres.cache.ttl` (por defecto `86400` segundos / `24h`).

## 4. Matriz de comportamiento observable

| Escenario | ¿Llama a Redis? | ¿Llama a Spotify? | ¿Escribe en Redis? | Origen entregado |
|---|---|---|---|---|
| Caché hit dentro del TTL | Sí (`find`) | No | No | `SPOTIFY` |
| Caché miss + Spotify OK (catálogo válido) | Sí (`find`) | Sí | Sí (`save` con TTL) | `SPOTIFY` |
| Caché miss + Spotify error / timeout / vacío | Sí (`find`) | Sí | **No** | `LOCAL` |
| Siguiente consulta tras `LOCAL` + Spotify OK | Sí (`find`) | Sí | Sí (`save` con TTL) | `SPOTIFY` |
| Caché expirada + Spotify OK | Sí (`find`) | Sí | Sí (`save` con nuevo TTL) | `SPOTIFY` |
| Caché expirada + Spotify falla | Sí (`find`) | Sí | **No** | `LOCAL` |
| Redis caído o timeout en `find` + Spotify OK | Sí (falla/expira ≤ `1s`) | Sí | Intenta `save` (falla/expira ≤ `1s`, se ignora) | `SPOTIFY` |
| Redis caído en `find` + Spotify falla | Sí (falla/expira ≤ `1s`) | Sí | **No** | `LOCAL` |
| Valor corrupto/ilegible en Redis + Spotify OK | Sí (`find` falla por lectura) | Sí | Sí (`save` sobrescribe) | `SPOTIFY` |
| `401` / `403` / `400` previo o `POST /lists` sin géneros | No | No | No | N/A |
| `GET /actuator/health/liveness` o `/readiness` con Redis caído | No | No | No | HTTP `200 UP` |
