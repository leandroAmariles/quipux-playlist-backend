---
description: "Tareas de HU-02: gestión de listas de reproducción"
---

# Tasks: Gestión de listas de reproducción

**Input**: documentos de diseño en `specs/02-playlist-management/`

**Tests**: incluidas (SC-002 y plan). En cada historia las pruebas se escriben primero y se confirma que fallan.

**Rutas**: relativas a la raíz de `quipux-playlist-backend`, con estos prefijos:

| Prefijo | Ruta |
|---|---|
| `{domain}` | `domain/src/main/java/com/quipux/playlist/domain` |
| `{app}` | `application/src/main/java/com/quipux/playlist/application` |
| `{infra}` | `infrastructure/src/main/java/com/quipux/playlist/infrastructure` |
| `{boot}` | `bootstrap/src/main/java/com/quipux/playlist` |
| `{domain-test}` | `domain/src/test/java/com/quipux/playlist/domain` |
| `{app-test}` | `application/src/test/java/com/quipux/playlist/application` |
| `{infra-test}` | `infrastructure/src/test/java/com/quipux/playlist/infrastructure` |
| `{boot-test}` | `bootstrap/src/test/java/com/quipux/playlist` |

**En todas las tareas**: identificadores en inglés, líneas de 121 caracteres como máximo, llaves obligatorias y sin
`.block()`, tampoco en pruebas de código reactivo (se usa `StepVerifier` o `WebTestClient`).

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Añadir a `pom.xml` la propiedad `mapstruct.version` 1.6.3 y gestionar `org.mapstruct:mapstruct` (R9 de
      `research.md`)
- [X] T002 [P] Añadir a `application/pom.xml` en ámbito `test`: `junit-jupiter`, `assertj-core`,
      `mockito-junit-jupiter` y `reactor-test`
- [X] T003 [P] Configurar `infrastructure/pom.xml`: dependencia `mapstruct`, `mapstruct-processor` como procesador de
      anotaciones y, en `test`, `spring-boot-starter-test`, `spring-boot-starter-webflux-test`,
      `spring-boot-starter-data-r2dbc-test` y `reactor-test`
- [X] T004 [P] Añadir a `bootstrap/pom.xml` en `test` `spring-boot-starter-webflux-test`
- [X] T005 Ejecutar `./mvnw verify`: los artefactos nuevos se resuelven y el proyecto sigue en verde

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: el modelo de dominio y sus reglas, que usan las cuatro historias.

- [X] T006 [P] Crear `{domain-test}/model/PlaylistTest.java`: cada regla de `data-model.md` para `Playlist.name`
      (FR-004, FR-010, sin recorte), `Playlist.description` y `Playlist.songs` (nulo, elemento nulo, copia inmutable,
      orden); cada límite con el valor exacto y con un carácter más
- [X] T007 [P] Crear `{domain-test}/model/SongTest.java`: todos los campos nulos se aceptan; valor exacto y uno más
      para `Song.title`, `Song.artist`, `Song.album`, `Song.releaseYear` y `Song.genre` de `data-model.md`
- [X] T008 [P] Crear `{domain}/model/constants/PlaylistConstraints.java` (`final`, constructor privado) con los
      máximos de `data-model.md`: `Playlist.name`, `Playlist.description`, `Song.title`, `Song.artist`, `Song.album`,
      `Song.releaseYear` y `Song.genre`
- [X] T009 [P] Crear `{domain}/model/enums/ResponseCodeEnum.java` con los cuatro códigos de
      `contracts/rest-api.md`, sin tipos HTTP
- [X] T010 Crear en `{domain}/exception/` `BusinessException` (código y mensaje) y sus subclases
      `InvalidPlaylistException`, `PlaylistNotFoundException` y `DuplicatePlaylistException`, con mensajes en inglés
      que no revelen datos internos
- [X] T011 Crear `{domain}/model/Song.java` como `record` que valida `data-model.md` `Song.title`, `Song.artist`,
      `Song.album`, `Song.releaseYear` y `Song.genre`
- [X] T012 Crear `{domain}/model/Playlist.java` como `record` que valida `data-model.md` `Playlist.name`,
      `Playlist.description` y `Playlist.songs`
- [X] T013 [P] Crear el soporte de pruebas de infraestructura: `{infra-test}/TestApplication.java`
      (`@SpringBootApplication`) e `infrastructure/src/test/resources/application.yml` con H2 en memoria y
      `spring.sql.init.mode=always`

**Checkpoint**: `./mvnw -pl domain verify` en verde y con cobertura.

---

## Phase 3: User Story 1 - Crear una lista (Priority: P1) 🎯 MVP

**Goal**: `POST /lists` guarda la lista con sus canciones o no guarda nada.

**Independent Test**: respuestas de `POST /lists` de `contracts/rest-api.md` y contenido de las tablas.

