# Implementation Plan: Recomendaciones con IA

**Branch**: `feature/12-ai-recommendations` (backend e infra) | **Date**: 2026-11-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/12-ai-recommendations/spec.md`

## Summary

Añadir `GET /lists/{listName}/recommendations`. Un caso de uso aplica la cuota por usuario, busca la lista, consulta
una caché local y pide sugerencias a un modelo mediante Spring AI contra LiteLLM; valida lo recibido y, ante cualquier
fallo, responde con una lista por defecto. La IA está desactivada por defecto. En infra, una composición adicional
levanta Ollama y LiteLLM. Detalle y evidencia en [research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.3 (WebFlux, Security), Reactor, Jackson 3, MapStruct. Nuevas, solo en
`infrastructure`: `spring-ai-starter-model-openai` (BOM 2.0.3), `resilience4j-circuitbreaker` y `resilience4j-reactor`
2.4.0, `caffeine` (versión de Boot)

**Storage**: sin cambios en H2; caché y cuotas en memoria; modelos en un volumen de Docker

**Testing**: JUnit 5, AssertJ, Mockito, StepVerifier, `@WebFluxTest`, WireMock, BlockHound, ArchUnit, JaCoCo,
Checkstyle

**Target Platform**: Windows (desarrollo) y Linux (CI y contenedores); Ollama en CPU o GPU NVIDIA

**Project Type**: web-service, más composición de Docker en un segundo repositorio

**Performance Goals**: respuesta en menos de 31 s en el peor caso y de 1 s sin llamada al modelo (SC-002)

**Constraints**: sin bloqueos en el *event loop*; `application` solo depende de `domain` y Reactor; 80 % de cobertura
por módulo; sin claves en el repositorio, y sin claves ni prompts en los registros; arranque y pruebas sin modelos ni
clave; una petición al proveedor por recomendación

**Scale/Scope**: 1 endpoint, 1 integración externa, 0 tablas, 1 réplica; 47 requisitos

## Constitution Check

*La constitución sigue sin rellenar. Se usan como puertas los ADR-0001 y 0002, las reglas de `CLAUDE.md` y la
definición de terminado de los dos backlogs.*

| Puerta | Antes de Phase 0 | Tras Phase 1 |
|---|---|---|
| Arquitectura hexagonal en cuatro módulos; Spring AI solo en `infrastructure` | Cumple | Cumple |
| DTO y dominio separados, con mapeadores | Cumple | Cumple |
| Puertos con `Mono`; sin `.block()` | Cumple | Cumple (`identity()` es un dato fijo) |
| Errores tipados y mensajes en enums | Cumple | Cumple |
| Sin valores fijos en el código; listas en `.properties` | Cumple | Cumple |
| Cobertura 80 % y Checkstyle | Cumple | Cumple |
| Sin secretos en el repositorio ni en registros | Cumple | Cumple (R6) |
| Identificadores en inglés | Cumple | Cumple |
| La IA no condiciona arranque, salud ni otros endpoints | Cumple | Cumple (R1, R4) |
| Archivos compartidos: solo se añaden bloques | Cumple | Cumple |

Las reglas generales piden `@UtilityClass`; el proyecto no usa Lombok y se sigue su convención (clase final con
constructor privado).

## Project Structure

### Documentation (this feature)

```text
specs/12-ai-recommendations/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── rest-api.md
│   ├── model-provider.md
│   ├── configuration.md
│   └── ai-compose.md
├── checklists/requirements.md
└── tasks.md              # lo crea /speckit-tasks
```

### Source Code

Backend (`quipux-playlist-backend`), paquete base `com.quipux.playlist`. `+` nuevo, `~` cambia.

```text
pom.xml                               ~ BOM de Spring AI y versión de Resilience4j
AI-LOG.md                             + 
README.md                             ~ uso de la IA y "Autoría IA"

domain/.../domain/
├── model/            + Recommendation, RecommendationSet, ProviderIdentity, RecommendationCacheKey,
│   │                   CachedRecommendations, QuotaDecision, SongSample, RecommendationRules,
│   │                   DefaultRecommendations
│   ├── constants/    + RecommendationConstraints
│   └── enums/        + RecommendationOrigin   ~ ErrorMessageEnum, ResponseCodeEnum, FieldNameEnum
└── exception/        + RecommendationRateLimitException, RecommendationProviderUnavailableException

application/.../application/
├── port/in/          + GetRecommendationsPort
├── port/out/         + RecommendationProviderPort, DefaultRecommendationsPort, RecommendationCachePort,
│                       RequestQuotaPort
└── usecase/          + GetRecommendationsUseCase

infrastructure/pom.xml                ~ dependencias nuevas
infrastructure/.../infrastructure/
├── in/rest/          + RecommendationController   ~ handler/GlobalExceptionHandler, handler/ProblemDetails,
│   │                                                  doc/ApiOperationDocEnum, doc/ApiResponseDocEnum,
│   │                                                  doc/OpenApiContractCustomizer (Retry-After)
│   ├── dto/          + RecommendationsResponseDto, RecommendationDto
│   └── mapper/       + RecommendationDtoMapper
└── out/recommendation/
    ├──               + LlmRecommendationProviderAdapter, SimulatedRecommendationProviderAdapter,
    │                   PromptTemplate, LlmResponseParser, LlmSettings, FileDefaultRecommendationsAdapter,
    │                   CaffeineRecommendationCacheAdapter, SlidingWindowRequestQuotaAdapter
    └── config/       + RecommendationConfig
infrastructure/src/main/resources/    + prompts/recommendations/v1.txt, default-recommendations.properties

