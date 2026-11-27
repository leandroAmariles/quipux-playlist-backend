---
description: "Tareas de HU-12: Recomendaciones con IA"
---

# Tasks: Recomendaciones con IA

**Input**: `specs/12-ai-recommendations/` (plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md)

**Tests**: obligatorias (FR-040 y FR-041). Dentro de cada fase se escriben antes que el código y deben fallar primero.

**Organization**: una fase por historia de usuario. La US4 va antes que la US2 porque el código del asistente se
genera y se guarda antes de escribir el cliente (FR-042).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (otro archivo y sin depender de una tarea pendiente)
- **[Story]**: historia de `spec.md` (US1 a US6)

## Path Conventions

Rutas relativas a `quipux-playlist-backend/`; las de infra empiezan por `../quipux-playlist-infra/`.

| Alias | Ruta |
|---|---|
| `{domain}` | `domain/src/main/java/com/quipux/playlist/domain` |
| `{domain-test}` | `domain/src/test/java/com/quipux/playlist/domain` |
| `{app}` | `application/src/main/java/com/quipux/playlist/application` |
| `{app-test}` | `application/src/test/java/com/quipux/playlist/application` |
| `{infra}` | `infrastructure/src/main/java/com/quipux/playlist/infrastructure` |
| `{infra-test}` | `infrastructure/src/test/java/com/quipux/playlist/infrastructure` |
| `{infra-res}` | `infrastructure/src/main/resources` |
| `{boot}` | `bootstrap/src/main/java/com/quipux/playlist` |
| `{boot-test}` | `bootstrap/src/test/java/com/quipux/playlist` |
| `{boot-res}` / `{boot-test-res}` | `bootstrap/src/main/resources` / `bootstrap/src/test/resources` |

## Reglas para todas las tareas

- Identificadores en inglés; el contrato HTTP, en español. Sin Lombok: clase final con constructor privado.
- `domain` solo usa el JDK; `application`, además, `domain` y Reactor. Spring AI solo en `{infra}`. Sin `.block()`.
- Mensajes en `ErrorMessageEnum`. En archivos compartidos solo se añaden bloques.
- Compilar con `./mvnw verify`, nunca `install`.
- Cada tarea que añade pruebas anota en `AI-LOG.md` si cada caso lo generó la IA o se añadió a mano; cada prompt
  relevante se anota en el momento.
- No hacer commit ni push sin que se pida.

---

## Phase 1: Setup

**Purpose**: punto de partida verificado y registro de autoría abierto.

- [X] T001 Comprobar que la rama `feature/12-ai-recommendations` incluye `origin/main` y que `./mvnw verify` pasa antes
  de cambiar nada (`pom.xml`)
- [X] T002 Crear `AI-LOG.md` con la plantilla entregada o, si no hay, una propia (fecha, prompt, respuesta, aceptado o
  corregido, verificación; tabla de pruebas con su origen) y registrar los prompts de especificación, plan y tareas

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: tipos, códigos y puertos que usan todas las historias del backend. La US6 no depende de esta fase.

- [X] T003 [P] Crear `{domain}/model/constants/RecommendationConstraints.java` con los límites de `data-model.md`
  `Recommendation.title`, `Recommendation.artist`, `Recommendation.genre`, `Recommendation.reason` y
  `RecommendationSet.recommendations`
- [X] T004 [P] Crear `{domain}/model/enums/RecommendationOrigin.java` según `data-model.md`
  `RecommendationSet.origin`, con su valor externo
- [X] T005 Añadir los códigos `RECOMMENDATION_ERROR_001` a `003` en `{domain}/model/enums/ResponseCodeEnum.java`, sus
  mensajes y los motivos de fallo de `data-model.md` ("Excepciones y códigos") en
  `{domain}/model/enums/ErrorMessageEnum.java`, y sus estados (`429`, `503`, `500`) en
  `{infra}/in/rest/handler/ProblemDetails.java`, con sus casos en `{domain-test}/model/ErrorMessageEnumTest.java`
- [X] T006 [P] Escribir `{domain-test}/model/RecommendationSetTest.java` y
  `{domain-test}/model/DefaultRecommendationsTest.java` con las reglas de `data-model.md`
  `RecommendationSet.recommendations`, `RecommendationSet.promptVersion` y
  `DefaultRecommendations.recommendations`
