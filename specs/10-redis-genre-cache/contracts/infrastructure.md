# Contrato de Infraestructura: Redis en Compose y Kubernetes

Aplica al repositorio `quipux-playlist-infra` (rama `feature/10-redis-genre-cache`).

## 1. Docker Compose (`compose/compose.yaml`)

- **Nuevo servicio `redis`**:
  - Imagen oficial Alpine de Redis con versión explícita fijada (sin `latest` ni etiquetas móviles) verificada sin vulnerabilidades `HIGH`/`CRITICAL` con fix disponible.
  - Ejecución en modo caché efímero sin persistencia en disco: `redis-server --save "" --appendonly no`.
  - Sin puertos publicados al host (comunicación exclusiva en la red interna de Compose).
  - `healthcheck`: `["CMD", "redis-cli", "ping"]`.
- **Servicio `api`**:
  - Recibe variables de conexión y caché:
    - `REDIS_HOST=redis`
    - `REDIS_PORT=6379`
    - `REDIS_PASSWORD` (opcional, desde entorno si se define)
    - `GENRES_CACHE_ENABLED` (opcional, por defecto `true` en la API)
    - `GENRES_CACHE_TTL` (opcional, por defecto `24h`)
    - `GENRES_CACHE_TIMEOUT` (opcional, por defecto `1s`)
  - **Sin `depends_on` bloqueante** hacia `redis`: `api` debe arrancar y seguir atendiendo peticiones aunque `redis` esté detenido o falle.
- **Compatibilidad con `compose/compose.wiremock.yaml`**:
  - Al levantar `docker compose -f compose.yaml -f compose.wiremock.yaml up`, la primera consulta a `GET /genres` llega a WireMock (`origen: SPOTIFY`), la segunda se sirve desde `redis` sin llamada a WireMock, y al ejecutar `docker compose stop redis` la API sigue respondiendo `200`.

## 2. Kubernetes y Kustomize (`k8s/`)

### Recursos añadidos/actualizados

| Archivo | Recurso | Requisitos obligatorios |
|---|---|---|
| `k8s/redis-deployment.yaml` | `Deployment` (`redis`) | `replicas: 1`, imagen oficial Alpine fijada (sin `latest`), `automountServiceAccountToken: false`, `runAsNonRoot: true`, `runAsUser: 999`, `runAsGroup: 999`, `fsGroup: 999`, `seccompProfile: RuntimeDefault`, `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`, `capabilities.drop: ["ALL"]`, `emptyDir` montado en `/tmp`, args `["redis-server", "--save", "", "--appendonly", "no", "--dir", "/tmp"]`, `resources` (`requests`/`limits` de CPU y memoria), `readinessProbe` y `livenessProbe` con `redis-cli ping` |
| `k8s/redis-service.yaml` | `Service` (`redis`) | `type: ClusterIP`, selector `app.kubernetes.io/name: redis`, puerto `6379` |
| `k8s/configmap.yaml` | `ConfigMap` (`playlist-config`) | Añade claves no secretas: `REDIS_HOST: "redis.quipux-playlist.svc.cluster.local"`, `REDIS_PORT: "6379"`, `GENRES_CACHE_ENABLED: "true"`, `GENRES_CACHE_TTL: "24h"`, `GENRES_CACHE_TIMEOUT: "1s"` |
| `k8s/kustomization.yaml` | `Kustomization` | Incluye `redis-deployment.yaml` y `redis-service.yaml` en `resources:`; mantiene `secret.example.yaml` fuera |

### Invariantes de CI en `quipux-playlist-infra`

- `kustomize build k8s` termina con código `0`, mantiene `Deployment/api` con `replicas: 1` y no incluye ningún recurso `Secret`.
- `trivy config` sobre `kustomize build k8s` + `argocd/` reporta **0** hallazgos `HIGH` o `CRITICAL`.
- `trivy fs --scanners secret .` reporta **0** secretos reales o detectados.
- `api-deployment.yaml` no bloquea su arranque ni su `readinessProbe` por Redis (`management.health.redis.enabled: false` en la API).
