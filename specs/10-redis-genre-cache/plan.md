# Implementation Plan: Caché de géneros en Redis

**Branch**: `feature/10-redis-genre-cache` | **Date**: 2026-11-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `quipux-playlist-backend/specs/10-redis-genre-cache/spec.md`

## Summary

Cachear en Redis el catálogo de géneros obtenido desde Spotify con un TTL configurable (24 horas por defecto) mediante un puerto de salida `GenreCachePort` en `application` y un adaptador reactivo sobre `ReactiveStringRedisTemplate` (Lettuce) en `infrastructure`, garantizando: (1) que la lista local de respaldo (`LOCAL`) nunca se cachea, (2) que un Redis caído, lento o corrupto degrada de forma transparente sin tumbar la API ni afectar `/actuator/health/liveness` y `/readiness`, y (3) que Redis queda desplegado tanto en Docker Compose como en Kubernetes/Kustomize en `quipux-playlist-infra`. Por indicación explícita del usuario, **primero se implementa el código completo (backend e infraestructura) y al final se escriben y ejecutan todas las pruebas** (unitarias, Testcontainers, BlockHound y validaciones de infra). Detalle en [research.md](research.md).

## Technical Context

**Language/Version**: Java 21 (backend), YAML (Docker Compose / Kubernetes Kustomize)

**Primary Dependencies**: Spring Boot `4.1.3` (WebFlux, Actuator), `spring-boot-starter-data-redis-reactive` (Lettuce `7.5.2.RELEASE`), Reactor, Jackson 3 (`tools.jackson.databind.ObjectMapper`). Solo en pruebas: `spring-boot-testcontainers`, `testcontainers-junit-jupiter` (Testcontainers `2.0.5`), `wiremock-standalone` `3.13.2`, `blockhound` `1.0.19.RELEASE`

**Storage**: Redis efímero en memoria (`--save "" --appendonly no`) para el catálogo de géneros con TTL (`24h` por defecto); sin cambios en el esquema R2DBC/H2

**Testing**: JUnit 5, AssertJ, Mockito, `StepVerifier`, Testcontainers (Redis real), WireMock, BlockHound, ArchUnit, JaCoCo (≥ 80 % por módulo), Checkstyle; `kustomize build` y `trivy` (`config` + `secret`) en infra — **escritas y ejecutadas al final tras completar la implementación**

**Target Platform**: Windows (desarrollo local) y Linux (`ubuntu-24.04` en GitHub Actions, contenedores Docker y clúster local Kubernetes/Argo CD)

**Project Type**: web-service (dos repositorios hermanos afectados: `quipux-playlist-backend` y `quipux-playlist-infra`)

**Performance Goals**: acierto de caché sin llamadas a Spotify durante el TTL (`SC-001`); timeout de operación Redis de `1s` por defecto para responder en `< 2s` con Redis caído y Spotify rápido, y `< 5s` con Redis y Spotify caídos (`SC-003`)

**Constraints**: sin llamadas bloqueantes en el event loop (`BlockHound`); `domain` sin frameworks; `application` solo depende de `domain` y Reactor (`ArchUnit`); cobertura ≥ 80 % por módulo en JaCoCo; cero violaciones Checkstyle; sin credenciales reales en Git ni en logs; `api` en `k8s/` con exactamente `1` réplica; cero hallazgos `HIGH`/`CRITICAL` en Trivy

**Scale/Scope**: 1 puerto nuevo, 2 adaptadores de caché (`RedisGenreCacheAdapter` y `NoOpGenreCacheAdapter`), 1 caso de uso actualizado (`GetGenresUseCase`), 0 cambios en el contrato REST/frontend, 1 servicio nuevo en `compose.yaml` y 2 manifiestos nuevos en `k8s/`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

La constitución base sigue con la plantilla sin rellenar; se usan como puertas el `ADR-0001` y las reglas de `CLAUDE.md`.

| Puerta | Antes de Phase 0 | Tras Phase 1 |
|---|---|---|
| Arquitectura hexagonal en cuatro módulos (`domain`, `application`, `infrastructure`, `bootstrap`) | Cumple | Cumple ([research.md](research.md) R2) |
| Identificadores en inglés; contrato JSON en español sin cambios | Cumple | Cumple ([contracts/cache-contract.md](contracts/cache-contract.md)) |
| Puertos reactivos con `Mono`; sin `.block()` ni bloqueo en event loop (`BlockHound`) | Cumple | Cumple (`ReactiveStringRedisTemplate` + Lettuce; R1, R6) |
| Errores tipados y mensajes en `ErrorMessageEnum` / `ResponseCodeEnum` sin exponer secretos | Cumple | Cumple (`GENRE_ERROR_003` en [data-model.md](data-model.md)) |
| Caché nunca tumba la API ni las sondas de salud; respaldo `LOCAL` nunca se cachea | Cumple | Cumple (`management.health.redis.enabled: false`; R1, R2, R4) |
| Cobertura ≥ 80 % por módulo, Checkstyle, ArchUnit y Testcontainers en `mvn verify` | Cumple | Cumple (pruebas implementadas al final; R6, R8) |
| Infra endurecida sin secretos en Git, `replicas: 1` en `api` y Trivy limpio | Cumple | Cumple ([contracts/infrastructure.md](contracts/infrastructure.md)) |