- [X] T007 Crear `{domain}/model/Recommendation.java` y `{domain}/model/RecommendationSet.java` según `data-model.md`
  `Recommendation.title`, `Recommendation.artist`, `Recommendation.genre`, `Recommendation.reason`,
  `RecommendationSet.playlistName`, `RecommendationSet.promptVersion` y `RecommendationSet.recommendations`
- [X] T008 Crear `{domain}/model/DefaultRecommendations.java`, que recibe el catálogo de géneros permitido, según
  `data-model.md` `DefaultRecommendations.recommendations`
- [X] T009 [P] Crear `{domain}/model/ProviderIdentity.java` según `data-model.md` `ProviderIdentity.promptVersion` y
  `ProviderIdentity.model`
- [X] T010 [P] Crear `{domain}/exception/RecommendationProviderUnavailableException.java`, sin causa ni texto del
  proveedor (research.md R6)
- [X] T011 Crear `{app}/port/in/GetRecommendationsPort.java`, `{app}/port/out/RecommendationProviderPort.java` y
  `{app}/port/out/DefaultRecommendationsPort.java` con las firmas de `data-model.md` ("Puertos")

**Checkpoint**: `./mvnw verify` en verde.

---

## Phase 3: User Story 1 - Pedir recomendaciones de una lista (Priority: P1) 🎯 MVP

**Goal**: el endpoint responde con la lista por defecto, sin modelos ni claves.

**Independent Test**: con la configuración por defecto, `GET /lists/{listName}/recommendations` da `200` `RESPALDO` a
`USER` y `ADMIN`, y `400`, `401`, `403` y `404` según `contracts/rest-api.md`.

### Tests for User Story 1

- [X] T012 [P] [US1] Casos del respaldo (omite canciones de la lista, máximo 5, puede quedar vacío) en
  `{domain-test}/model/RecommendationRulesTest.java`
- [X] T013 [P] [US1] En `{app-test}/usecase/GetRecommendationsUseCaseTest.java`: lista inexistente sin llamar al
  proveedor, fallo del repositorio que no se oculta, lista vacía sin llamar al proveedor y error del proveedor que
  responde `RESPALDO`
- [X] T014 [P] [US1] En `{infra-test}/out/recommendation/FileDefaultRecommendationsAdapterTest.java`: archivo válido y
  fallo de arranque si está ausente, mal formado, con menos de 5, repetidos, campos incompletos o largos, o un género
  fuera de la lista local
- [X] T015 [P] [US1] En `{infra-test}/out/recommendation/SimulatedRecommendationProviderAdapterTest.java`: señala
  `DISABLED` o `NOT_CONFIGURED`
- [X] T016 [P] [US1] En `{infra-test}/in/rest/RecommendationControllerTest.java`: forma de la respuesta de
  `contracts/rest-api.md`, `idCatalogo` vacío y nombres con espacios
- [X] T017 [P] [US1] En `{boot-test}/RecommendationsApiTest.java` (base `ApiTestSupport`, token sin rol como en
  `RoleAccessApiTest`): escenarios 1 a 6 de la US1, lista vacía, `HEAD`, lista llamada `recommendations` y respuesta en
  menos de 1 s

### Implementation for User Story 1

- [X] T018 [US1] Crear `{domain}/model/RecommendationRules.java` con la regla del respaldo de `data-model.md`
  ("Identidad de una canción" y "Validación")
- [X] T019 [US1] Crear `{app}/usecase/GetRecommendationsUseCase.java`: buscar la lista, lista vacía, llamar al
  proveedor y, ante cualquier error suyo, respaldo con el nombre pedido
- [X] T020 [P] [US1] Crear `{infra-res}/default-recommendations.properties` (formato de research.md R11): al menos 5
  canciones reales, con motivo y géneros de `local-genres.properties`
- [X] T021 [US1] Crear `{infra}/out/recommendation/FileDefaultRecommendationsAdapter.java`, que valida al construirse
  con `DefaultRecommendations`
- [X] T022 [P] [US1] Crear `{infra}/out/recommendation/SimulatedRecommendationProviderAdapter.java`
- [X] T023 [P] [US1] Crear `{infra}/in/rest/dto/RecommendationsResponseDto.java` y
  `{infra}/in/rest/dto/RecommendationDto.java` según `contracts/rest-api.md`