### Tests for User Story 1

- [X] T014 [P] [US1] Crear `{app-test}/usecase/CreatePlaylistUseCaseTest.java`: guarda y devuelve la lista; propaga
      `DuplicatePlaylistException` y un error inesperado del puerto
- [X] T015 [P] [US1] Crear `{infra-test}/out/persistence/PlaylistPersistenceCreateTest.java` (`@DataR2dbcTest`,
      limpiando las tablas antes de cada prueba): guarda las canciones en orden; nombre repetido da
      `DuplicatePlaylistException` sin cambiar la original; "Rock", "rock" y " Rock " conviven; con una restricción
      `CHECK` temporal que rechace la segunda canción no queda ninguna fila (FR-008), y la restricción se retira al
      terminar
- [X] T016 [P] [US1] Crear `{infra-test}/in/rest/PlaylistControllerCreateTest.java` (`@WebFluxTest`, puerto con
      `@MockitoBean`, `@Import` de la configuración de JSON): `201`, `Location` codificada y cuerpo con todos los
      campos; campos desconocidos ignorados; `400` con `PLAYLIST_GE_00001` para cada caso de "Edge Cases" y FR-011
      (sin cuerpo, `null`, JSON mal formado, número o booleano donde va texto, `canciones` que no es arreglo, canción
      nula, nombre no válido, longitud excedida); `409`; `415`; `500` con `detail` genérico
- [X] T017 [P] [US1] Crear `{boot-test}/PlaylistApiCreateTest.java` con servidor real y
      `@AutoConfigureWebTestClient`: `201` y `409`; varias peticiones simultáneas con el mismo nombre dan un `201` y
      el resto `409`; un cuerpo mayor de 256 KB da `413` (R8)

### Implementation for User Story 1

- [X] T018 [P] [US1] Crear `{app}/port/in/CreatePlaylistPort.java`: `Mono<Playlist> create(Playlist)`
- [X] T019 [P] [US1] Crear `{app}/port/out/PlaylistRepositoryPort.java`: `Mono<Playlist> save(Playlist)`
- [X] T020 [US1] Crear `{app}/usecase/CreatePlaylistUseCase.java`
- [X] T021 [P] [US1] Crear `PlaylistEntity` y `SongEntity` como `record` en `{infra}/out/persistence/entity/`, según
      la tabla de persistencia de `data-model.md`
- [X] T022 [P] [US1] Crear `PlaylistDto` y `SongDto` en `{infra}/in/rest/dto/` con los nombres JSON de la tabla de
      DTOs de `data-model.md`
- [X] T023 [US1] Crear `{infra}/out/persistence/mapper/PlaylistEntityMapper.java` (MapStruct, dominio a entidades)
- [X] T024 [US1] Crear `{infra}/in/rest/mapper/PlaylistDtoMapper.java` (MapStruct, en ambos sentidos)
- [X] T025 [US1] Crear `PlaylistR2dbcRepository` y `SongR2dbcRepository` en `{infra}/out/persistence/repository/`
- [X] T026 [US1] Crear `{infra}/out/persistence/PlaylistPersistenceAdapter.java` con `save`: `TransactionalOperator`,
      canciones con `concatMap` y `DuplicateKeyException` traducida solo en la inserción de la lista (R1 a R3)
- [X] T027 [P] [US1] Crear `{infra}/in/rest/config/JacksonStrictTypesConfig.java`: número, decimal y booleano no se
      convierten en texto (R6)
- [X] T028 [P] [US1] Crear `{infra}/in/rest/handler/GlobalExceptionHandler.java` extendiendo
      `ResponseEntityExceptionHandler`: `ProblemDetail` con `code` según `contracts/rest-api.md`, reserva para errores
      inesperados con registro en log y sin datos internos (R8)
- [X] T029 [US1] Crear `{infra}/in/rest/PlaylistController.java` con `POST /lists`: solo mapea, llama al puerto y arma
      `201` con `Location` codificada con `UriUtils.encode` (R7)
- [X] T030 [US1] Crear `{boot}/config/UseCaseConfig.java` con el bean de `CreatePlaylistPort`
- [X] T031 [US1] Ejecutar `./mvnw verify`: pruebas, cobertura por módulo, Checkstyle y arquitectura en verde

**Checkpoint**: se pueden crear listas.

---

## Phase 4: User Story 2 - Consultar todas las listas (Priority: P1)

**Goal**: `GET /lists` devuelve todas las listas con sus canciones, en orden.

**Independent Test**: respuesta vacía y respuesta con listas creadas antes.

### Tests for User Story 2

- [X] T032 [P] [US2] Crear `{app-test}/usecase/GetPlaylistsUseCaseTest.java`: varias, ninguna y error del puerto
- [X] T033 [P] [US2] Crear `{infra-test}/out/persistence/PlaylistPersistenceFindAllTest.java`: sin listas no emite
      nada; listas en orden de creación; canciones propias y en el orden enviado; lista sin canciones con colección
      vacía (FR-012, FR-013)
