# Research: Caché de géneros en Redis

## R1. Cliente reactivo de Redis y salud de Actuator

- **Decisión**: añadir `org.springframework.boot:spring-boot-starter-data-redis-reactive` en `infrastructure/pom.xml` (gestionado por el parent Spring Boot `4.1.3`, que incluye Lettuce `7.5.2.RELEASE`) y desactivar el indicador automático de salud de Redis (`management.health.redis.enabled: false`).
- **Motivo**:
  - Lettuce es reactivo y no bloqueante sobre Netty, evitando bloqueos en el event loop (`FR-015`, `ADR-0001` sección 5).
  - Con `management.health.probes.enabled: true` ya configurado en `application.yml`, si el `RedisReactiveHealthIndicator` quedara activo y Redis cayera, `/actuator/health/readiness` devolvería `503 DOWN` y Kubernetes sacaría el pod de servicio, violando `FR-012` y `SC-004`.
- **Descartado**:
  - `spring-boot-starter-data-redis` con Jedis: es bloqueante y rompe WebFlux/BlockHound.
  - `@Cacheable` / Spring Cache Abstraction: bloqueante en su modelo clásico y menos explícito para excluir `LOCAL` y controlar timeouts/degradación reactiva sin envoltorios complejos.

## R2. Arquitectura hexagonal y responsabilidad de la caché

- **Decisión**: definir el puerto de salida `GenreCachePort` (`Mono<List<String>> find()`, `Mono<Void> save(List<String> genres)`) en `application/port/out/` e integrar la lectura/escritura en `GetGenresUseCase`.
- **Motivo**:
  - Mantiene `domain` libre de frameworks y `application` dependiendo únicamente de `domain` y Reactor (`ArchitectureTest`, `FR-014`).
  - `GetGenresUseCase` centraliza la regla de negocio:
    1. `readFromCache()`: consulta `GenreCachePort.find()`; ante cualquier error o timeout, registra una advertencia (`LOG.warn`) con el motivo resumido y continúa como si la caché estuviera vacía. Si encuentra lista válida no vacía, devuelve `GenreCatalog(GenreSource.SPOTIFY, genres)`.
    2. `fetchFromProviderAndCache()`: si no hay caché vigente, consulta `provider.genres()`, construye `GenreCatalog(GenreSource.SPOTIFY, genres)` y filtra `!catalog.isEmpty()`. Solo si el catálogo saneado es no vacío invoca `cache.save(catalog.genres())` absorbiendo cualquier fallo de escritura (`onErrorResume`) para devolver siempre el catálogo de Spotify.
    3. `fromTheFallback()`: si el proveedor falla o queda vacío, consulta `fallback.genres()` y devuelve `GenreCatalog(GenreSource.LOCAL, genres)` **sin invocar nunca** `cache.save(...)` (`FR-006`, `FR-007`).
  - `CreatePlaylistUseCase` ya delega en `GetGenresPort.getGenres()`, por lo que hereda automáticamente la caché tanto en `GET /genres` como en la validación de géneros de `POST /lists` sin modificar su código (`FR-003`).
- **Descartado**:
  - Implementar la caché dentro de `SpotifyGenreProviderAdapter`: mezclaría responsabilidades de caché con el cliente HTTP de Spotify y haría más frágil el control de que solo se cacheen géneros ya saneados por `GenreCatalog`.

## R3. Serialización en Redis y manejo de entradas corruptas

- **Decisión**: usar `ReactiveStringRedisTemplate` (de `spring-boot-starter-data-redis-reactive`) y serializar la lista de géneros `List<String>` como un arreglo JSON bajo una única clave configurable (por defecto `playlist:genres:spotify`), usando Jackson 3 (`tools.jackson.databind.ObjectMapper`, ya presente en Spring Boot `4.1.3`).
- **Motivo**:
  - Guardar JSON legible en UTF-8 evita depender de la serialización binaria de Java (insegura y acoplada a clases) y facilita la inspección manual en Redis durante evaluación o depuración.
  - Si Redis devuelve un valor vacío, JSON inválido o una lista que tras `GenreCatalog` queda vacía, el adaptador traduce el fallo a `GenreCacheUnavailableException` (mensaje sin datos sensibles) para que `GetGenresUseCase` registre la advertencia, consulte Spotify y sobrescriba la entrada corrupta si Spotify responde con éxito.