## Project Structure

### Documentation (this feature)

```text
quipux-playlist-backend/specs/10-redis-genre-cache/
├── spec.md                  # Especificación funcional (HU-10)
├── checklists/
│   └── requirements.md      # Validación de calidad de la spec
├── plan.md                  # Este archivo
├── research.md              # Phase 0: cliente, arquitectura, timeouts, aislamiento y Testcontainers
├── data-model.md            # Phase 1: clave/TTL en Redis, transiciones, errores y propiedades
├── contracts/
│   ├── cache-contract.md    # Phase 1: contrato de GenreCachePort, formato JSON y matriz de escenarios
│   └── infrastructure.md    # Phase 1: contrato de Redis en Compose y Kubernetes/Kustomize
├── quickstart.md            # Phase 1: guía de validación end-to-end
└── tasks.md                 # Phase 2 (/speckit-tasks - no generado por /speckit-plan)
```

### Source Code (affected repositories)

Dos repositorios hermanos (`quipux-playlist-backend` y `quipux-playlist-infra`), ambos en rama `feature/10-redis-genre-cache`. `+` nuevo, `~` cambia.

```text
quipux-playlist-backend/
├── domain/src/main/java/com/quipux/playlist/domain/
│   ├── exception/
│   │   └── + GenreCacheUnavailableException.java
│   └── model/enums/
│       ├── ~ ResponseCodeEnum.java                  # + GENRE_CACHE_UNAVAILABLE("GENRE_ERROR_003")
│       └── ~ ErrorMessageEnum.java                  # + mensajes de caché y configuración inválida
├── application/src/main/java/com/quipux/playlist/application/
│   ├── port/out/
│   │   └── + GenreCachePort.java                    # find() / save(List<String>)
│   └── usecase/
│       └── ~ GetGenresUseCase.java                  # lectura de caché, guardado solo SPOTIFY, fallback sin cachear
├── infrastructure/
│   ├── pom.xml                                      # ~ + spring-boot-starter-data-redis-reactive
│   └── src/main/java/com/quipux/playlist/infrastructure/out/genre/
│       ├── + GenreCacheSettings.java                # validación fail-fast de key, ttl y timeout
│       ├── + RedisGenreCacheAdapter.java            # ReactiveStringRedisTemplate + Jackson 3 + timeout
│       ├── + NoOpGenreCacheAdapter.java             # adaptador cuando playlist.genres.cache.enabled=false
│       └── config/
│           └── + GenreCacheConfig.java              # bean condicional GenreCachePort
├── bootstrap/
│   ├── pom.xml                                      # ~ + spring-boot-testcontainers, testcontainers-junit-jupiter, ejecución BlockHound Redis
│   ├── src/main/java/com/quipux/playlist/config/
│   │   └── ~ UseCaseConfig.java                     # inyectar GenreCachePort en GetGenresUseCase
│   ├── src/main/resources/
│   │   └── ~ application.yml                        # spring.data.redis.*, playlist.genres.cache.*, management.health.redis.enabled=false
│   └── src/test/
│       ├── resources/config/
│       │   └── ~ application.yml                    # playlist.genres.cache.enabled: false (aislar suites previas)
│       └── java/com/quipux/playlist/
│           ├── + GenreCacheRedisIntegrationTest.java      # Testcontainers + WireMock: hit, TTL, POST /lists, LOCAL sin cachear, entrada corrupta
│           ├── + GenreCacheRedisDownIntegrationTest.java  # Testcontainers detenido/reiniciado: 200, latencia, sondas /health/*, recuperación
│           ├── + GenreCacheNonBlockingTest.java           # BlockHound con Redis real
│           └── ~ StartupConfigurationTest.java            # validación fail-fast de TTL, timeout y key
├── application/src/test/.../usecase/
│   └── ~ GetGenresUseCaseTest.java                  # pruebas unitarias (escritas al final)
├── infrastructure/src/test/.../out/genre/
│   └── + RedisGenreCacheAdapterTest.java            # pruebas unitarias de serialización/corrupción/errores (escritas al final)
├── README.md                                        # ~ variables Redis/caché y requisito de Docker para Testcontainers
└── docs/
    ├── ~ backlog.md                                 # marcar criterios de HU-10
    └── adr/
        └── ~ 0001-arquitectura-api-listas-reproduccion.md # concreción de HU-10

quipux-playlist-infra/
├── compose/
│   └── ~ compose.yaml                               # + servicio redis (imagen Alpine fijada, red interna) y env en api sin depends_on bloqueante
├── k8s/
│   ├── + redis-deployment.yaml                      # Deployment endurecido (UID 999, readOnlyRootFilesystem, emptyDir /tmp, probes)
│   ├── + redis-service.yaml                         # ClusterIP 6379
│   ├── ~ configmap.yaml                             # REDIS_HOST, REDIS_PORT, GENRES_CACHE_*
│   └── ~ kustomization.yaml                         # incluir redis-deployment.yaml y redis-service.yaml
└── README.md                                        # ~ documentar Redis en Compose y Kubernetes
```

