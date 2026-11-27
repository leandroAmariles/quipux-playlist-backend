# Quickstart: Caché de géneros en Redis

## 1. Requisitos previos

- Java 21 y Maven Wrapper (`./mvnw` en `quipux-playlist-backend`)
- Docker en ejecución (para las pruebas de integración con Testcontainers y para Docker Compose)
- `kustomize` y `trivy` (para validar los manifiestos de `quipux-playlist-infra`)

## 2. Verificación automática completa del backend

Desde `quipux-playlist-backend` (con Docker activo):

```bash
./mvnw -B -ntp verify
```

Qué valida en una sola ejecución:
- Pruebas unitarias de `domain`, `application` e `infrastructure` (incluidos `GetGenresUseCaseTest` y `RedisGenreCacheAdapterTest`).
- Pruebas de integración con Redis real vía Testcontainers y Spotify simulado vía WireMock (`GenreCacheRedisIntegrationTest` y `GenreCacheRedisDownIntegrationTest`): acierto de caché, expiración de TTL, exclusión de `LOCAL`, entrada corrupta y tolerancia con contenedor Redis detenido.
- Verificación sin llamadas bloqueantes en el event loop (`GenreCacheNonBlockingTest` y `PlaylistNonBlockingTest` con BlockHound).
- Reglas de arquitectura hexagonal (`ArchitectureTest`), cobertura mínima del 80 % por módulo en JaCoCo y Checkstyle.

## 3. Validación de extremo a extremo con Docker Compose y WireMock

Desde `quipux-playlist-infra/compose` (con `.env` configurado según el README de infra):

1. Levantar la aplicación con WireMock y Redis:
   ```bash
   docker compose -f compose.yaml -f compose.wiremock.yaml up --build -d
   ```
2. Autenticarse (`POST /auth/login`) y consultar `GET /genres` dos veces seguidas:
   - Ambas respuestas devuelven `200` con `origen: "SPOTIFY"`.
   - En los logs de `wiremock`, solo la **primera** petición genera llamadas a `/api/token` y `/v1/recommendations/available-genre-seeds`.
3. Crear una lista (`POST /lists`) con un género válido (p. ej. `rock`): devuelve `201` sin llamada adicional a WireMock.
4. Simular caída de Redis:
   ```bash
   docker compose -f compose.yaml -f compose.wiremock.yaml stop redis
   ```
   - `GET /genres` sigue respondiendo `200` (`SPOTIFY` desde WireMock o `LOCAL` si WireMock también falla).
   - `GET /actuator/health/liveness` y `GET /actuator/health/readiness` siguen respondiendo `200 {"status":"UP"}`.
5. Recuperar Redis (`docker compose -f compose.yaml -f compose.wiremock.yaml start redis`) y volver a consultar `GET /genres` dos veces: se reanuda el cacheo automáticamente sin reiniciar `api`.
6. Detener el entorno:
   ```bash
   docker compose -f compose.yaml -f compose.wiremock.yaml down
   ```

## 4. Validación de infraestructura Kubernetes y Trivy

Desde `quipux-playlist-infra`:

```bash
kustomize build k8s > /tmp/manifests.yaml
trivy config /tmp/manifests.yaml
trivy fs --scanners secret .
```

Resultado esperado:
- `kustomize build k8s` finaliza sin errores e incluye `Deployment/redis` endurecido, `Service/redis` (`ClusterIP:6379`) y `Deployment/api` con `replicas: 1`.
- `trivy` reporta 0 hallazgos `HIGH` o `CRITICAL` de configuración ni de secretos.
- Al sincronizar `main` en Argo CD (clúster local `kind`), la aplicación queda `Synced` y `Healthy` con el pod de Redis en ejecución, y sigue `Healthy` si se escala temporalmente `redis` a `0` réplicas.