- **Descartado**:
  - `JdkSerializationRedisSerializer`: acoplado a versiones de clases Java y opaco para diagnóstico.
  - `Jackson2JsonRedisSerializer`: Spring Boot `4.1.3` usa Jackson 3 (`tools.jackson.*`); usar `ReactiveStringRedisTemplate` con `ObjectMapper` explícito evita incompatibilidades y da control total de errores.

## R4. Timeouts, validación al arranque y reconexión automática

- **Decisión**:
  - Configurar `spring.data.redis.timeout` y `spring.data.redis.connect-timeout` a `1s` por defecto (`REDIS_TIMEOUT`, `REDIS_CONNECT_TIMEOUT`) junto con `.timeout(settings.timeout())` de Reactor (`playlist.genres.cache.timeout`, `1s` por defecto) en `find()` y `save()`.
  - Validar en `GenreCacheSettings` al arrancar (cuando `playlist.genres.cache.enabled=true`) que la clave no sea blanca y que `ttl` y `timeout` sean estrictamente positivos (`> 0`), lanzando una excepción con código `GENRE_ERROR_002` (`INVALID_GENRE_CONFIGURATION`) si no se cumplen (`FR-017`).
  - Crear `GenreCacheUnavailableException` y registrar nuevos valores en `ErrorMessageEnum` bajo un código propio `ResponseCodeEnum.GENRE_CACHE_UNAVAILABLE("GENRE_ERROR_003")` para diferenciar en logs los avisos de Redis de los de Spotify (`GENRE_ERROR_001`), sin alterar ninguna respuesta HTTP.
  - Apoyarse en la conexión perezosa y reconexión automática (`autoReconnect`) de Lettuce: si Redis está caído al iniciar, la API arranca con normalidad y reanuda el cacheo en cuanto Redis vuelve (`FR-009`, `FR-011`).
- **Motivo**:
  - Un timeout de Reactor acota toda la operación reactiva (conexión + comando + respuesta) a `1s` incluso si el cliente subyacente tardara más en cortar, manteniendo `SC-003` (< 2 s con Spotify rápido y < 5 s con Redis y Spotify caídos).
  - Nunca encadenar causas crudas de excepciones externas en mensajes de log expuestos si pudieran contener credenciales de conexión.
- **Descartado**:
  - Timeout por defecto largo (60 s de Lettuce): dejaría bloqueada la respuesta durante demasiado tiempo cuando Redis no responde.

## R5. Aislamiento de pruebas existentes y `NoOpGenreCacheAdapter`

- **Decisión**: proveer dos implementaciones de `GenreCachePort` seleccionadas por configuración (`playlist.genres.cache.enabled`, `true` por defecto en `application.yml`): `RedisGenreCacheAdapter` cuando está habilitada y `NoOpGenreCacheAdapter` cuando está deshabilitada. En `bootstrap/src/test/resources/config/application.yml`, establecer `playlist.genres.cache.enabled: false`.
- **Motivo**:
  - Mantiene intactas y rápidas todas las pruebas previas (`GenresApiTest`, donde `SPOTIFY.verify(2, ...)` espera dos llamadas a WireMock, `PlaylistGenreValidationApiTest`, `PlaylistNonBlockingTest`, `StartupConfigurationTest`, etc.) siguiendo exactamente el mismo patrón ya empleado en `bootstrap/src/test/resources/config/application.yml` para la IA (`spring.ai.model.chat: none`).
  - Permite ejecutar la aplicación localmente sin levantar Redis cuando se deshabilita explícitamente la caché (`playlist.genres.cache.enabled=false`) o incluso con `enabled=true` gracias a la tolerancia a Redis caído.
- **Descartado**:
  - Mantener Redis activado para toda la suite de `bootstrap`: ralentizaría todas las pruebas previas y rompería aserciones existentes de HU-04 diseñadas sin caché (`SPOTIFY.verify(2, ...)`).

## R6. Pruebas de integración con Testcontainers `2.0.5` y `BlockHound`