**Structure Decision**: mantener los artefactos de especificación y diseño en `quipux-playlist-backend/specs/10-redis-genre-cache/` (igual que HU-04 y HU-12), el código de caché en el paquete existente `infrastructure/out/genre/`, y los despliegues en `quipux-playlist-infra` (`compose/` y `k8s/`). No se toca `quipux-playlist-frontend` porque el contrato REST de `GET /genres` y `POST /lists` no cambia.

### Flujo operativo

| Caso | Recorrido |
|---|---|
| `GET /genres` (caché hit) | `401`/`403` → `GenreCachePort.find()` (≤ `1s`) → `GenreCatalog(SPOTIFY)` sin llamar a Spotify |
| `GET /genres` (caché miss / expirada / corrupta) | `401`/`403` → `find()` vacío/aviso → `SpotifyGenreProviderAdapter` (≤ `3s`) → `GenreCatalog(SPOTIFY)` válido → `save()` con TTL `24h` (absorbe error) → `200 SPOTIFY` |
| `GET /genres` (Spotify falla o vacío) | `find()` vacío/aviso → Spotify falla/vacío → `LOG.warn` → `LocalGenreProviderAdapter` → `200 LOCAL` (**nunca** llama a `save()`) |
| `GET /genres` (Redis caído o lento) | `find()` falla o supera `1s` → `LOG.warn` → continúa con Spotify (o `LOCAL`) → `save()` falla/supera `1s` se ignora → `200` sin error |
| `POST /lists` (canciones con género) | `401`/`403` → reglas HU-02 → `GetGenresPort.getGenres()` (usa caché si vigente) → `400` o guardar |
| `GET /actuator/health/liveness` y `/readiness` | Responden `200 {"status":"UP"}` sin token y sin consultar Redis (`management.health.redis.enabled: false`) |

### Orden de trabajo (código primero, pruebas al final)

1. **Dominio y aplicación (`quipux-playlist-backend`)**:
   - Añadir `GENRE_CACHE_UNAVAILABLE("GENRE_ERROR_003")` en `ResponseCodeEnum`, mensajes de caché y validación en `ErrorMessageEnum`, y excepción `GenreCacheUnavailableException`.
   - Crear `GenreCachePort` y actualizar `GetGenresUseCase` para leer de caché, guardar solo `SPOTIFY` válido y degradar ante cualquier fallo/timeout de caché sin cachear nunca `LOCAL`.
2. **Infraestructura y ensamblaje (`quipux-playlist-backend`)**:
   - Añadir `spring-boot-starter-data-redis-reactive` en `infrastructure/pom.xml`.
   - Implementar `GenreCacheSettings`, `RedisGenreCacheAdapter` (con `ReactiveStringRedisTemplate`, Jackson 3 y `.timeout(...)`), `NoOpGenreCacheAdapter` y `GenreCacheConfig`.
   - Actualizar `UseCaseConfig` y `bootstrap/src/main/resources/application.yml` (propiedades `spring.data.redis.*`, `playlist.genres.cache.*`, `management.health.redis.enabled: false`).
3. **Infraestructura (`quipux-playlist-infra`, rama `feature/10-redis-genre-cache`)**:
   - Añadir el servicio `redis` con etiqueta Alpine fijada (sin `latest`) en `compose/compose.yaml` y pasar variables a `api` sin `depends_on`.
   - Crear `k8s/redis-deployment.yaml` y `k8s/redis-service.yaml`, y actualizar `k8s/configmap.yaml` y `k8s/kustomization.yaml`.
