---
description: "Tareas de HU-06: Postman, OpenAPI y README"
---

# Tasks: Postman, OpenAPI y README

**Input**: documentos de diseño en `specs/06-postman-openapi-readme/`

**Tests**: incluidas. Las pruebas se escriben primero y se confirma que fallan (que no compilen cuenta como fallo).

**Orden de las historias**: las tres son P1. Se sigue el orden del plan: US3 (contrato), US2 (Postman) y US1 (README),
porque el README describe lo que entregan las otras dos.

**Rutas**: relativas a la raíz de `quipux-playlist-backend`, con estos prefijos:

| Prefijo | Ruta |
|---|---|
| `{rest}` | `infrastructure/src/main/java/com/quipux/playlist/infrastructure/in/rest` |
| `{rest-test}` | `infrastructure/src/test/java/com/quipux/playlist/infrastructure/in/rest` |
| `{boot-test}` | `bootstrap/src/test/java/com/quipux/playlist` |
| `{boot-res}` | `bootstrap/src/test/resources` |
| `{spec}` | `specs/06-postman-openapi-readme` |

**En todas las tareas**:

- Identificadores en inglés, líneas de 121 caracteres como máximo, llaves obligatorias y sin imports sin usar.
- Sin `.block()`, también en pruebas, y sin comentarios ni Javadoc.
- Textos y valores fijos en enums o constantes; listas de datos, también de pruebas, en `.properties`; `@Value` solo en
  atributos de clase.
- Textos del contrato, de la colección y del README en español.
- Ningún secreto, contraseña real ni token en el repositorio.
- Las operaciones existentes no cambian su comportamiento (FR-020) y sus pruebas no se modifican, salvo
  `PlaylistNonBlockingTest`, que solo se amplía.

## Phase 1: Setup

- [X] T001 Añadir la propiedad `springdoc.version` (3.2.0) y la dependencia gestionada
      `org.springdoc:springdoc-openapi-starter-webflux-ui` en `pom.xml`, y la dependencia en `infrastructure/pom.xml`
      (research R1)
- [X] T002 Ejecutar `./mvnw verify` sobre `pom.xml` y confirmar que las pruebas existentes siguen en verde con la
      dependencia

## Phase 2: Foundational

No hay tareas: ninguna pieza es común a las tres historias.

## Phase 3: User Story 3 - Consultar el contrato documentado (P1) 🎯 MVP

**Goal**: contrato OpenAPI y Swagger UI públicos, con las seis operaciones, sus códigos y Bearer JWT.

**Independent Test**: abrir la documentación sin sesión, iniciar sesión desde ella, autorizar y ejecutar `GET /lists`.

### Tests

- [X] T003 [P] [US3] Crear `{boot-res}/openapi-operations.properties` copiando de `{spec}/contracts/openapi.md`
      (no del código) método, ruta, rol y códigos de cada operación, y `{boot-test}/OpenApiContractTest.java`, que
      compara `/v3/api-docs` con ese archivo: operaciones y códigos exactos, sin ninguna de más (FR-012); `bearerAuth`
      en todas salvo el inicio de sesión; rol en la descripción; tipos de contenido; `Location` en el `201`; `204` sin
      cuerpo; esquemas con obligatorios y máximos de `data-model.md` (Esquemas); `info.version` resuelta y sin
      contraseñas ni tokens (FR-013)
- [X] T004 [P] [US3] Crear `{boot-test}/DocumentationAccessApiTest.java` según `{spec}/contracts/openapi.md` (Rutas
      nuevas): las públicas responden sin token y todos los archivos que pide la interfaz cargan; `/v3/api-docs.yaml`,
      `/webjars/**`, `POST` sobre las rutas de documentación, `/lists` y `/genres` siguen en `401`
- [X] T005 [P] [US3] Ampliar `{boot-test}/PlaylistNonBlockingTest.java` con el contrato (con dos `Accept-Language`) y
      los archivos de Swagger UI. No incluirlos en el calentamiento: la primera petición es la que bloquea (research R4)
