---
description: "Tareas de HU-13: Recomendaciones sobre un catálogo (RAG)"
---

# Tasks: Recomendaciones sobre un catálogo (RAG)

**Input**: `specs/13-catalog-recommendations/` (plan.md, spec.md, research.md, data-model.md, contracts/,
quickstart.md)

**Tests**: obligatorias (FR-035 a FR-037). Dentro de cada fase se escriben antes que el código y deben fallar primero.

**Organization**: una fase por historia de usuario, en el orden de `spec.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (otro archivo y sin depender de una tarea pendiente)
- **[Story]**: historia de `spec.md` (US1 a US4)

## Path Conventions

Rutas relativas a `quipux-playlist-backend/`; las de infra empiezan por `../quipux-playlist-infra/`.

| Alias | Ruta |
|---|---|
| `{domain}` / `{domain-test}` | `domain/src/main/java/com/quipux/playlist/domain` / `domain/src/test/java/com/quipux/playlist/domain` |
| `{app}` / `{app-test}` | `application/src/main/java/com/quipux/playlist/application` / `application/src/test/java/com/quipux/playlist/application` |
| `{infra}` / `{infra-test}` | `infrastructure/src/main/java/com/quipux/playlist/infrastructure` / `infrastructure/src/test/java/com/quipux/playlist/infrastructure` |
| `{infra-res}` / `{infra-test-res}` | `infrastructure/src/main/resources` / `infrastructure/src/test/resources` |
| `{boot}` / `{boot-test}` | `bootstrap/src/main/java/com/quipux/playlist` / `bootstrap/src/test/java/com/quipux/playlist` |
| `{boot-res}` / `{boot-test-res}` | `bootstrap/src/main/resources` / `bootstrap/src/test/resources` |

## Reglas para todas las tareas

- Identificadores en inglés; el contrato HTTP y el catálogo, en español. Sin Lombok: clase final con constructor
  privado.
- `domain` solo usa el JDK; `application`, además, `domain` y Reactor. Spring AI y JDBC solo en `{infra}` y en el
  arranque de `{boot}`. Sin `.block()` en el código de la API (solo en el proceso de indexación).
- Toda llamada JDBC o bloqueante, en `Schedulers.boundedElastic()`.
- Mensajes en `ErrorMessageEnum`. Ningún mensaje ni causa de la pasarela o de PostgreSQL llega a un registro.
- Las pruebas pasan sin modelos ni clave. Las que necesitan Docker usan `disabledWithoutDocker`.
- Compilar con `./mvnw verify`, nunca `install`.
- Cada tarea que añade pruebas anota en `AI-LOG.md` si cada caso lo generó la IA o se añadió a mano; cada prompt
  relevante se anota en el momento.
- No hacer commit ni push sin que se pida.

---

## Phase 1: Setup

**Purpose**: punto de partida verificado y dependencias disponibles.

- [X] T001 Comprobar que la rama `feature/13-catalog-recommendations` incluye `origin/main` y que `./mvnw verify` pasa
  antes de cambiar nada; anotar el número de pruebas en `AI-LOG.md`
- [X] T002 Añadir las dependencias de research.md R1 y R13 en `infrastructure/pom.xml` (`spring-ai-pgvector-store`,
  `spring-boot-starter-jdbc`, `postgresql` en `runtime`, `testcontainers-postgresql` y
  `testcontainers-junit-jupiter` en `test`) y las dos de Testcontainers en `bootstrap/pom.xml`, sin versiones propias;
  `./mvnw verify` sigue en verde

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: el catálogo como tipo de dominio, que usan la indexación y la recomendación.

- [X] T003 [P] Añadir en `{domain}/model/constants/CatalogConstraints.java` los límites de `data-model.md`
  `SongCatalog.songs` (tamaño) y `CatalogSong.releaseYear` (formato), reutilizando `PlaylistConstraints` para las
  longitudes
- [X] T004 Añadir `RECOMMENDATION_ERROR_004` (catálogo no disponible) en `{domain}/model/enums/ResponseCodeEnum.java`;
  en `{domain}/model/enums/ErrorMessageEnum.java`, los mensajes de catálogo inválido (con regla y posición, código
  `RECOMMENDATION_ERROR_003`), no configurado, inalcanzable, tiempo agotado, sin candidatas y fallo de indexación; su
  estado `503` en `{infra}/in/rest/handler/ProblemDetails.java`; casos en `{domain-test}/model/ErrorMessageEnumTest.java`
- [X] T005 [P] Crear `{domain}/exception/SongCatalogUnavailableException.java`, sin causa, como
  `RecommendationProviderUnavailableException`
- [X] T006 [P] Escribir `{domain-test}/model/CatalogSongTest.java`, `{domain-test}/model/SongCatalogTest.java` y
  `{domain-test}/model/CatalogRulesTest.java`: identificador estable y distinto al cambiar cualquier campo o repartir
  el texto entre campos; versión estable ante otro orden y distinta al cambiar contenido o modelo; recuento de canciones
  del catálogo presentes en una lista; cada fila de la tabla "Validez del catálogo" por dentro y por fuera del límite
- [X] T007 Crear `{domain}/model/CatalogSong.java` según `data-model.md` `CatalogSong.id`, `CatalogSong.title`,
  `CatalogSong.artist`, `CatalogSong.album`, `CatalogSong.releaseYear` y `CatalogSong.genre`
- [X] T008 Crear `{domain}/model/SongCatalog.java` según `data-model.md` `SongCatalog.songs` y `SongCatalog.version`
- [X] T009 Crear `{domain}/model/CatalogRules.java` ("Validez del catálogo"), que recibe los géneros permitidos y
  comprueba `data-model.md` `SongCatalog.songs`, `CatalogSong.title`, `CatalogSong.artist`, `CatalogSong.album`,
  `CatalogSong.releaseYear` y `CatalogSong.genre`
- [X] T010 Crear `{app}/port/out/SongCatalogPort.java` solo con `catalog()` (`nearest` llega en la US2)

**Checkpoint**: `./mvnw verify` en verde.

---

## Phase 3: User Story 1 - Tener el catálogo disponible e indexado (Priority: P1) 🎯 MVP

**Goal**: el catálogo existe, se valida y queda indexado en pgvector con la composición, sin condicionar la API.

**Independent Test**: desde un volumen vacío, levantar la composición, contar las filas, repetir la indexación
(`added=0`) y arrancar la API con PostgreSQL detenido.

### Tests for User Story 1

- [X] T011 [P] [US1] En `{domain-test}/model/CatalogIndexPlanTest.java`: ausente, igual, de otro modelo y sobrante
- [X] T012 [P] [US1] En `{app-test}/usecase/IndexCatalogUseCaseTest.java`: lotes del tamaño configurado, guardar antes
  de borrar, sin llamadas cuando no hay cambios, un fallo detiene el proceso sin borrar, y el informe
- [X] T013 [P] [US1] En `{infra-test}/out/catalog/FileSongCatalogTest.java`: fichero válido; fallo de arranque que
  nombra regla y posición si falta, está mal formado o incumple una regla; y el fichero del repositorio
  `catalog/songs.json` cumple todas las reglas (escenario 7)
- [X] T014 [P] [US1] En `{infra-test}/out/catalog/PgVectorSongCatalogIndexTest.java`, con `VectorStore` y
  `JdbcTemplate` simulados: texto y metadatos de `contracts/catalog.md`, plantilla de contenido `{content}`,
  `indexed()`, `save` y `remove` en un hilo `boundedElastic`, y errores traducidos a
  `SongCatalogUnavailableException` sin causa
- [X] T015 [P] [US1] Copiar el esquema de `contracts/catalog.md` a `{infra-test-res}/catalog/init.sql`, crear
  `{infra-test}/out/catalog/PgVectorContainer.java` (imagen y digest de research.md R11) y
  `{infra-test}/out/catalog/DockerRequiredOnCiTest.java`, que falla si `CI=true` y no hay Docker
- [X] T016 [US1] En `{infra-test}/out/catalog/PgVectorCatalogIndexingTest.java` (Testcontainers, embeddings
  deterministas): indexar, repetir sin pedir embeddings, cambiar, quitar y cambiar de modelo, completar tras un fallo a
  mitad, y una sola fila por canción
- [X] T017 [P] [US1] En `{infra-test}/out/catalog/CatalogConfigTest.java`: cada fila de "Qué se activa" de
  `contracts/configuration.md` con su aviso, clave vacía sin peticiones, arranque con PostgreSQL inalcanzable sin
  esperar la conexión y `DataSource` que no se inyecta sin calificador
- [X] T018 [P] [US1] En `{boot-test}/CatalogIsolationApiTest.java`: con IA activada y PostgreSQL inalcanzable la API
  arranca, crea y borra listas, el único gestor de transacciones es el de R2DBC y no hay `JdbcTemplate` inyectable; y
  en `{boot-test}/SchemaTest.java`, que H2 no tiene tablas del catálogo
- [X] T019 [US1] En `{boot-test}/CatalogIndexApplicationTest.java` (Testcontainers y WireMock que devuelve un vector
  por texto recibido, en `{boot-test}/EmbeddingsStub.java`): `index-catalog` termina con 0 y el registro de
  `contracts/catalog.md`, la segunda vez sin peticiones, termina con 1 ante un error sin escribir claves, contraseña ni
  respuesta externa, no necesita secretos JWT ni H2 y envía una petición por lote; usa la imagen y el script de
  T015 (ruta `../infrastructure/src/test/resources/catalog/init.sql`)

### Implementation for User Story 1

- [X] T020 [P] [US1] Reunir entre 200 y 250 canciones reales en `{infra-res}/catalog/songs.json` según
  `contracts/catalog.md` y `data-model.md` `SongCatalog.songs`, con géneros de `local-genres.properties` asignados a
  mano y sin datos de Spotify; anotar en `AI-LOG.md` el prompt y lo corregido
- [X] T021 [P] [US1] Crear `tools/catalog/verify_catalog.py` según `contracts/catalog.md` (solo biblioteca estándar,
  1 petición por segundo, sin leer etiquetas ni géneros, sin peticiones si falta `MUSICBRAINZ_CONTACT`) y probar esa
  negativa
- [X] T022 [US1] Pedir a la persona usuaria el valor de `MUSICBRAINZ_CONTACT` (no usar sus datos sin que lo indique),
  ejecutar el script, corregir o retirar las canciones sin coincidencia en `{infra-res}/catalog/songs.json` y guardar
  `docs/catalog-verification.md`
- [X] T023 [P] [US1] Crear `{domain}/model/CatalogIndexPlan.java` y `{domain}/model/IndexReport.java` según
  `data-model.md` `CatalogIndexPlan.toIndex`, `CatalogIndexPlan.toRemove`, `CatalogIndexPlan.kept`,
  `IndexReport.added`, `IndexReport.kept` y `IndexReport.removed`
- [X] T024 [US1] Crear `{app}/port/out/CatalogIndexPort.java`, `{app}/port/in/IndexCatalogPort.java` y
  `{app}/usecase/IndexCatalogUseCase.java` según `data-model.md` ("Caso de uso `IndexCatalogUseCase`")
- [X] T025 [P] [US1] Crear `{infra}/out/catalog/FileSongCatalog.java`, que lee el JSON con Jackson 3, aplica
  `CatalogRules` y calcula la versión con el alias de embeddings
- [X] T026 [US1] Crear `{infra}/out/catalog/CatalogText.java` y `{infra}/out/catalog/PgVectorSongCatalogAdapter.java`
  (`catalog()` y operaciones de `CatalogIndexPort`; research.md R2 y R5)
- [X] T027 [US1] Crear `{infra}/out/catalog/UnavailableSongCatalogAdapter.java`: entrega el catálogo del fichero y
  señala el motivo en toda operación contra el almacén
- [X] T028 [US1] Crear `{infra}/out/catalog/config/CatalogSettings.java` y `{infra}/out/catalog/config/CatalogConfig.java`:
  `HikariDataSource` con `defaultCandidate = false` y los valores de research.md R1, `JdbcTemplate` propio sin
  exponerlo como *bean*, `PgVectorStore` de 768 dimensiones, coseno, HNSW, UUID y sin crear el esquema, y elección del
  adaptador
- [X] T029 [US1] Aplicar `contracts/configuration.md` en `{boot-res}/application.yml` (bloques de embeddings y
  `playlist.catalog`; `prompt-version` y `response-format` no cambian todavía) y fijar
  `spring.ai.model.embedding: none` en `{boot-test-res}/config/application.yml`; activar también los embeddings en
  `{boot-test}/AiApiTestSupport.java`
- [X] T030 [US1] Crear `{boot}/CatalogIndexApplication.java` (sin estereotipo, sin web, importa solo la
  autoconfiguración de embeddings, `CatalogConfig`, el `SingleAttemptInterceptor` y el caso de uso) y hacer que
  `{boot}/PlaylistApplication.java` la lance con el argumento `index-catalog` y salga con su código (research.md R6)
- [X] T031 [US1] En `../quipux-playlist-infra`, crear la rama `feature/13-catalog-recommendations` desde `origin/main`;
  revisar avisos de la imagen de research.md R11; crear `compose/catalog-db/init.sql`, idéntico a la copia de T015
- [X] T032 [US1] Aplicar `contracts/ai-compose.md` en `../quipux-playlist-infra/compose/compose.ai.yaml`,
  `compose/litellm/config.yaml` y `compose/.env.example`
- [ ] T033 [US1] Validar los escenarios 3 y 4 de `quickstart.md` y los puertos publicados, usando
  `-p quipux-ia` y sin borrar volúmenes existentes sin permiso; anotar el tiempo de indexación y la memoria en
  `specs/13-catalog-recommendations/research.md`

**Checkpoint**: `./mvnw verify` en verde; catálogo indexado en la composición; la API de HU-12 sigue igual.

---

## Phase 4: User Story 2 - Recibir sugerencias que existen en el catálogo (Priority: P1)

**Goal**: con la IA activada, todo lo recomendado sale de las candidatas del catálogo.

**Independent Test**: con el modelo simulado y un catálogo de prueba, `200` `IA` `v2` con `idCatalogo` relleno,
canciones ausentes de la lista y datos del catálogo.

**Nota**: hasta la US3, un fallo del modelo sigue respondiendo `RESPALDO`.

### Tests for User Story 2

- [X] T034 [P] [US2] En `{domain-test}/model/RecommendationRulesTest.java`, sustituir los casos de sugerencias libres
  por "Candidatas" y "Elección del modelo": presentes en la lista (incluida la canción 51) y no vigentes fuera, 20 como
  máximo y en orden, desconocidos y repetidos fuera, 5 como máximo, motivo recortado y datos de la candidata; y ajustar
  `{domain-test}/model/RecommendationSetTest.java` y `{domain-test}/model/DefaultRecommendationsTest.java`
- [X] T035 [P] [US2] En `{app-test}/usecase/GetRecommendationsUseCaseTest.java`: orden caché → candidatas → modelo,
  `limit` igual a 20 más las canciones del catálogo presentes en la lista, `IA` con la versión del prompt y sin
  candidatas no se llama al modelo
- [X] T036 [P] [US2] En `{infra-test}/out/recommendation/PromptTemplateTest.java`: `v2` con dos bloques y los campos de
  `contracts/model-provider.md`, escape de `<` y `>` en ambos, texto que imita el cierre e instrucciones inyectadas sin
  cambiar el mensaje de sistema
- [X] T037 [P] [US2] En `{infra-test}/out/recommendation/CandidateAliasesTest.java` y
  `{infra-test}/out/recommendation/ChoiceSchemaTest.java`: identificadores `c1`…`cN` en orden y el esquema exacto del
  contrato
- [X] T038 [P] [US2] En `{infra-test}/out/recommendation/LlmResponseParserTest.java`: forma nueva, elemento mal tipado
  descartado solo, `motivo` ausente admitido, campos extra ignorados y texto sobrante inválido
- [X] T039 [P] [US2] En `{infra-test}/out/recommendation/LlmRecommendationProviderAdapterTest.java`: el cuerpo lleva
  `model`, `max_tokens`, `stream: true` y `json_schema` con el `enum` de la petición (nunca otro modelo), y los
  identificadores cortos vuelven como UUID descartando los desconocidos
- [X] T040 [P] [US2] En `{infra-test}/out/catalog/PgVectorSongCatalogSearchTest.java`: texto de consulta con la muestra
  de HU-12, `topK` pedido, identificadores en orden y ejecución en `boundedElastic`; y en
  `{infra-test}/out/catalog/PgVectorCatalogSearchTest.java` (Testcontainers), orden por cercanía y más de 40
  resultados
- [X] T041 [P] [US2] En `{infra-test}/in/rest/RecommendationControllerTest.java`: `idCatalogo` relleno y vacío según
  `contracts/rest-api.md`
- [X] T042 [P] [US2] Crear `{boot-test}/InMemorySongCatalog.java`, registrarlo en `{boot-test}/AiApiTestSupport.java` y
  actualizar `{boot-test}/RecommendationsAiApiTest.java` (escenarios 1 y 3 a 7); cambiar la ayuda `suggestions` a la
  forma nueva y ajustar `{boot-test}/RecommendationFailureApiTest.java`, `{boot-test}/RecommendationLimitsApiTest.java`,
  `{boot-test}/RecommendationLoggingApiTest.java` y `{boot-test}/RecommendationNonBlockingTest.java`, que en esta fase
  siguen esperando `RESPALDO` ante un fallo
- [X] T043 [P] [US2] En `{boot-test}/RecommendationsCatalogApiTest.java` (Testcontainers y WireMock, catálogo real
  indexado): extremo a extremo con 20 candidatas ausentes de la lista y respuesta con datos de `songs.json`

### Implementation for User Story 2

- [X] T044 [P] [US2] Crear `{domain}/model/ModelChoice.java` según `data-model.md` `ModelChoice.catalogId` y
  `ModelChoice.reason`
- [X] T045 [US2] Añadir `catalogId` a `{domain}/model/Recommendation.java` según `data-model.md`
  `Recommendation.catalogId` (constructor para las no verificadas) y ajustar
  `{infra}/out/recommendation/FileDefaultRecommendationsAdapter.java`
- [X] T046 [US2] En `{domain}/model/RecommendationRules.java` y
  `{domain}/model/constants/RecommendationConstraints.java`, sustituir `validate` por las reglas "Candidatas" y
  "Elección del modelo", con `MAX_CANDIDATES` y `data-model.md` `Recommendation.reason`, `Recommendation.title`,
  `Recommendation.artist` y `Recommendation.genre`
- [X] T047 [US2] Añadir `nearest` a `{app}/port/out/SongCatalogPort.java` e implementarlo en
  `{infra}/out/catalog/PgVectorSongCatalogAdapter.java` y `{infra}/out/catalog/UnavailableSongCatalogAdapter.java`
  (research.md R4)
- [X] T048 [US2] Sustituir `recommend` por `choose` en `{app}/port/out/RecommendationProviderPort.java` y
  `{infra}/out/recommendation/SimulatedRecommendationProviderAdapter.java`, y ajustar
  `{infra-test}/out/recommendation/SimulatedRecommendationProviderAdapterTest.java`
- [X] T049 [P] [US2] Crear `{infra-res}/prompts/recommendations/v2.txt` con el texto exacto de la sección 10 del
  ADR-0002 (`v1.txt` se conserva) y adaptar `{infra}/out/recommendation/PromptTemplate.java` a los dos bloques
- [X] T050 [P] [US2] Crear `{infra}/out/recommendation/CandidateAliases.java` y
  `{infra}/out/recommendation/ChoiceSchema.java`
- [X] T051 [US2] Adaptar `{infra}/out/recommendation/LlmResponseParser.java` y
  `{infra}/out/recommendation/LlmRecommendationProviderAdapter.java`: opciones construidas con
  `getOptions().mutate()` más el esquema (research.md R7), sin perder cortacircuitos, tiempo ni permisos
- [X] T052 [US2] Integrar la recuperación en `{app}/usecase/GetRecommendationsUseCase.java` y pasarle el catálogo y el
  número de candidatas en `{boot}/config/UseCaseConfig.java`
- [X] T053 [US2] En `{boot-res}/application.yml`, `prompt-version: v2` y quitar `response-format`; el mismo valor por
  defecto en `{infra}/out/recommendation/config/RecommendationConfig.java`
- [X] T054 [US2] Mapear `idCatalogo` en `{infra}/in/rest/mapper/RecommendationDtoMapper.java` y actualizar
  `{infra}/in/rest/doc/ApiOperationDocEnum.java`, los esquemas de `{infra}/in/rest/dto/RecommendationDto.java` y
  `{infra}/in/rest/dto/RecommendationsResponseDto.java`, `{boot-test-res}/openapi-operations.properties`,
  `{boot-test-res}/postman-collection.properties` y `postman/quipux-playlist.postman_collection.json`

**Checkpoint**: `./mvnw verify` en verde; escenario 5 de `quickstart.md` con los modelos reales.

---

## Phase 5: User Story 3 - Seguir respondiendo cuando falla una parte (Priority: P1)

**Goal**: `SIMILITUD` si falla el modelo y `RESPALDO` si falla la búsqueda, siempre dentro del tiempo.

**Independent Test**: simular cada fila de la tabla "Fallos" de `contracts/model-provider.md` y comprobar origen,
peticiones enviadas y tiempo.

### Tests for User Story 3

- [X] T055 [P] [US3] En `{domain-test}/model/RecommendationRulesTest.java` y
  `{domain-test}/model/RecommendationSetTest.java`: "Similitud" (5 primeras, motivo fijo) y origen `SIMILITUD` sin
  versión de prompt
- [X] T056 [P] [US3] En `{app-test}/usecase/GetRecommendationsUseCaseTest.java`: error, nada válido y tiempo restante
  agotado del modelo dan `SIMILITUD`; error o vacío de la búsqueda dan `RESPALDO` sin llamar al modelo; lista vacía no
  busca; `404` y fallo del repositorio no se ocultan; `SIMILITUD` no se guarda; registro con versión del catálogo,
  candidatas, tiempo de recuperación y motivo
- [X] T057 [P] [US3] En `{infra-test}/out/catalog/PgVectorSongCatalogFailureTest.java`: tiempo máximo de recuperación
  con almacén lento, y errores de embeddings, de PostgreSQL y de dimensión traducidos sin causa ni texto externo
- [X] T058 [US3] Actualizar `{boot-test}/RecommendationFailureApiTest.java`: fallos del chat dan `SIMILITUD`; ante
  `500`, `503` con `Retry-After: 0`, `408`, tiempo y otra dimensión en embeddings, `RESPALDO` con **una** petición de
  embeddings y ninguna de chat; el cortacircuitos abierto sigue buscando y responde `SIMILITUD`; los fallos de
  embeddings no lo abren
- [X] T059 [P] [US3] En `{boot-test}/RecommendationCatalogDownApiTest.java`: adaptador real con PostgreSQL inalcanzable
  y detenido, `RESPALDO` en menos de 6 s y el resto de la API en funcionamiento
- [X] T060 [P] [US3] Ajustar a `SIMILITUD` la saturación en `{boot-test}/RecommendationLimitsApiTest.java` y comprobar
  IA desactivada y sin clave sin ninguna petición en `{boot-test}/RecommendationsApiTest.java` y
  `{boot-test}/RecommendationsWithoutKeyApiTest.java`
- [X] T061 [P] [US3] En `{boot-test}/RecommendationLoggingApiTest.java`: en éxito, degradaciones y fallo de conexión, la
  salida no contiene clave, contraseña del catálogo, tokens, prompt, canciones ni textos de error externos
- [X] T062 [P] [US3] En `{boot-test}/RecommendationNonBlockingTest.java`: BlockHound en `IA`, `SIMILITUD` y `RESPALDO`
  con el catálogo en memoria, y con pgvector en `{boot-test}/RecommendationsCatalogApiTest.java`

### Implementation for User Story 3

- [X] T063 [US3] Añadir `SIMILARITY` en `{domain}/model/enums/RecommendationOrigin.java`, su fábrica en
  `{domain}/model/RecommendationSet.java` según `data-model.md` `RecommendationSet.promptVersion`, `SIMILARITY_REASON` en
  `{domain}/model/constants/RecommendationConstraints.java` y la regla "Similitud" en
  `{domain}/model/RecommendationRules.java`
- [X] T064 [US3] En `{infra}/out/catalog/PgVectorSongCatalogAdapter.java`, aplicar
  `playlist.catalog.retrieval-timeout` y traducir todo fallo (research.md R10), ajustando los registros que escriban
  respuestas externas en `{boot-res}/application.yml`
- [X] T065 [US3] Completar la escalera de `data-model.md` en `{app}/usecase/GetRecommendationsUseCase.java`: tiempo
  total, límite del modelo al tiempo restante, `SIMILITUD`, `RESPALDO` y registro; pasar `playlist.ai.timeout` desde
  `{boot}/config/UseCaseConfig.java`
- [X] T066 [US3] Documentar el origen `SIMILITUD` en `{infra}/in/rest/doc/ApiOperationDocEnum.java`,
  `{infra}/in/rest/dto/RecommendationsResponseDto.java`, `{boot-test-res}/openapi-operations.properties` y
  `postman/quipux-playlist.postman_collection.json`

**Checkpoint**: `./mvnw verify` en verde; escenarios 6 y 7 de `quickstart.md`.

---

## Phase 6: User Story 4 - Reutilizar respuestas sin quedarse con un catálogo antiguo (Priority: P2)

**Goal**: las respuestas guardadas dependen también de la versión del catálogo.

**Independent Test**: diez peticiones iguales producen 1 petición de embeddings, 1 búsqueda y 1 de chat; con otra
versión del catálogo se vuelve a llamar.

### Tests for User Story 4

- [X] T067 [P] [US4] En `{domain-test}/model/RecommendationCacheKeyTest.java` y
  `{infra-test}/out/recommendation/CaffeineRecommendationCacheAdapterTest.java`: otra versión del catálogo es otra clave
- [X] T068 [P] [US4] En `{app-test}/usecase/GetRecommendationsUseCaseTest.java`: un acierto no busca ni llama al modelo,
  otra versión sí, y `SIMILITUD` y `RESPALDO` no se guardan
- [X] T069 [P] [US4] En `{boot-test}/RecommendationLimitsApiTest.java`: escenarios 1 y 3 de la US4 y SC-005 contando
  peticiones y búsquedas

### Implementation for User Story 4

- [X] T070 [US4] Añadir la versión a `{domain}/model/RecommendationCacheKey.java` según `data-model.md`
  `RecommendationCacheKey.catalogVersion` y usarla en `{app}/usecase/GetRecommendationsUseCase.java`

**Checkpoint**: `./mvnw verify` en verde.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T071 [P] Actualizar `README.md` del backend: catálogo, origen y comprobación, indexación automática, repetida y
  desde cero, configuración, orígenes de la respuesta, límite conocido de research.md R2 y `qwen2.5:7b` con GPU
  (research.md R9)
- [X] T072 [P] Actualizar `../quipux-playlist-infra/README.md`: PostgreSQL, variables, actualización desde HU-12,
  memoria y requisitos
- [X] T073 [P] Generar `specs/13-catalog-recommendations/requests.http` según `.ai/rules/http-test-requests.md`
- [ ] T074 Ejecutar los escenarios 1, 2 y 5 a 9 de `quickstart.md` en CPU y GPU, anotar las mediciones y el análisis de
  la imagen con Trivy en `specs/13-catalog-recommendations/research.md` y ajustar tiempos o contexto si no cumplen SC-003
- [X] T075 Completar `AI-LOG.md` (prompts, correcciones y origen de las pruebas) y la sección "Autoría IA" de
  `README.md`
- [X] T076 Comprobar que cada criterio de HU-13 tiene prueba o comprobación anotada (SC-009) y marcarlos en
  `../backlog-ia.md`
- [ ] T077 Integrar `origin/main` en ambos repositorios, ejecutar `./mvnw verify` con Docker, confirmar que las pruebas
  de Docker no se omitieron, la cobertura de 80 % por módulo y que no hay secretos en archivos ni historial; anotar el
  resultado en `AI-LOG.md`

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Setup → Foundational → US1 → US2 → US3 → US4 → Polish
```