- [X] T024 [US1] Crear `{infra}/in/rest/mapper/RecommendationDtoMapper.java` y
  `{infra}/in/rest/RecommendationController.java`, que pasa como solicitante el `sub` del token; `SecurityConfig` no
  cambia
- [X] T025 [US1] Cablear en `{infra}/out/recommendation/config/RecommendationConfig.java` (adaptador simulado y lista
  por defecto, con los géneros de `LocalGenreProviderAdapter`), `{boot}/config/UseCaseConfig.java` y el bloque
  `playlist.ai.default-recommendations-file` de `{boot-res}/application.yml`
- [X] T026 [US1] Documentar la operación (sin `429` todavía) en `{infra}/in/rest/doc/ApiOperationDocEnum.java` y añadir
  `operation.07` y los campos de los DTO en `{boot-test-res}/openapi-operations.properties`
- [X] T027 [US1] Añadir las peticiones `200` (tras crear la lista) y `404` a
  `postman/quipux-playlist.postman_collection.json` y a `{boot-test-res}/postman-collection.properties`, y hacer que
  `summaryOf` en `{boot-test}/PostmanCollectionTest.java` distinga `/lists/{listName}/recommendations` antes de
  reducir las demás rutas a `/lists/{listName}`

**Checkpoint (MVP)**: `./mvnw verify` en verde; escenarios 1 a 3 de quickstart.md salvo el `429`.

---

## Phase 4: User Story 4 - Dejar constancia de la autoría y la revisión de la IA (Priority: P1)

**Goal**: el código del asistente queda guardado tal como salió y revisado antes de tocarlo.

**Independent Test**: `AI-LOG.md` tiene el prompt, el código sin cambios y la revisión de los nueve puntos.

⚠️ Ninguna tarea de las fases 5 en adelante se empieza antes de T029.

- [X] T028 [US4] Redactar en `AI-LOG.md` el prompt que pide la implementación **completa** del cliente: puerto
  `RecommendationProviderPort`, `contracts/rest-api.md` y comportamiento esperado según `spec.md`, **sin** las
  conclusiones de research.md, plan.md ni los demás contratos (research.md R15)
- [X] T029 [US4] Obtener el código en una sesión **nueva** de un asistente (la abre el usuario, o un subagente sin
  contexto si lo autoriza), sin acceso a `specs/12-ai-recommendations`, y pegar en `AI-LOG.md` la respuesta íntegra sin
  cambios, con herramienta, modelo y fecha. La sesión que implementa no lo genera
- [X] T030 [US4] Escribir en `AI-LOG.md` la revisión del código tal como salió: secretos, bloqueo, timeout, reintentos
  acumulados, errores, validación, prompt injection, logs y pruebas, contrastado con research.md R1 a R10; cada hallazgo
  con la corrección prevista y la prueba que lo demostrará

**Checkpoint**: revisión escrita; todavía sin cambios sobre el código generado.

---

## Phase 5: User Story 2 - Recibir sugerencias de la IA (Priority: P1)

**Goal**: con la IA activada, hasta 5 sugerencias validadas con `origen` `IA`.

**Independent Test**: con LiteLLM simulado, respuestas correctas y defectuosas producen lo que indican los escenarios
de la US2.

### Setup for User Story 2

- [X] T031 [US2] Importar `spring-ai-bom` 2.0.3 en `pom.xml` y añadir `spring-ai-starter-model-openai` en
  `infrastructure/pom.xml`
- [X] T032 [US2] Añadir a `{boot-res}/application.yml` las propiedades de Spring de `contracts/configuration.md` (los
  seis `spring.ai.model.*`) y de `playlist.ai` `timeout`, `chat-model`, `prompt-version`, `max-songs`,
  `max-field-length` y `max-output-tokens`; fijar `spring.ai.model.chat: none` en
  `{boot-test-res}/config/application.yml` y confirmar que `./mvnw verify` pasa sin clave

### Tests for User Story 2

- [X] T033 [P] [US2] Casos de validación (pasos 1 a 7 de `data-model.md`, "Validación") en
  `{domain-test}/model/RecommendationRulesTest.java`
