---
description: "Tareas de HU-04: géneros"
---

# Tasks: Géneros

**Input**: documentos de diseño en `specs/04-genres/`

**Tests**: incluidas. En cada fase las pruebas se escriben primero y se confirma que fallan.

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

**En todas las tareas**:

- Identificadores en inglés, líneas de 121 caracteres como máximo, llaves obligatorias y sin imports sin usar.
- Sin `.block()`, también en pruebas, y sin comentarios ni Javadoc.
- Mensajes en `ErrorMessageEnum`; valores fijos en enums o constantes; `@Value` solo en atributos de clase.
- Ninguna credencial o token en registros, mensajes de error o `toString`; ningún secreto real en el repositorio.
- Las pruebas nunca llaman a Spotify.

## Phase 1: Setup

- [X] T001 Añadir `org.wiremock:wiremock-standalone` con su versión en `pom.xml` y como dependencia `test` en
      `infrastructure/pom.xml` y `bootstrap/pom.xml`
- [X] T002 Dejar vacíos `playlist.spotify.client-id` y `client-secret` en
      `bootstrap/src/test/resources/config/application.yml` (research R8)

## Phase 2: Foundational

- [X] T003 [P] Crear `{domain-test}/model/GenreCatalogTest.java` y ampliar `{domain-test}/model/PlaylistTest.java`
      según `data-model.md` `GenreCatalog.genres` y `Playlist.declaredGenres`
- [X] T004 Crear `{domain}/model/GenreCatalog.java` y `{domain}/model/enums/GenreSource.java`; añadir
      `declaredGenres()` a `{domain}/model/Playlist.java` (`data-model.md` `GenreCatalog.genres`)
- [X] T005 Añadir los códigos y mensajes de `data-model.md` (Errores) a `{domain}/model/enums/ResponseCodeEnum.java` y
      `ErrorMessageEnum.java`, crear `{domain}/exception/GenreProviderUnavailableException.java` y completar el estado
      HTTP en `{infra}/in/rest/handler/ProblemDetails.java`
- [X] T006 Crear `{app}/port/in/GetGenresPort.java` y `{app}/port/out/GenreProviderPort.java`

## Phase 3: User Story 1 - Verificar qué ofrece Spotify (P1)

**Independent Test**: el ADR anota el resultado de cada consulta.

- [X] T007 [US1] Probar el token y las dos consultas con las credenciales reales
- [X] T008 [US1] Registrar el resultado y la decisión en `docs/adr/0001-arquitectura-api-listas-reproduccion.md`

## Phase 4: User Story 2 - Consultar los géneros (P1)

**Independent Test**: con Spotify simulado, `GET /genres` responde la lista y el origen.

- [X] T009 [P] [US2] Crear `{app-test}/usecase/GetGenresUseCaseTest.java`: éxito con origen `SPOTIFY`
- [X] T010 [P] [US2] Crear `{infra-test}/out/genre/SpotifyGenreProviderAdapterTest.java`: petición del token, petición
      de géneros y campos adicionales ignorados (`contracts/rest-api.md`)
- [X] T011 [P] [US2] Crear `{infra-test}/in/rest/GenreControllerTest.java`: `200` para ambos roles, `401` y `403`
- [X] T012 [US2] Crear `{app}/usecase/GetGenresUseCase.java`
- [X] T013 [US2] Crear `{infra}/out/genre/` (`SpotifyGenreProviderAdapter`, `SpotifySettings`, `SpotifyEndpoint`,
      `dto/`) según `data-model.md` (Configuración)
- [X] T014 [US2] Crear `{infra}/in/rest/GenreController.java`, `dto/GenresResponseDto.java`,
      `mapper/GenreDtoMapper.java` y las reglas de `/genres` en `config/SecurityConfig.java`

## Phase 5: User Story 3 - Seguir respondiendo cuando Spotify falla (P1)

**Independent Test**: cada fallo simulado devuelve la lista local con origen `LOCAL`.

- [X] T015 [P] [US3] Ampliar `{app-test}/usecase/GetGenresUseCaseTest.java`: error, catálogo vacío, error inesperado y
      registro del motivo
- [X] T016 [P] [US3] Ampliar `{infra-test}/out/genre/SpotifyGenreProviderAdapterTest.java`: sin credenciales, `401`,
      `4xx`, `429`, `5xx`, tiempo agotado, conexión rechazada, contenido ilegible y mensajes sin secretos
- [X] T017 [P] [US3] Crear `{infra-test}/out/genre/LocalGenreProviderAdapterTest.java`: orden, lista entregada sin
      repetidos y lista vacía que falla
- [X] T018 [US3] Añadir el respaldo y su registro a `{app}/usecase/GetGenresUseCase.java`
- [X] T019 [US3] Completar la traducción de errores y el tiempo máximo en
      `{infra}/out/genre/SpotifyGenreProviderAdapter.java`
- [X] T020 [US3] Crear `{infra}/out/genre/LocalGenreProviderAdapter.java`,
      `infrastructure/src/main/resources/local-genres.properties` y `{infra}/out/genre/config/GenreProviderConfig.java`
- [X] T021 [US3] Ensamblar en `{boot}/config/UseCaseConfig.java` y añadir `data-model.md` (Configuración) a
      `bootstrap/src/main/resources/application.yml`
- [X] T022 [US3] Crear `{boot-test}/GenresApiTest.java` (origen, respaldo, recuperación, roles, token no reenviado,
      menos de 4 s), `{boot-test}/GenresWithoutCredentialsApiTest.java` y el caso de lista vacía en
      `{boot-test}/StartupConfigurationTest.java`

## Phase 6: User Story 4 - Aceptar solo géneros del catálogo (P2)

**Independent Test**: crear listas con géneros dentro y fuera del catálogo.

- [X] T023 [P] [US4] Ampliar `{app-test}/usecase/CreatePlaylistUseCaseTest.java`: sin géneros no consulta; género
      conocido y con otras mayúsculas; desconocido no guarda; una sola consulta
- [X] T024 [P] [US4] Crear `{boot-test}/PlaylistGenreValidationApiTest.java`: escenarios de la historia 4 y casos
      límite de la especificación
- [X] T025 [US4] Validar en `{app}/usecase/CreatePlaylistUseCase.java` y actualizar su bean en
      `{boot}/config/UseCaseConfig.java`
- [X] T026 [US4] Ampliar `{boot-test}/PlaylistNonBlockingTest.java` con la consulta de géneros y la creación con
      género, con Spotify simulado y caído

## Phase 7: Polish

- [X] T027 Ejecutar `./mvnw verify` con las variables `SPOTIFY_*` del equipo definidas
- [X] T028 [P] Actualizar `README.md`, `docs/backlog.md` y las notas en
      `specs/02-playlist-management/contracts/rest-api.md` y `requests.http`
- [X] T029 Generar `specs/04-genres/requests.http` y ejecutarlo contra la aplicación arrancada
- [X] T030 Revisar que no haya credenciales en el repositorio ni en los registros

## Dependencies

- Setup → Foundational → US2 → US3 → US4 → Polish. US1 ya está hecha.
- US2 y US3 comparten el caso de uso y el adaptador; US4 necesita ambas.

## Parallel Opportunities

- Las pruebas marcadas `[P]` de cada historia están en archivos distintos.

## Implementation Strategy

**MVP**: US2 y US3 (consulta con respaldo). US4 se añade encima y se valida por separado.