bootstrap/.../config/                 ~ UseCaseConfig
bootstrap/src/main/resources/         ~ application.yml
bootstrap/src/test/resources/         ~ config/application.yml, openapi-operations.properties,
                                        postman-collection.properties
postman/                              ~ colección
```

`SecurityConfig` no cambia: la ruta ya queda bajo `GET`/`HEAD` `/lists/**`.

Infra (`quipux-playlist-infra`): ver [contracts/ai-compose.md](contracts/ai-compose.md).

**Structure Decision**: se mantienen los cuatro módulos y se agrupa lo nuevo de salida en `out/recommendation`, como
`out/genre`.

### Flujo

| Caso | Recorrido |
|---|---|
| Recomendar | ruta, token y rol → cuota → lista → ¿sin canciones? → caché → proveedor → validar → guardar |
| Llamada al modelo | permiso → `stream()` en `boundedElastic` con `timeout` y cortacircuitos → agregar → interpretar JSON |
| Cualquier fallo del proveedor | registrar el motivo → lista por defecto sin las canciones de la lista → `RESPALDO` |
| Arranque | cargar prompt y lista por defecto (si no cumplen, no arranca) → elegir proveedor simulado o real → avisar si falta la clave |

### Orden de trabajo

Los bloques A (backend) y B (infra) pueden avanzar a la vez.

**A. Backend**

1. `AI-LOG.md` con su plantilla y los prompts ya usados (especificación y plan).
2. Dominio, puertos y caso de uso, con pruebas primero.
3. Endpoint con adaptador simulado, lista por defecto, cuota y caché; OpenAPI y Postman. **Primer punto entregable**:
   funciona sin modelos.
4. Dependencias de Spring AI y configuración; la verificación sigue en verde sin clave.
5. **Generación del cliente por un asistente en una sesión nueva** (R15). Copiar a `AI-LOG.md` el prompt y el código
   sin cambios. Nada del paso 6 se escribe antes.
6. Revisión crítica punto por punto; pruebas contra WireMock que demuestren cada defecto; correcciones.
7. BlockHound, prueba de registros sin secretos y prueba de extremo a extremo.

**B. Infra**

1. Revisar avisos, verificar firma y digests.
2. `compose.ai.yaml`, configuración de LiteLLM, descarga y precarga de modelos.
3. GPU, comprobaciones del quickstart y medición de latencia y memoria.

**C. Cierre**: README de ambos repositorios, "Autoría IA", Dependabot si ya existe, `requests.http`, criterios en
`backlog-ia.md`, integrar `main` y quickstart completo.

Si el plazo no alcanza, se recorta según los puntos de corte del backlog: primero B (queda la conexión simulada), sin
tocar clave, tiempo máximo, respaldo, prueba de fallo ni `AI-LOG.md`.

### Estrategia de pruebas

| Módulo | Qué prueba |
|---|---|
| `domain` | Reglas de validación y respaldo, muestra de canciones, huella, lista por defecto |
| `application` | Orden cuota → lista → caché → proveedor; respaldo ante cada error; no se oculta `404` ni fallo del repositorio; el respaldo no se guarda; registros |
| `infrastructure` | Adaptador real contra WireMock: éxito y cada fila de la tabla de fallos, **una** petición, tiempo agotado, cortacircuitos, concurrencia (también con el suscriptor cancelado), delimitador e instrucciones inyectadas, clave vacía; cuota y caché con reloj simulado; archivos; controlador |
| `bootstrap` | Extremo a extremo con IA desactivada y con WireMock: `200`, `401`, `403`, `404`, `429`, caché, lista vacía; arranque sin clave; API con el proveedor caído; clave y prompt ausentes en la salida; BlockHound; ArchUnit; contrato y Postman |

Las pruebas usan tiempos cortos por configuración. Cada prueba generada por IA o añadida a mano se anota en
`AI-LOG.md`.

### Riesgos

| Riesgo | Respuesta |
|---|---|
| Un modelo pequeño devuelve a menudo respuestas inválidas | Modo JSON y descarte por elemento; medir y, si hace falta, probar un modelo de 8B |
| 30 s no bastan en CPU o en frío | Precarga, medición y `AI_TIMEOUT` |
| Memoria del motor de Docker (15,2 GiB) compartida | Medir; ampliar en `.wslconfig` |
| Puntos de LiteLLM sin confirmar (R3, R12) | Se comprueban en el quickstart antes de dar la historia por hecha |
| Conflictos con HU-08 en archivos compartidos | Solo añadir bloques e integrar `main` con frecuencia |
| Avisos de seguridad nuevos | Revisión el día de implementar; subir versión y digest |

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Llamada desacoplada de la cancelación con `cache()` | Cancelar no cierra la conexión; el permiso debe durar lo que la llamada | Liberarlo al cancelar permite más de 2 generaciones a la vez |
| Dos tiempos máximos con el mismo valor | Solo el del SDK cierra la conexión; el de Reactor garantiza la respuesta | Con uno solo se pierde una de las dos garantías |
| `boundedElastic` para una API reactiva | La preparación de la petición bloquea al cargar clases | Llamar desde el *event loop* falla en BlockHound sin calentamiento |
| *Logger* de Spring AI apagado | Registra el cuerpo de error del proveedor | Dejarlo puede escribir el prompt |
| La excepción del proveedor no conserva la causa | La causa puede incluir el prompt | Registrarla incumple FR-025 |
| LiteLLM entre el backend y Ollama, no exigido por el módulo | Decisión del ADR-0002 | Ya decidido |