- [X] T034 [P] [US2] En `{domain-test}/model/SongSampleTest.java`: primeras 50 y recorte a 100 sin partir caracteres
- [X] T035 [P] [US2] En `{infra-test}/out/recommendation/PromptTemplateTest.java`: texto de `v1`, mensajes de sistema y
  usuario, JSON de las canciones, `<` y `>` escapados y un título que imita `</lista>` (research.md R10)
- [X] T036 [P] [US2] En `{infra-test}/out/recommendation/LlmResponseParserTest.java`: JSON correcto, campos extra,
  elementos con tipos erróneos descartados, texto alrededor, otra forma y vacío
- [X] T037 [P] [US2] Crear `{boot-test}/AiApiTestSupport.java` (WireMock como LiteLLM, respuestas SSE de
  `contracts/model-provider.md`, IA activada con clave falsa y tiempos cortos) y `{boot-test}/RecommendationsAiApiTest.java`
  con los escenarios 1 a 7 de la US2 y la forma exacta de la petición recibida, sin nombre de lista ni usuario
- [X] T038 [P] [US2] Añadir a `{app-test}/usecase/GetRecommendationsUseCaseTest.java` el camino `AI` y el caso sin
  sugerencias válidas

### Implementation for User Story 2

- [X] T039 [P] [US2] Crear `{domain}/model/SongSample.java` según `data-model.md` `SongSample.maxSongs` y
  `SongSample.maxFieldLength`
- [X] T040 [P] [US2] Añadir la validación a `{domain}/model/RecommendationRules.java` según `data-model.md`
  `Recommendation.title`, `Recommendation.artist`, `Recommendation.genre`, `Recommendation.reason` y
  `RecommendationSet.recommendations`
- [X] T041 [P] [US2] Crear `{infra-res}/prompts/recommendations/v1.txt` (texto exacto del ADR-0002, sección 10) y
  `{infra}/out/recommendation/PromptTemplate.java`; sin archivo no arranca (`RECOMMENDATION_ERROR_003`)
- [X] T042 [P] [US2] Crear `{infra}/out/recommendation/LlmResponseParser.java` y sus DTO en
  `{infra}/out/recommendation/dto/`
- [X] T043 [US2] Partiendo del código de T029, crear `{infra}/out/recommendation/LlmRecommendationProviderAdapter.java`
  y `{infra}/out/recommendation/LlmSettings.java`: `ChatModel.stream()` en `boundedElastic`, usando T039, T041 y T042
  (research.md R2, R5, R10)
- [X] T044 [US2] Elegir en `{infra}/out/recommendation/config/RecommendationConfig.java` el adaptador real solo si hay
  `ChatModel` y clave no vacía; si no, el simulado
- [X] T045 [US2] Añadir a `{app}/usecase/GetRecommendationsUseCase.java` la validación contra la lista completa, el
  resultado `AI` con la versión del prompt y el respaldo sin sugerencias válidas

**Checkpoint**: US1 y US2 en verde.

---

## Phase 6: User Story 3 - Seguir respondiendo cuando la IA falla (Priority: P1)

**Goal**: cualquier fallo del proveedor da `200` `RESPALDO`, con una sola petición y sin filtrar secretos.

**Independent Test**: cada fila de la tabla de fallos de `contracts/model-provider.md` produce la respuesta esperada y
el resto de la API sigue funcionando.

### Tests for User Story 3

- [X] T046 [P] [US3] En `{boot-test}/RecommendationFailureApiTest.java`, sobre la configuración real de
  `application.yml`: `401`, `429`, `500`, conexión cortada, tiempo agotado antes y durante el *streaming*, vacío, no
  SSE y JSON inválido; todos `200` `RESPALDO` con **una** petición en WireMock; `404` sigue siendo `404` y el resto de la
  API responde
- [X] T047 [P] [US3] En `{infra-test}/out/recommendation/LlmRecommendationProviderAdapterTest.java`: motivo de cada
  fallo; el cortacircuitos abre, no llama, prueba de nuevo tras la espera y se cierra; el contenido inválido no cuenta
- [X] T048 [P] [US3] En `{boot-test}/RecommendationsWithoutKeyApiTest.java`: `openai` sin clave arranca, avisa, responde
  `RESPALDO` y no envía peticiones