- **Decisión**: añadir en `bootstrap/pom.xml` (`scope: test`) `org.springframework.boot:spring-boot-testcontainers` y `org.testcontainers:testcontainers-junit-jupiter` (ambos gestionados por el BOM de Spring Boot `4.1.3` con Testcontainers `2.0.5`), utilizando `GenericContainer<?>` con la misma imagen Alpine fijada de Redis que en infraestructura.
- **Motivo**:
  - Evita dependencias de terceros fuera del BOM oficial de Spring Boot y mantiene la superficie de vulnerabilidades de Trivy bajo control.
  - Permite detener y arrancar el contenedor Redis real en pruebas dedicadas (`GenreCacheRedisIntegrationTest` y `GenreCacheRedisDownIntegrationTest`) y usar `@DynamicPropertySource` con `playlist.genres.cache.enabled=true` y TTL corto (p. ej. `2s`) para probar expiración real.
  - Para `FR-015` (`BlockHound`), añadir `GenreCacheNonBlockingTest` con su propia ejecución aislada en `maven-surefire-plugin` (igual que `PlaylistNonBlockingTest` y `RecommendationNonBlockingTest`, porque `BlockHound` solo puede instalarse una vez por JVM) probando lectura y escritura reales contra Testcontainers Redis bajo `BlockHound`.
- **Descartado**:
  - Redis embebido en Java: no reproduce fielmente el protocolo de red ni el comportamiento real de un contenedor Redis caído/reiniciado exigido por el backlog (`HU-10`) y el `ADR-0001`.

## R7. Infraestructura en `quipux-playlist-infra` (Docker Compose y Kubernetes)

- **Decisión**:
  - **Docker Compose (`compose/compose.yaml`)**: añadir servicio `redis` con imagen oficial Alpine fijada (`redis:<version-alpine>` sin `latest`, verificada sin hallazgos `HIGH`/`CRITICAL` en Trivy al implementar), comando `redis-server --save "" --appendonly no`, sin publicación de puertos al host (solo accesible en la red interna de Compose) y `healthcheck` con `redis-cli ping`. Pasar `REDIS_HOST=redis`, `REDIS_PORT=6379`, `REDIS_PASSWORD`, `GENRES_CACHE_ENABLED`, `GENRES_CACHE_TTL` y `GENRES_CACHE_TIMEOUT` al servicio `api` **sin** `depends_on` hacia `redis`, para que `api` arranque y siga operando aunque `redis` esté detenido.
  - **Kubernetes (`k8s/`)**: añadir `redis-deployment.yaml` y `redis-service.yaml` (`ClusterIP` puerto `6379`), actualizar `configmap.yaml` (`REDIS_HOST: redis.quipux-playlist.svc.cluster.local`, `REDIS_PORT: "6379"`, `GENRES_CACHE_ENABLED: "true"`, `GENRES_CACHE_TTL: "24h"`, `GENRES_CACHE_TIMEOUT: "1s"`) y registrar ambos recursos en `kustomization.yaml`. Endurecer `redis-deployment.yaml` con `automountServiceAccountToken: false`, `runAsNonRoot: true`, UID/GID `999`, `seccompProfile: RuntimeDefault`, `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`, `capabilities.drop: ["ALL"]`, `emptyDir` en `/tmp`, argumentos `redis-server --save "" --appendonly no --dir /tmp`, `resources` acotados y sondas `exec` (`redis-cli ping`).
- **Motivo**:
  - Cumple `FR-023`–`FR-026`, la regla de no exponer puertos innecesarios en Compose y las exigencias de `trivy config` y `kustomize build` del CI de infra (manteniendo `api` con `replicas: 1` y cero `Secret` dentro de `kustomization.yaml`).
- **Descartado**:
  - Añadir `depends_on: redis: condition: service_healthy` en `compose.yaml`: impediría arrancar `api` cuando Redis está caído, contradiciendo el principio de resiliencia de `HU-10`.

## R8. Estrategia de implementación rápida (pruebas al final)

- **Decisión**: por petición explícita del usuario, implementar primero el código de producción en backend (`domain` → `application` → `infrastructure` → `bootstrap`) y los artefactos de infraestructura en `quipux-playlist-infra`, y escribir y ejecutar al final todo el bloque de pruebas (unitarias, integración con Testcontainers, `BlockHound`, `StartupConfigurationTest`) junto con la verificación integral (`./mvnw verify`, `kustomize build`, `trivy`).
- **Motivo**: acelera la iteración sobre el código compilado manteniendo exactamente los mismos controles de calidad y cobertura ≥ 80 % antes de entregar la historia.
- **Descartado**: flujo TDD estricto por fases incrementales, descartado explícitamente por el usuario para esta entrega.