- [X] T006 [P] [US3] Crear `{rest-test}/doc/DocumentationSchedulingFilterTest.java`: las rutas de documentación
      continúan la cadena en `boundedElastic` y las demás en el hilo original
- [X] T007 [P] [US3] Crear `{rest-test}/doc/OpenApiContractCustomizerTest.java`: a partir de un `OpenAPI` como el que
      springdoc genera por defecto (research R2), comprobar éxito `201` y `204`, errores con `Problem`, `Location`,
      seguridad y que una operación sin entrada en `ApiOperationDocEnum` queda sin completar

### Implementation

- [X] T008 [US3] Crear `{rest}/doc/DocumentationPath.java` (`data-model.md` `DocumentationPath`) y permitir solo `GET`
      en esos patrones en `{rest}/config/SecurityConfig.java`
- [X] T009 [US3] Crear `{rest}/doc/DocumentationSchedulingFilter.java`: `WebFilter` que aplica
      `subscribeOn(Schedulers.boundedElastic())` solo a las rutas de `DocumentationPath`
- [X] T010 [P] [US3] Añadir `springdoc.default-produces-media-type: application/json` y
      `playlist.openapi.version: "@project.version@"` a `bootstrap/src/main/resources/application.yml` (research R6)
- [X] T011 [P] [US3] Añadir `@Schema` a `{rest}/dto/PlaylistDto.java`, `SongDto.java`, `LoginRequestDto.java`,
      `TokenResponseDto.java` y `GenresResponseDto.java`, con las longitudes de `PlaylistConstraints` y los ejemplos de
      `data-model.md`: `PlaylistDto.nombre`, `PlaylistDto.descripcion`, `PlaylistDto.canciones`, `SongDto.titulo`,
      `SongDto.artista`, `SongDto.album`, `SongDto.anno`, `SongDto.genero`, `LoginRequestDto.username`,
      `LoginRequestDto.password`, `TokenResponseDto.accessToken`, `TokenResponseDto.tokenType`,
      `TokenResponseDto.expiresIn`, `GenresResponseDto.generos` y `GenresResponseDto.origen`
- [X] T012 [US3] Crear `{rest}/doc/ApiResponseDocEnum.java` y `ApiOperationDocEnum.java` según `data-model.md` y
      `{spec}/contracts/openapi.md`, haciendo pública en `{rest}/PlaylistController.java` la ruta de una lista
- [X] T013 [US3] Crear `{rest}/doc/OpenApiContractCustomizer.java` (éxito, errores, esquema `data-model.md`
      `Problem.type`, `Problem.title`, `Problem.status`, `Problem.detail`, `Problem.instance` y `Problem.code`,
      `Location`, `bearerAuth` y rol) y ensamblarlo con `info` en `{rest}/config/OpenApiConfig.java`
- [X] T014 [US3] Ejecutar `./mvnw verify` y, con el JAR empaquetado y arrancado, el paso 3 de `{spec}/quickstart.md`

**Checkpoint**: la documentación se usa desde el navegador y `verify` está en verde.

## Phase 4: User Story 2 - Probar la API con un cliente configurado (P1)

**Goal**: colección de Postman completa, sin secretos y repetible.

**Independent Test**: importar la colección, escribir las dos contraseñas y ejecutarla dos veces.

### Tests

- [X] T015 [US2] Crear `{boot-test}/PostmanCollectionTest.java`, que lee
      `../postman/quipux-playlist.postman_collection.json`: formato v2.1; las seis operaciones y los estados `400`,
      `401`, `403`, `404` y `409`; cada petición con una comprobación; contraseñas y tokens vacíos; los inicios de
      sesión correctos usan las variables; ningún JWT ni clave (FR-001 a FR-006). Las listas esperadas van en
      `{boot-res}/postman-collection.properties`