- [X] T049 [P] [US3] En `{boot-test}/RecommendationLoggingApiTest.java`: la salida no contiene la clave, el token, el
  prompt ni un cuerpo de error que repita el prompt, y sí latencia, origen, modelo, versión y motivo

### Implementation for User Story 3

- [X] T050 [US3] Añadir `resilience4j-circuitbreaker` y `resilience4j-reactor` 2.4.0 en `pom.xml` e
  `infrastructure/pom.xml`
- [X] T051 [US3] En `{infra}/out/recommendation/LlmRecommendationProviderAdapter.java`: `timeout` de Reactor igual al del
  SDK, cortacircuitos que lo envuelve y traducción de errores a los motivos de `data-model.md`, sin conservar la causa
  (research.md R4, R6, R7)
- [X] T052 [US3] Añadir `playlist.ai.circuit-breaker.*` y apagar el *logger* `MessageAggregator` en
  `{boot-res}/application.yml`, y crear el cortacircuitos en
  `{infra}/out/recommendation/config/RecommendationConfig.java`
- [X] T053 [US3] Registrar en `{app}/usecase/GetRecommendationsUseCase.java` latencia, origen, modelo, versión del
  prompt y motivo del respaldo (FR-025)
- [X] T054 [US3] Avisar al arrancar, en `{infra}/out/recommendation/config/RecommendationConfig.java`, cuando la IA está
  activada sin clave

**Checkpoint**: US1 a US3 en verde. Punto mínimo con IA real.

---

## Phase 7: User Story 5 - Controlar el uso del modelo (Priority: P2)

**Goal**: caché, límite por usuario y máximo de llamadas simultáneas.

**Independent Test**: contar las peticiones que recibe WireMock y las respuestas `429`.

### Tests for User Story 5

- [X] T055 [P] [US5] En `{domain-test}/model/RecommendationCacheKeyTest.java`: la huella cambia con cualquier campo de
  cualquier canción, también más allá de la 50, y no confunde campos contiguos
- [X] T056 [P] [US5] En `{infra-test}/out/recommendation/SlidingWindowRequestQuotaAdapterTest.java`, con reloj simulado:
  10 permitidas, la undécima no, `retryAfter`, rechazadas sin anotar, usuarios independientes y concurrencia
- [X] T057 [P] [US5] En `{infra-test}/out/recommendation/CaffeineRecommendationCacheAdapterTest.java`: caducidad a las
  24 h y tamaño máximo
- [X] T058 [P] [US5] Añadir a `{app-test}/usecase/GetRecommendationsUseCaseTest.java` el orden cuota → lista → vacía →
  caché → proveedor, el `404` que consume cupo, el nombre pedido en una respuesta guardada y el respaldo sin guardar
- [X] T059 [P] [US5] Añadir a `{infra-test}/out/recommendation/LlmRecommendationProviderAdapterTest.java`: la tercera
  llamada no espera, y el permiso no se libera al cancelar el suscriptor sino al terminar la llamada
- [X] T060 [P] [US5] En `{boot-test}/RecommendationLimitsApiTest.java`: escenarios 1 a 8 de la US5 (el 5, en T056), 10
  peticiones con 1 llamada y `HEAD` que consume cupo; esta clase fija `requests-per-minute: 10` y reinicia el
  contexto entre casos para que no compartan cupo ni caché

### Implementation for User Story 5

- [X] T061 [US5] Añadir `caffeine` en `infrastructure/pom.xml`
- [X] T062 [P] [US5] Crear `{domain}/model/RecommendationCacheKey.java` y `{domain}/model/CachedRecommendations.java`
  según `data-model.md` `RecommendationCacheKey.songsFingerprint`, `RecommendationCacheKey.promptVersion`,
  `RecommendationCacheKey.model`, `CachedRecommendations.promptVersion` y `CachedRecommendations.recommendations`
- [X] T063 [P] [US5] Crear `{domain}/model/QuotaDecision.java` (`data-model.md` `QuotaDecision.allowed` y
  `QuotaDecision.retryAfter`) y `{domain}/exception/RecommendationRateLimitException.java`
- [X] T064 [US5] Crear `{app}/port/out/RecommendationCachePort.java` y `{app}/port/out/RequestQuotaPort.java`
- [X] T065 [P] [US5] Crear `{infra}/out/recommendation/CaffeineRecommendationCacheAdapter.java` (research.md R9)
- [X] T066 [P] [US5] Crear `{infra}/out/recommendation/SlidingWindowRequestQuotaAdapter.java` con reloj inyectado
  (research.md R8)
