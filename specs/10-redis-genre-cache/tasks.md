---
description: "Tareas de HU-10: caché de géneros en Redis"
---

# Tasks: Caché de géneros en Redis

**Input**: documentos de diseño en `quipux-playlist-backend/specs/10-redis-genre-cache/` (`plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)

**Tests**: incluidas en la **Phase 7 (`US4`)**, implementadas y ejecutadas **al final sobre el código ya implementado** (por indicación explícita del usuario para agilizar la entrega), cubriendo pruebas unitarias, integración real con Testcontainers + WireMock, `BlockHound`, `ArchitectureTest`, Checkstyle y JaCoCo ≥ 80 %.

**Rutas**: relativas a la raíz del espacio de trabajo, con estos prefijos:

| Prefijo | Ruta |
|---|---|
| `{back}` | `quipux-playlist-backend` |
| `{infra}` | `quipux-playlist-infra` |
| `{spec}` | `quipux-playlist-backend/specs/10-redis-genre-cache` |
| `{domain}` | `quipux-playlist-backend/domain/src/main/java/com/quipux/playlist/domain` |
| `{app}` | `quipux-playlist-backend/application/src/main/java/com/quipux/playlist/application` |
| `{infra-src}` | `quipux-playlist-backend/infrastructure/src/main/java/com/quipux/playlist/infrastructure` |
| `{boot}` | `quipux-playlist-backend/bootstrap/src/main/java/com/quipux/playlist` |
| `{app-test}` | `quipux-playlist-backend/application/src/test/java/com/quipux/playlist/application` |
| `{infra-test}` | `quipux-playlist-backend/infrastructure/src/test/java/com/quipux/playlist/infrastructure` |
| `{boot-test}` | `quipux-playlist-backend/bootstrap/src/test/java/com/quipux/playlist` |

**En todas las tareas**:

- Identificadores en inglés, líneas de 121 caracteres como máximo, llaves obligatorias y sin imports sin usar.
- Sin `.block()` en producción ni en pruebas; toda operación Redis es reactiva y no bloqueante (`BlockHound`).
- Mensajes de error y avisos en `ErrorMessageEnum`; `@Value` solo en atributos de clase; sin secretos reales ni credenciales en Git o logs.
- **Orden rápido**: implementar todo el código de producción (backend + infra, Phases 1–6) primero y escribir/ejecutar todas las pruebas al final (Phase 7).

---

## Phase 1: Setup (Dependencias, aislamiento y rama de infra)

**Purpose**: Preparar las dependencias Maven de Redis y Testcontainers, aislar la suite previa de pruebas y abrir la rama de trabajo en infraestructura.

- [X] T001 Añadir `org.springframework.boot:spring-boot-starter-data-redis-reactive` en `{back}/infrastructure/pom.xml` (`{spec}/research.md`, R1)
- [X] T002 [P] Añadir `org.springframework.boot:spring-boot-testcontainers` y `org.testcontainers:testcontainers-junit-jupiter` (`scope: test`) y la ejecución aislada `non-blocking-genre-cache-test` para `GenreCacheNonBlockingTest` en `maven-surefire-plugin` dentro de `{back}/bootstrap/pom.xml` (`{spec}/research.md`, R6)
- [X] T003 [P] Añadir `playlist.genres.cache.enabled: false` en `{back}/bootstrap/src/test/resources/config/application.yml` para aislar las pruebas heredadas de HU-02..HU-09 (`{spec}/data-model.md`, `Configuración.playlist.genres.cache.enabled`; `{spec}/research.md`, R5)
- [X] T004 [P] Crear y cambiar a la rama `feature/10-redis-genre-cache` desde `main` actualizado en `{infra}` (en `{back}` la rama ya está activa)

---

## Phase 2: Foundational (Dominio, Puerto y Configuración base)

**Purpose**: Definir excepciones, códigos de trazabilidad, puerto de salida y validación de configuración necesarios antes de cualquier historia de usuario.

**⚠️ CRITICAL**: Ninguna historia de usuario (`US1`–`US5`) puede implementarse en el backend hasta completar esta fase.

- [X] T005 Añadir `GENRE_CACHE_UNAVAILABLE("GENRE_ERROR_003")` en `{domain}/model/enums/ResponseCodeEnum.java` y los mensajes `GENRE_CACHE_UNAVAILABLE`, `GENRE_CACHE_TIMEOUT`, `GENRE_CACHE_UNREADABLE`, `INVALID_GENRE_CACHE_DURATION` e `INVALID_GENRE_CACHE_KEY` en `{domain}/model/enums/ErrorMessageEnum.java` (`{spec}/data-model.md`, `Errores`)
- [X] T006 [P] Crear `{domain}/exception/GenreCacheUnavailableException.java` sin encadenar causas crudas que puedan exponer credenciales de conexión (`{spec}/research.md`, R4)
- [X] T007 [P] Crear el puerto de salida `{app}/port/out/GenreCachePort.java` con `Mono<List<String>> find()` y `Mono<Void> save(List<String> genres)` (`{spec}/contracts/cache-contract.md`)
- [X] T008 [P] Crear `{infra-src}/out/genre/GenreCacheSettings.java` validando al arrancar `key`, `ttl` y `timeout` cuando la caché está habilitada (`{spec}/data-model.md`, `Entrada cacheada en Redis.Clave`, `Entrada cacheada en Redis.TTL`, `Configuración.playlist.genres.cache.key`, `Configuración.playlist.genres.cache.ttl`, `Configuración.playlist.genres.cache.timeout`, `Errores.INVALID_GENRE_CACHE_DURATION` e `Errores.INVALID_GENRE_CACHE_KEY`)
- [X] T009 [P] Crear `{infra-src}/out/genre/NoOpGenreCacheAdapter.java` devolviendo `Mono.empty()` en `find()` y `save(List<String>)` para el modo sin caché (`{spec}/research.md`, R5)

**Checkpoint**: Dominio, puerto de salida, configuración fail-fast y adaptador nulo listos para construir los flujos de caché.

---

## Phase 3: User Story 1 - Reutilizar los géneros de Spotify desde Redis durante el TTL (Priority: P1) 🎯 MVP

**Goal**: Almacenar en Redis el catálogo válido de Spotify durante el TTL configurado (`24h` por defecto) y reutilizarlo en `GET /genres` y `POST /lists` sin volver a llamar a Spotify.

**Independent Test**: Consultar `GET /genres` dos veces seguidas y crear una lista en `POST /lists` dentro del TTL comprobando una sola llamada a Spotify, y verificar la renovación al expirar el TTL (se valida al final en `US4`).

- [X] T010 [US1] Crear `{infra-src}/out/genre/RedisGenreCacheAdapter.java` con `ReactiveStringRedisTemplate` y Jackson 3 (`tools.jackson.databind.ObjectMapper`) para leer y guardar la lista saneada como JSON UTF-8 bajo la clave y TTL configurados (`{spec}/data-model.md`, `Entrada cacheada en Redis.Clave`, `Entrada cacheada en Redis.Contenido`, `Entrada cacheada en Redis.TTL`; `{spec}/contracts/cache-contract.md`)
- [X] T011 [US1] Crear `{infra-src}/out/genre/config/GenreCacheConfig.java` exponiendo `RedisGenreCacheAdapter` o `NoOpGenreCacheAdapter` según `playlist.genres.cache.enabled` (`{spec}/data-model.md`, `Configuración.playlist.genres.cache.enabled`)
- [X] T012 [US1] Actualizar `{app}/usecase/GetGenresUseCase.java` inyectando `GenreCachePort` para leer caché antes de llamar a `GenreProviderPort`, construir `GenreCatalog` con `GenreSource.SPOTIFY` y persistir solo catálogos saneados no vacíos (reutilizado automáticamente por `GET /genres` y `CreatePlaylistUseCase` en `POST /lists`) (`{spec}/data-model.md`, `Entrada cacheada en Redis.Origen asociado`; `{spec}/contracts/cache-contract.md`)
- [X] T013 [US1] Actualizar `{boot}/config/UseCaseConfig.java` inyectando `GenreCachePort` en `GetGenresUseCase`, y declarar `spring.data.redis.*` (`host`, `port`, `password`, `timeout`, `connect-timeout`) y `playlist.genres.cache.*` (`enabled`, `key`, `ttl`, `timeout`) en `{back}/bootstrap/src/main/resources/application.yml` (`{spec}/data-model.md`, `Configuración`)

**Checkpoint**: El acierto de caché durante el TTL y la renovación tras expirar funcionan en `GET /genres` y `POST /lists`.

---

## Phase 4: User Story 2 - No cachear la lista local de respaldo (Priority: P1)

**Goal**: Garantizar que la lista local (`LOCAL`) devuelta ante fallo o respuesta vacía de Spotify jamás se almacene en Redis, permitiendo recuperación inmediata cuando Spotify vuelva a responder.

**Independent Test**: Provocar un fallo de Spotify para recibir `LOCAL`, restaurar Spotify de inmediato y comprobar que la siguiente petición vuelve a llamar a Spotify y devuelve `SPOTIFY` (se valida al final en `US4`).

- [X] T014 [US2] Verificar y reforzar en `{app}/usecase/GetGenresUseCase.java` que la ruta `fromTheFallback` (`GenreSource.LOCAL`) y las respuestas vacías o sin géneros válidos de Spotify nunca invoquen `GenreCachePort.save(...)`, de modo que la siguiente consulta vuelva a consultar Spotify sin esperar TTL (`{spec}/data-model.md`, `Entrada cacheada en Redis.Contenido` y `Entrada cacheada en Redis.Origen asociado`; `{spec}/contracts/cache-contract.md`)

**Checkpoint**: La lista local de respaldo nunca queda almacenada en Redis.

---

## Phase 5: User Story 3 - Seguir respondiendo con Redis caído o lento (Priority: P1)

**Goal**: Garantizar que la API arranque y responda con normalidad ante Redis caído, lento o con datos corruptos, manteniendo `/actuator/health/liveness` y `/actuator/health/readiness` en `200 UP` y reanudando el cacheo cuando Redis vuelve.

**Independent Test**: Detener o ralentizar Redis y verificar respuestas `200` acotadas en tiempo en `GET /genres` y `POST /lists`, `200 UP` en liveness/readiness y reanudación al reiniciar Redis (se valida al final en `US4`).

- [X] T015 [US3] Añadir en `{infra-src}/out/genre/RedisGenreCacheAdapter.java` el operador `.timeout(settings.timeout())` en `find()` y `save(List<String>)`, traduciendo `TimeoutException`, errores de conexión/Redis y valores vacíos o JSON corrupto a `GenreCacheUnavailableException` con `ErrorMessageEnum.GENRE_CACHE_TIMEOUT`, `GENRE_CACHE_UNAVAILABLE` y `GENRE_CACHE_UNREADABLE` sin exponer credenciales (`{spec}/data-model.md`, `Configuración.playlist.genres.cache.timeout`, `Errores`)
- [X] T016 [US3] Completar en `{app}/usecase/GetGenresUseCase.java` la degradación transparente (`onErrorResume` con `LOG.warn` resumido) en lectura y escritura de caché para devolver `SPOTIFY` si falla `save(...)` tras obtener géneros válidos y continuar con Spotify/`LOCAL` si falla `find()` (`{spec}/contracts/cache-contract.md`)
- [X] T017 [P] [US3] Añadir `management.health.redis.enabled: false` en `{back}/bootstrap/src/main/resources/application.yml` para aislar `/actuator/health/liveness` y `/actuator/health/readiness` ante caídas de Redis (`{spec}/data-model.md`, `Configuración.management.health.redis.enabled`)

**Checkpoint**: Todo el código de producción del backend está implementado y tolera Redis caído, lento o corrupto.

---

## Phase 6: User Story 5 - Desplegar Redis en Docker Compose y Kubernetes (Priority: P2)

**Goal**: Integrar el servicio Redis efímero en `compose/compose.yaml` y en los manifiestos endurecidos `k8s/` de `quipux-playlist-infra`, cumpliendo las reglas de seguridad de Kustomize y Trivy.

**Independent Test**: Levantar `compose.yaml` + `compose.wiremock.yaml` comprobando hit y detención de Redis, y validar `kustomize build k8s` + `trivy` sin hallazgos `HIGH`/`CRITICAL`.

- [X] T018 [P] [US5] Añadir en `{infra}/compose/compose.yaml` el servicio `redis` con imagen oficial Alpine fijada (sin `latest`), comando `redis-server --save "" --appendonly no`, sin puertos publicados al host, healthcheck `redis-cli ping`, y entregar `REDIS_HOST=redis`, `REDIS_PORT=6379`, `REDIS_PASSWORD`, `GENRES_CACHE_ENABLED`, `GENRES_CACHE_TTL` y `GENRES_CACHE_TIMEOUT` a `api` sin `depends_on` hacia `redis` (`{spec}/contracts/infrastructure.md`)
- [X] T019 [P] [US5] Crear `{infra}/k8s/redis-deployment.yaml` endurecido (`replicas: 1`, imagen Alpine fijada, `automountServiceAccountToken: false`, UID/GID `999`, `runAsNonRoot: true`, `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`, `capabilities.drop: ["ALL"]`, `seccompProfile: RuntimeDefault`, `emptyDir` en `/tmp`, args `["redis-server", "--save", "", "--appendonly", "no", "--dir", "/tmp"]`, `resources` y sondas `redis-cli ping`) y `{infra}/k8s/redis-service.yaml` (`ClusterIP` `6379`) (`{spec}/contracts/infrastructure.md`)
- [X] T020 [P] [US5] Añadir `REDIS_HOST`, `REDIS_PORT`, `GENRES_CACHE_ENABLED`, `GENRES_CACHE_TTL` y `GENRES_CACHE_TIMEOUT` en `{infra}/k8s/configmap.yaml` (`{spec}/contracts/infrastructure.md`)
- [X] T021 [US5] Registrar `redis-deployment.yaml` y `redis-service.yaml` en `{infra}/k8s/kustomization.yaml` manteniendo `secret.example.yaml` excluido y `api` en `replicas: 1` (`{spec}/contracts/infrastructure.md`)

**Checkpoint**: Todo el código de producción (backend e infraestructura) queda implementado y listo para la batería final de pruebas.

---

## Phase 7: User Story 4 - Verificar automáticamente la caché con pruebas al final (Priority: P1)

**Goal**: Escribir y ejecutar en bloque todas las pruebas unitarias, de configuración, integración con Testcontainers (Redis real + WireMock) y `BlockHound` sobre el código ya implementado, pasando `mvn verify` con cobertura ≥ 80 % y Checkstyle en cero.

**Independent Test**: Ejecutar `./mvnw -B -ntp verify` en `quipux-playlist-backend` con Docker activo y confirmar todas las pruebas en verde sin credenciales ni acceso real a Spotify.

- [X] T022 [P] [US4] Ampliar `{app-test}/usecase/GetGenresUseCaseTest.java` cubriendo acierto de caché sin llamar a Spotify, miss + guardado del catálogo saneado, exclusión de `LOCAL` y degradación ante error/timeout en lectura y en escritura (`{spec}/contracts/cache-contract.md`)
- [X] T023 [P] [US4] Crear `{infra-test}/out/genre/RedisGenreCacheAdapterTest.java` cubriendo serialización JSON, lectura válida, clave ausente, JSON corrupto o vacío, timeout, error reactivo sin exponer credenciales, `NoOpGenreCacheAdapter` y validación en `GenreCacheSettings` (`{spec}/data-model.md`, `Entrada cacheada en Redis` y `Errores`)
- [X] T024 [P] [US4] Ampliar `{boot-test}/StartupConfigurationTest.java` comprobando rechazo al arrancar con `GENRE_ERROR_002` cuando `playlist.genres.cache.enabled=true` y `ttl <= 0`, `timeout <= 0` o `key` en blanco (`{spec}/data-model.md`, `Configuración` y `Errores`)
- [X] T025 [P] [US4] Crear `{boot-test}/GenreCacheRedisIntegrationTest.java` con Testcontainers (Redis real) y WireMock cubriendo: 10 consultas `GET /genres` con 1 llamada a Spotify, reutilización en `POST /lists`, expiración de TTL corto (`2s`), fallo de Spotify (`LOCAL`) sin cachear + recuperación inmediata (`SPOTIFY`) y sobrescritura de entrada corrupta (`{spec}/quickstart.md`)
- [X] T026 [P] [US4] Crear `{boot-test}/GenreCacheRedisDownIntegrationTest.java` con Testcontainers (contenedor detenido y reiniciado) cubriendo: `GET /genres` (`200` `SPOTIFY` y `LOCAL`), `POST /lists` (`201`), latencia acotada (`SC-003`), `/actuator/health/liveness` y `/actuator/health/readiness` en `200 UP`, y reanudación automática al reiniciar Redis (`{spec}/quickstart.md`)
- [X] T027 [P] [US4] Crear `{boot-test}/GenreCacheNonBlockingTest.java` verificando lectura y escritura reales contra Testcontainers Redis bajo `BlockHound` tras calentamiento en `@BeforeAll` (`FR-015`)
- [X] T028 [US4] Ejecutar `./mvnw -B -ntp verify` en `{back}` confirmando todas las pruebas (unitarias, Testcontainers, `BlockHound`, `ArchitectureTest`), cobertura ≥ 80 % por módulo en JaCoCo y 0 violaciones de Checkstyle (`{spec}/quickstart.md`)

**Checkpoint**: Verificación automática integral del backend en verde (`SC-001`–`SC-006`).

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Documentación, cierre de backlog/ADR y validaciones finales de infraestructura y seguridad sin secretos.

- [X] T029 [P] Actualizar `{back}/README.md` (variables de Redis/caché y requisito de Docker para Testcontainers) y `{infra}/README.md` (servicio Redis en Compose y manifiestos en Kubernetes) (`FR-027`)
- [X] T030 [P] Marcar los 4 criterios de HU-10 en `{back}/docs/backlog.md` y anotar las concreciones técnicas de HU-10 en `{back}/docs/adr/0001-arquitectura-api-listas-reproduccion.md` (`FR-028`)
- [X] T031 Validar `{infra}` con `kustomize build k8s`, `trivy config` (`0` `HIGH`/`CRITICAL`), `trivy fs --scanners secret` en `{back}` e `{infra}` (`0` secretos), y probar el flujo completo de caché y caída de Redis en `docker compose -f compose.yaml -f compose.wiremock.yaml` según `{spec}/quickstart.md` (`SC-007`, `SC-008`)
- [X] T032 Revisar `git diff` en `{back}` e `{infra}` verificando ausencia de secretos, comentarios `REVIEW:`/`TODO:`/`FIXME:` y cumplimiento de Conventional Commits (`feat(HU-10): ...`); no se genera `requests.http` porque HU-10 no añade ni modifica endpoints HTTP

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: sin dependencias — inicia de inmediato.
- **Phase 2 (Foundational)**: depende de Phase 1 — bloquea `US1`, `US2`, `US3` y `US4` en backend. (`T004` permite avanzar `US5` en `{infra}` en paralelo en cualquier momento).
- **Phase 3 (`US1`) → Phase 4 (`US2`) → Phase 5 (`US3`)**: secuencia lógica de implementación de producción sobre `GetGenresUseCase` y `RedisGenreCacheAdapter`.
- **Phase 6 (`US5`)**: depende solo de `T004` (rama en `{infra}`); puede ejecutarse en paralelo con Phases 2–5 y debe terminar antes de `T031`.
- **Phase 7 (`US4` — Pruebas al final)**: depende de completar toda la implementación de producción del backend (`Phases 1–5`).
- **Phase 8 (Polish)**: depende de `Phase 6` (`US5`) y `Phase 7` (`US4`).

### Within Each User Story

- En esta historia, por instrucción del usuario, **todo el código de producción (`Phases 1–6`) precede a la escritura y ejecución de pruebas (`Phase 7`)**.
- Dentro de `Phase 2`, `T006`–`T009` son paralelizables entre sí tras `T005`.
- Dentro de `Phase 6` (`US5`), `T018`–`T020` son paralelizables antes de registrar los recursos en `T021`.
- Dentro de `Phase 7` (`US4`), `T022`–`T027` son paralelizables en archivos distintos antes de ejecutar `./mvnw -B -ntp verify` en `T028`.

### Parallel Opportunities

- **Phase 1**: `T002`, `T003` y `T004` en paralelo tras `T001`.
- **Phase 2**: `T006`, `T007`, `T008` y `T009` en paralelo tras `T005`.
- **Phase 6 (`US5`)**: `T018` (`compose.yaml`), `T019` (`redis-deployment.yaml` + `redis-service.yaml`) y `T020` (`configmap.yaml`) en paralelo.
- **Phase 7 (`US4`)**: `T022`–`T027` (todas las suites de prueba nuevas o ampliadas) en paralelo antes de `T028`.
- **Phase 8**: `T029` y `T030` en paralelo antes de `T031` y `T032`.

---

## Parallel Example: Phase 7 (Pruebas al final)

```bash
# Escribir todas las pruebas de HU-10 en paralelo sobre el código implementado:
Task: "T022 [P] [US4] Ampliar {app-test}/usecase/GetGenresUseCaseTest.java"
Task: "T023 [P] [US4] Crear {infra-test}/out/genre/RedisGenreCacheAdapterTest.java"
Task: "T024 [P] [US4] Ampliar {boot-test}/StartupConfigurationTest.java"
Task: "T025 [P] [US4] Crear {boot-test}/GenreCacheRedisIntegrationTest.java"
Task: "T026 [P] [US4] Crear {boot-test}/GenreCacheRedisDownIntegrationTest.java"
Task: "T027 [P] [US4] Crear {boot-test}/GenreCacheNonBlockingTest.java"
```

---

## Implementation Strategy

### MVP First (Phases 1–5 + verificación `US4`)

1. Completar **Phases 1–2** (`Setup` + `Foundational`: dependencias, `GenreCachePort`, `GenreCacheSettings`, `NoOpGenreCacheAdapter`, errores).
2. Completar **Phases 3–5** (`US1` caché con TTL + `US2` exclusión de `LOCAL` + `US3` tolerancia a caída/timeout y sondas `200`).
3. Completar **Phase 6** (`US5`: Redis en `compose.yaml` y `k8s/`).
4. **Ejecutar Phase 7 (`US4`) al final**: escribir todas las pruebas unitarias, Testcontainers y `BlockHound` sobre el código implementado y validar `./mvnw -B -ntp verify`.
5. Cerrar con **Phase 8**: READMEs, `backlog.md`, `ADR-0001`, `kustomize build` + `trivy` y validación en Docker Compose.