### Implementation

- [X] T016 [US2] Crear `postman/quipux-playlist.postman_collection.json` según `{spec}/contracts/postman-collection.md`,
      con las variables de `data-model.md`: `baseUrl`, `userPassword`, `adminPassword`, `userToken`, `adminToken`,
      `listName` y `genre`; la descripción explica las contraseñas y la vigencia del token
- [X] T017 [US2] Ejecutar el paso 4 de `{spec}/quickstart.md` contra la API recién arrancada: dos veces seguidas sin
      fallos (SC-002) y una tercera con la lista de prueba creada de antemano (ejecución interrumpida)

**Checkpoint**: la colección pasa completa y repetida.

## Phase 5: User Story 1 - Ejecutar el proyecto con las instrucciones (P1)

**Goal**: README suficiente para probar el proyecto sin ayuda.

**Independent Test**: desde una copia limpia, siguiendo solo el README, arrancar, probar y crear una lista.

- [X] T018 [US1] Reorganizar `README.md` (FR-014 a FR-019), en este orden: qué es; R2DBC en lugar de JPA (el enunciado
      pide JPA, motivo y consecuencias según ADR-0001, sección 1, y consulta aún sin respuesta); requisitos; variables
      de entorno; ejecución; pruebas; documentación interactiva; colección de Postman; aplicación web; API; limitación
      de Spotify (ADR-0001, sección 10, y `specs/04-genres/quickstart.md`); estructura; repositorios; ADR y
      especificaciones. Quitar "En construcción" y marcar como pendiente lo que el repositorio de infra aún no tiene
- [X] T019 [US1] Comprobar que cada enlace de `README.md` abre: los relativos existen y los externos responden (SC-005)
- [X] T020 [US1] En una copia del repositorio sin `target/`, en una carpeta temporal, seguir `README.md` de principio a
      fin hasta crear una lista (paso 5 de `{spec}/quickstart.md`) y corregir lo que falte. Anotar en ese archivo el
      resultado; el tiempo de una persona ajena (SC-001) queda anotado como pendiente de quien evalúe

**Checkpoint**: las instrucciones bastan para usar la documentación y la colección.

## Phase 6: Polish

- [X] T021 [P] Añadir en `docs/adr/0001-arquitectura-api-listas-reproduccion.md`, sección 4, la concreción: springdoc
      3.2.0, documentación pública, filtro por las lecturas bloqueantes y Jackson 2 como dependencia de swagger-core
- [X] T022 [P] Actualizar `docs/backlog.md`: marcar los criterios de HU-06 y añadir las comprobaciones de la colección
      (FR-004) y la prueba del contrato (FR-012)
- [X] T023 Generar `{spec}/requests.http` para las rutas de documentación y ejecutarlo contra la aplicación arrancada
- [X] T024 Ejecutar `./mvnw verify`, la búsqueda de secretos de `{spec}/quickstart.md` (SC-004) y confirmar con
      `git diff main` que no cambió ninguna otra prueba existente (FR-020)
- [X] T025 Buscar comentarios de revisión en los archivos tocados y, si hay, registrarlos en `{spec}/code-review.md`

## Dependencies

- Setup → US3 → US1 → Polish.
- US2 no depende de US3: solo necesita la API actual, y puede hacerse antes o en paralelo.
- US1 necesita US2 y US3 terminadas.
- Dentro de US3: T008 → T009 → T012 → T013 → T014; T008 y T009 bastan para T004, T005 y T006.

## Parallel Opportunities

- US3: las pruebas T003 a T007 están en archivos distintos; T010 y T011 también.
- US2 completa puede avanzar en paralelo con US3.
- Polish: T021 y T022.

## Implementation Strategy

**MVP**: Setup y US3, que es la pieza con riesgo técnico. US2 y US1 se añaden encima y se validan por separado. La
historia no se entrega a medias: se cierra con las tres y Polish.