- **US1** necesita solo la base. Es entregable sola: no cambia el endpoint.
- **US2** necesita US1 (adaptadores, configuración y catálogo).
- **US3** necesita US2 (candidatas y elección).
- **US4** necesita US2; puede ir antes que US3, pero ambas cambian el mismo caso de uso.
- Dentro de US1: T025 y T020 antes que T028 (la aplicación valida el fichero al arrancar); T015 antes que T016, T019
  y T031; T024 y T025 antes que T027; T029 después de T028. T022 depende de la respuesta de la persona usuaria y **solo bloquea el cierre de la US1**, no
  el código.

### Parallel Opportunities

- **US1**: pruebas T011 a T015, T017 y T018; datos y script (T020, T021) junto al código; T023 y T025.
- **US2**: pruebas T034 a T043; T044, T049 y T050.
- **US3**: pruebas T055 a T057 y T059 a T062.
- **US4**: T067 a T069.
- **Polish**: T071 a T073.

### Parallel Example: User Story 1

```bash
# Tres frentes a la vez
Datos:   T020 → T021 → T022
Backend: T011-T019 → T023-T030
Infra:   T031 → T032      # T033 necesita T030
```

---

## Implementation Strategy

### MVP

Fases 1 a 3: catálogo validado e indexado en pgvector, sin tocar el comportamiento del endpoint. Es el primer punto de
parada, pero **no cumple por sí solo la historia**: el valor para el usuario llega con US2 y US3.

### Entrega incremental

1. US1 → validar la indexación en la composición.
2. US2 → sugerencias reales del catálogo.
3. US3 → degradación completa. Con esto se cumplen las tres historias P1.
4. US4 y cierre.

### Recorte

Según `backlog-ia.md`: si el plazo no alcanza, la historia queda como diseño documentado y se anota lo no entregado.
No se recortan la clave, el tiempo máximo, el respaldo, la prueba de fallo del proveedor ni `AI-LOG.md`.

## Notes

- Cambiar el puerto del proveedor rompe la compilación hasta terminar T044 a T054: verificar al final de la fase.
- Las pruebas de HU-12 que se ajustan conservan sus casos; si uno deja de aplicar, se anota el motivo en `AI-LOG.md`.
- Desviaciones de la US3: los fallos de embeddings de T058 están en `RecommendationEmbeddingsFailureApiTest` (usa
  pgvector real) y la comprobación del registro de T056 está en las pruebas de la API, con captura de salida.