4. **Pruebas completas al final (sobre el código ya implementado)**:
   - Añadir `spring-boot-testcontainers` y `testcontainers-junit-jupiter` en `bootstrap/pom.xml` y configurar el aislamiento en `bootstrap/src/test/resources/config/application.yml` (`playlist.genres.cache.enabled: false`).
   - Escribir `GetGenresUseCaseTest` (ampliación), `RedisGenreCacheAdapterTest`, `GenreCacheRedisIntegrationTest`, `GenreCacheRedisDownIntegrationTest`, `GenreCacheNonBlockingTest` (BlockHound) y ampliar `StartupConfigurationTest`.
   - Ejecutar `./mvnw -B -ntp verify` en `quipux-playlist-backend` y `kustomize build` + `trivy` en `quipux-playlist-infra` (más prueba end-to-end en `docker compose`).
5. **Cierre**:
   - Actualizar `README.md` de backend e infra, marcar los criterios de HU-10 en `docs/backlog.md`, anotar la concreción en `docs/adr/0001-arquitectura-api-listas-reproduccion.md` y verificar `code-review.md` si hubiera anotaciones.

### Estrategia de pruebas (ejecución al final)

| Módulo | Qué se escribe y valida tras implementar |
|---|---|
| `application` | `GetGenresUseCaseTest`: acierto de caché sin llamar proveedor, miss + guardado, exclusión de `LOCAL`, error/timeout en lectura y en escritura |
| `infrastructure` | `RedisGenreCacheAdapterTest`: serialización JSON, lectura válida, JSON corrupto/vacío traducido a `GenreCacheUnavailableException`, fallo reactivo y timeout |
| `bootstrap` (Testcontainers) | `GenreCacheRedisIntegrationTest`: hit en `GET /genres` y `POST /lists` (WireMock=1), expiración real de TTL corto (`2s`), fallo de Spotify (`LOCAL`) + recuperación inmediata (`SPOTIFY`), valor corrupto sobrescrito |
| `bootstrap` (Testcontainers caído) | `GenreCacheRedisDownIntegrationTest`: Redis detenido (`200` con Spotify y con `LOCAL`), latencia acotada (`SC-003`), `/actuator/health/liveness` y `/readiness` en `200`, y reanudación al reiniciar Redis |
| `bootstrap` (BlockHound / arranque) | `GenreCacheNonBlockingTest` (lectura/escritura real contra Redis bajo `BlockHound`), `StartupConfigurationTest` (`TTL <= 0`, `timeout <= 0`, `key` vacía) y `ArchitectureTest` |
| `quipux-playlist-infra` | `kustomize build k8s`, `trivy config`, `trivy fs --scanners secret`, y comprobación manual con `compose.yaml` + `compose.wiremock.yaml` |

### Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Las pruebas previas de HU-04 (`GenresApiTest` con `SPOTIFY.verify(2, ...)`) fallen al activarse Redis | Fijar `playlist.genres.cache.enabled: false` en `bootstrap/src/test/resources/config/application.yml` y activarlo explícitamente en los tests de HU-10 |
| `RedisReactiveHealthIndicator` tumbe `/actuator/health/readiness` con Redis caído | Fijar `management.health.redis.enabled: false` en `application.yml` y verificarlo en `GenreCacheRedisDownIntegrationTest` |
| Timeouts por defecto de Lettuce bloqueen peticiones más de `1s` | Configurar `spring.data.redis.timeout=1s`, `connect-timeout=1s` y `.timeout(settings.timeout())` de Reactor sobre `find()` y `save()` |
| `BlockHound` detecte lectura de recursos/clases en el primer acceso a Lettuce/Jackson | Calentar lectura y escritura en `@BeforeAll` antes de `BlockHound.install()` en `GenreCacheNonBlockingTest`, igual que en `PlaylistNonBlockingTest` |
| Hallazgos `HIGH`/`CRITICAL` de Trivy en `redis-deployment.yaml` o en la imagen Redis | Usar etiqueta Alpine reciente sin CVEs corregibles, UID `999` no root, `readOnlyRootFilesystem: true`, `emptyDir` en `/tmp`, `capabilities.drop: ["ALL"]` y `seccompProfile: RuntimeDefault` |

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Una sola spec/plan cubre `quipux-playlist-backend` y `quipux-playlist-infra` | HU-10 exige caché Redis en el backend y su servicio/despliegue en Compose y Kubernetes (`ADR-0001` sección 7) | Dividir en dos specs fragmentaría la trazabilidad extremo a extremo de la historia |
| Desactivar caché por defecto en `bootstrap/src/test/resources/config/application.yml` | Aísla las pruebas de HU-02..HU-09 de requerir Docker/Redis activo, igual que ya se hace con la IA en HU-12 | Exigir Redis para todas las pruebas previas acoplaría y ralentizaría toda la suite |