- [X] T067 [US5] Integrar en `{app}/usecase/GetRecommendationsUseCase.java` la cuota y la caché según el flujo de
  `data-model.md`, y registrar el uso de caché
- [X] T068 [US5] Añadir en `{infra}/out/recommendation/LlmRecommendationProviderAdapter.java` el `Semaphore` con
  `tryAcquire` y la llamada desacoplada de la cancelación (research.md R4)
- [X] T069 [US5] Responder `429` con `Retry-After` y `ProblemDetail` en
  `{infra}/in/rest/handler/GlobalExceptionHandler.java`
- [X] T070 [US5] Añadir las propiedades `max-concurrent-calls`, `requests-per-minute` y `cache.*` a
  `{boot-res}/application.yml`, un `requests-per-minute` alto en `{boot-test-res}/config/application.yml` y los
  adaptadores en `{infra}/out/recommendation/config/RecommendationConfig.java` y `{boot}/config/UseCaseConfig.java`
- [X] T071 [US5] Documentar `429` y `Retry-After` en `{infra}/in/rest/doc/ApiResponseDocEnum.java`,
  `{infra}/in/rest/doc/ApiOperationDocEnum.java`, `{infra}/in/rest/doc/OpenApiContractCustomizer.java` y
  `{boot-test-res}/openapi-operations.properties`

**Checkpoint**: backend funcional completo; `./mvnw verify` en verde.

---

## Phase 8: User Story 6 - Levantar los modelos en el propio equipo (Priority: P2)

**Goal**: Ollama y LiteLLM con una composición adicional.

**Independent Test**: escenarios 5 a 8 de quickstart.md.

**Paralelo**: T072 a T076 solo dependen de la Phase 1; T077 y T078 necesitan las fases 5 y 6.

- [X] T072 [US6] Revisar avisos nuevos de Ollama y LiteLLM, verificar la firma de LiteLLM con `cosign` desde su imagen
  y confirmar los digests; anotar el resultado y la decisión sobre ZDI-26-1031 (consultar al usuario) en
  `specs/12-ai-recommendations/research.md` (R14). Si la firma falla, parar
- [X] T073 [P] [US6] Crear `../quipux-playlist-infra/compose/litellm/config.yaml` según research.md R3 y R12
- [X] T074 [US6] Crear `../quipux-playlist-infra/compose/compose.ai.yaml` según `contracts/ai-compose.md` y research.md
  R13, con imágenes por digest, descarga solo si falta y precarga de los dos modelos; sin tocar `compose.yaml`
- [X] T075 [P] [US6] Crear `../quipux-playlist-infra/compose/compose.ai.gpu.yaml`
- [X] T076 [P] [US6] Añadir `AI_API_KEY=` a `../quipux-playlist-infra/compose/.env.example` y comprobar que
  `../quipux-playlist-infra/.gitignore` ignora `.env`
- [X] T077 [US6] Ejecutar los escenarios 6 y 7 de `specs/12-ai-recommendations/quickstart.md` y resolver lo pendiente
  (un solo intento, `max_tokens`, modo JSON, prefijo y precarga de embeddings, telemetría y comprobación de salud),
  corrigiendo `../quipux-playlist-infra/compose/` y actualizando research.md R12 y R13
- [X] T078 [US6] Ejecutar el escenario 8 (GPU) y el arranque en CPU, y medir latencia en frío y en caliente y memoria;
  ajustar `AI_TIMEOUT` o el modelo si hace falta y anotarlo en `specs/12-ai-recommendations/research.md`
- [X] T079 [US6] Documentar en `../quipux-playlist-infra/README.md` la clave aleatoria `sk-`, los archivos de
  composición, la descarga de modelos, la GPU, la memoria de Docker, los requisitos y las latencias medidas

**Checkpoint**: pila completa con respuestas `IA` reales.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T080 Escribir `{boot-test}/RecommendationNonBlockingTest.java` con BlockHound tras calentar (como
  `PlaylistNonBlockingTest`): desactivada, `IA`, caché, respaldo y `429`