- [X] T034 [P] [US2] Crear `{infra-test}/in/rest/PlaylistControllerListTest.java`: `200` con arreglo y con `[]`;
      opcionales como `null`; sin identificadores internos (FR-001)
- [X] T035 [P] [US2] Crear `{boot-test}/PlaylistApiListTest.java`: lo creado por `POST` aparece en `GET /lists`, en
      orden (escenarios 1 y 4)

### Implementation for User Story 2

- [X] T036 [P] [US2] Crear `{app}/port/in/GetPlaylistsPort.java` y `{app}/usecase/GetPlaylistsUseCase.java`, y añadir
      `findAll` a `{app}/port/out/PlaylistRepositoryPort.java`
- [X] T037 [P] [US2] Crear `{infra}/out/persistence/entity/PlaylistSongRow.java` según `data-model.md`
- [X] T038 [US2] Añadir la consulta `LEFT JOIN` a `{infra}/out/persistence/repository/PlaylistR2dbcRepository.java` y
      el mapeo de filas a `{infra}/out/persistence/mapper/PlaylistEntityMapper.java`
- [X] T039 [US2] Implementar `findAll` en `{infra}/out/persistence/PlaylistPersistenceAdapter.java` agrupando con
      `bufferUntilChanged` (R4)
- [X] T040 [US2] Añadir `GET /lists` a `{infra}/in/rest/PlaylistController.java` y el bean a
      `{boot}/config/UseCaseConfig.java`
- [X] T041 [US2] Ejecutar `./mvnw verify`

**Checkpoint**: crear y listar funcionan.

---

## Phase 5: User Story 3 - Consultar una lista (Priority: P1)

**Goal**: `GET /lists/{listName}` devuelve la lista exacta o `404`.

**Independent Test**: consultar una lista creada y una inexistente.

### Tests for User Story 3

- [X] T042 [P] [US3] Crear `{app-test}/usecase/GetPlaylistUseCaseTest.java`: encontrada; vacío da
      `PlaylistNotFoundException`
- [X] T043 [P] [US3] Crear `{infra-test}/out/persistence/PlaylistPersistenceFindByNameTest.java`: coincidencia exacta
      (mayúsculas y espacios), canciones en orden, sin canciones y nombre inexistente
- [X] T044 [P] [US3] Crear `{infra-test}/in/rest/PlaylistControllerGetTest.java`: `200` y `404` con
      `PLAYLIST_GE_00002`
- [X] T045 [P] [US3] Crear `{boot-test}/PlaylistApiGetTest.java`: por cada nombre de R7 y "Edge Cases" (espacios,
      tildes, `ñ`, `;`, `+`, `&`, `?`, `#`, `%`, `%2F`, espacios alrededor), crear y seguir `Location` sin volver a
      codificarla (`URI.create` con host y puerto) y obtener la misma lista (FR-009, escenario 5 de US1)

### Implementation for User Story 3

- [X] T046 [US3] Crear `{app}/port/in/GetPlaylistPort.java` y `{app}/usecase/GetPlaylistUseCase.java`, y añadir
      `findByName` a `{app}/port/out/PlaylistRepositoryPort.java`
- [X] T047 [US3] Añadir la consulta filtrada a `{infra}/out/persistence/repository/PlaylistR2dbcRepository.java` e
      implementar `findByName` en `{infra}/out/persistence/PlaylistPersistenceAdapter.java`
- [X] T048 [US3] Añadir `GET /lists/{listName}` a `{infra}/in/rest/PlaylistController.java` y el bean a
      `{boot}/config/UseCaseConfig.java`
- [X] T049 [US3] Ejecutar `./mvnw verify`

**Checkpoint**: la dirección que devuelve la creación funciona.

---

## Phase 6: User Story 4 - Borrar una lista (Priority: P1)

**Goal**: `DELETE /lists/{listName}` borra la lista y sus canciones, o nada.

**Independent Test**: borrar, consultar y volver a borrar.

### Tests for User Story 4

- [X] T050 [P] [US4] Crear `{app-test}/usecase/DeletePlaylistUseCaseTest.java`: borrada; `false` da
      `PlaylistNotFoundException`; propaga errores
- [X] T051 [P] [US4] Crear `{infra-test}/out/persistence/PlaylistPersistenceDeleteTest.java`: borra lista y canciones;
      las demás conservan las suyas; inexistente da `false`; recrear el nombre no trae canciones antiguas; con una
      tabla temporal que referencie `playlist.id` el borrado falla y las canciones siguen (FR-016), y la tabla se
      elimina al terminar
