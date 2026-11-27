# Research: Géneros

## R1. Fuente de géneros

- **Decisión**: integrar `GET /v1/recommendations/available-genre-seeds` y simularlo con WireMock.
- **Motivo**: es el único catálogo de géneros documentado. Con las credenciales reales responde `404` y
  `/v1/browse/categories`, `403`; `/v1/search` responde `200` y los artistas ya no traen `genres` (2026-11-26).
- **Descartado**: derivar géneros de artistas (el campo no llega) o del filtro `genre:` de la búsqueda (filtra, no
  lista, y devuelve coincidencias por nombre).

## R2. Dónde vive el respaldo

- **Decisión**: un puerto de salida `GenreProviderPort` con dos adaptadores (Spotify y lista local) y la regla de
  respaldo en un caso de uso.
- **Motivo**: lo fija el ADR-0001 (sección 10); la regla se prueba sin infraestructura.
- **Descartado**: respaldo dentro del adaptador de Spotify, que mezcla la regla con el protocolo.

## R3. Errores de Spotify y registro

- **Decisión**: el adaptador traduce cualquier fallo a `GenreProviderUnavailableException`, sin conservar la causa. El
  caso de uso registra ese mensaje; ante otro error registra solo el nombre de la clase.
- **Motivo**: un error al leer la respuesta del token puede incluir el token en su mensaje (SC-004).
- **Nota**: `application` registra con `reactor.util.Loggers`, para depender solo de Reactor.

## R4. Tiempo máximo

- **Decisión**: un `timeout` de Reactor sobre la cadena completa (token y géneros), de 3 s por defecto.
- **Descartado**: tiempos por petición, que sumados superan el máximo de FR-007.

## R5. Token de Spotify

- **Decisión**: pedir un token en cada consulta.
- **Motivo**: sin estado ni vencimientos que manejar. La caché llega con HU-10.

## R6. Cliente HTTP

- **Decisión**: `WebClient.builder()` sobre Reactor Netty, ya incluido en WebFlux. Sin dependencias de producción
  nuevas.

## R7. Servicio simulado en las pruebas

- **Decisión**: `org.wiremock:wiremock-standalone` con alcance `test` en `infrastructure` y `bootstrap`.
- **Motivo**: trae sus dependencias reempaquetadas y no choca con las versiones de Boot 4.

## R8. Pruebas aisladas de las credenciales del equipo

- **Decisión**: la configuración de pruebas de `bootstrap` deja `playlist.spotify.client-id` y `client-secret` vacíos;
  las pruebas que necesitan Spotify apuntan a WireMock con credenciales falsas.
- **Motivo**: el equipo de desarrollo tiene `SPOTIFY_CLIENT_ID` y `SPOTIFY_CLIENT_SECRET` definidos y, sin esto, las
  pruebas llamarían al servicio real (FR-014).

## R9. Lista local

- **Decisión**: `local-genres.properties` en `infrastructure`, con claves numeradas que fijan el orden. La ruta se
  puede cambiar con `playlist.genres.local-file`. Se lee una vez, al arrancar.