- [X] T081 [P] Añadir a `README.md` el endpoint, las variables de `contracts/configuration.md`, `AI_API_KEY`, la
  composición, la descarga de modelos, la GPU, la memoria de Docker y los requisitos
- [X] T082 Completar `AI-LOG.md`: cambios aplicados sobre el código generado y su motivo (cada hallazgo de T030),
  prompts, verificación y origen de cada prueba
- [X] T083 Escribir la sección "Autoría IA" de `README.md`, contrastada con `AI-LOG.md`
- [X] T084 [P] Generar `specs/12-ai-recommendations/requests.http` según `.ai/rules/http-test-requests.md` (en la raíz
  del espacio de trabajo), con las rutas y códigos reales
- [X] T085 Integrar `origin/main` en las ramas de backend e infra, añadiendo solo bloques en los archivos compartidos;
  si ya existe `.github/dependabot.yml`, ignorar Spring AI 2.1 o superior y Spring Boot 4.2 o superior
- [X] T086 Ejecutar `./mvnw verify` sin variables de IA ni Ollama y confirmar ArchUnit, 80 % por módulo y Checkstyle
  (`pom.xml`)
- [X] T087 Buscar claves y tokens en los archivos y en el historial de Git de backend e infra, y en los registros de la
  pila (`AI-LOG.md` incluido)
- [ ] T088 Ejecutar `specs/12-ai-recommendations/quickstart.md` completo, anotar ahí la comprobación de cada criterio no
  automatizable y marcar los criterios cumplidos de HU-12 en `../backlog-ia.md`

---

## Dependencies & Execution Order

```text
Fase 1 ─► Fase 2 ─► US1 ─► US4 ─► US2 ─► US3 ─► US5 ─► Cierre
Fase 1 ─► US6 (T072–T076)
US6 (T072–T076) + US3 ─► US6 (T077–T079) ─► Cierre
```

- **US1**: solo la fase 2. Entrega el endpoint sin modelos.
- **US4**: T028 y T029 necesitan los puertos y el contrato; **bloquea US2**. La revisión se cierra en T082.
- **US2**: US1 y US4.
- **US3**: US2 (comparte el adaptador).
- **US5**: US3 (comparte adaptador y caso de uso). El límite y la caché podrían adelantarse tras US1; no el
  `Semaphore`.
- **US6**: composición en paralelo desde la fase 1; las comprobaciones con la API, tras US3.
- Archivos que se tocan en varias fases, en orden: `GetRecommendationsUseCase`, `RecommendationRules`,
  `LlmRecommendationProviderAdapter`, `RecommendationConfig`, `application.yml` y `openapi-operations.properties`.

## Parallel Opportunities

| Historia | En paralelo |
|---|---|
| Fase 2 | T003, T004; T009, T010 |
| US1 | Pruebas T012–T017; después T020, T022, T023 |
| US2 | Pruebas T033–T038 (tras T031 y T032); después T039–T042 |
| US3 | Pruebas T046–T049 |
| US5 | Pruebas T055–T060; T062, T063; T065, T066 |
| US6 | T073, T075, T076; todo el bloque con el backend |
| Cierre | T081, T084 |

Ejemplo (US1):

```text
T012 reglas del respaldo      T014 lista por defecto      T016 controlador
T013 caso de uso              T015 adaptador simulado     T017 API de extremo a extremo
```

Las pruebas que usan tipos aún sin crear compilan cuando llega su implementación: primero fallan.

## Implementation Strategy

1. **MVP**: fases 1 a 3. El endpoint cumple el contrato con la lista por defecto.
2. **IA real**: US4, US2 y US3.
3. **Control y entorno**: US5 y US6.
4. **Cierre**: fase 9.

**Si el plazo no alcanza** (puntos de corte de `backlog-ia.md`): se mantiene el adaptador simulado o una conexión
mínima a LiteLLM y se anota lo no entregado. No se recortan la clave, el tiempo máximo, el respaldo, la prueba de fallo
del proveedor ni `AI-LOG.md` (US4, T046, T051, T081–T083).

## Notes

- 88 tareas. Parar en cada *checkpoint* y verificar.
- Commit sugerido por fase, cuando se pida: `feat(HU-12): <descripción en inglés>`.
- T029 y T072 necesitan una acción o decisión del usuario.