- [X] T052 [P] [US4] Crear `{infra-test}/in/rest/PlaylistControllerDeleteTest.java`: `204` sin cuerpo y `404`
- [X] T053 [P] [US4] Crear `{boot-test}/PlaylistApiDeleteTest.java`: crear, borrar, consultar (`404`) y volver a
      borrar (`404`); borrados simultáneos dan un `204` y el resto `404`; los nombres de T045 se borran con su
      `Location` (SC-004)

### Implementation for User Story 4

- [X] T054 [US4] Crear `{app}/port/in/DeletePlaylistPort.java` y `{app}/usecase/DeletePlaylistUseCase.java`, y añadir
      `Mono<Boolean> deleteByName(String)` a `{app}/port/out/PlaylistRepositoryPort.java`
- [X] T055 [US4] Añadir las consultas de borrado a los repositorios de `{infra}/out/persistence/repository/` e
      implementar `deleteByName` en `{infra}/out/persistence/PlaylistPersistenceAdapter.java`: una transacción,
      canciones primero y resultado según las filas borradas de la lista (R3)
- [X] T056 [US4] Añadir `DELETE /lists/{listName}` a `{infra}/in/rest/PlaylistController.java` y el bean a
      `{boot}/config/UseCaseConfig.java`
- [X] T057 [US4] Ejecutar `./mvnw verify`

**Checkpoint**: las cuatro operaciones del enunciado funcionan.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T058 Añadir BlockHound 1.0.19.RELEASE en `test` a `bootstrap/pom.xml`, con
      `argLine` `@{argLine} -XX:+AllowRedefinitionToAddDeleteMethods`, y crear `{boot-test}/PlaylistNonBlockingTest.java`:
      control que demuestra que detecta un bloqueo en `parallel` y ciclo de las cuatro operaciones sin bloqueos
      (FR-017, riesgos en R10). No permitir en la lista blanca llamadas de H2 o del proyecto sin avisar al usuario
- [X] T059 [P] Crear `{boot-test}/PlaylistPerformanceTest.java`: con 100 listas de 20 canciones cada operación tarda
      menos de 1 segundo (SC-006)
- [X] T060 [P] Generar `specs/02-playlist-management/requests.http` según la regla de peticiones HTTP del equipo, con
      un caso por requisito y escenario de `spec.md`
- [X] T061 [P] Actualizar `README.md`: endpoints, enlace al contrato, aviso de que no hay autenticación hasta HU-03 y
      que `r2dbc-h2` ejecuta en el hilo que llama (R10)
- [X] T062 Ejecutar el recorrido manual de `specs/02-playlist-management/quickstart.md`
- [X] T063 Actualizar el estado de cada decisión de `specs/02-playlist-management/research.md` con el resultado real
- [X] T064 Comprobar que cada criterio de HU-02 tiene una prueba (SC-002) y marcarlo en `docs/backlog.md`
- [X] T065 Ejecutar `./mvnw clean verify` y revisar `git status` y el diff completo: sin archivos temporales ni
      secretos
- [X] T066 Con la aprobación del usuario: commits atómicos `…(HU-02): …` en `feature/02-playlist-management`, push y
      PR hacia `main`

---

## Dependencies & Execution Order

- Setup (T001 a T005) → Foundational (T006 a T013) → US1 → US2 → US3 → US4 → Polish.
- Las historias van en orden porque editan los mismos archivos: puerto de salida, adaptador, repositorio, controlador y
  `UseCaseConfig`.
- US2 y US3 usan la creación para preparar datos de extremo a extremo; US4 usa además la consulta.
- El escenario 5 de US1 (seguir `Location`) se comprueba en US3.
- T058 y T059 necesitan las cuatro operaciones. T066 es la única tarea que publica algo.

## Parallel Opportunities

- Setup: T002, T003, T004
- Foundational: T006 a T009, y T013
- US1: T014 a T017; después T018, T019, T021, T022; y T027 con T028
- US2: T032 a T035; T036 y T037
- US3: T042 a T045
- US4: T050 a T053
- Polish: T059, T060, T061

## Parallel Example: User Story 1

```text
T014  {app-test}/usecase/CreatePlaylistUseCaseTest.java
T015  {infra-test}/out/persistence/PlaylistPersistenceCreateTest.java
T016  {infra-test}/in/rest/PlaylistControllerCreateTest.java
T017  {boot-test}/PlaylistApiCreateTest.java
```

## Implementation Strategy

**MVP**: Setup, Foundational y US1. **Entregable**: las cuatro historias, porque todas son P1 y una HU a medias no se
entrega. En cada checkpoint se valida antes de seguir.

Las pruebas de una fase compilan solo cuando existen sus tipos: "fallan" significa primero error de compilación y, con
las firmas creadas, fallo de la prueba. La base en memoria se comparte dentro de un módulo, así que cada prueba limpia
o usa nombres propios.
