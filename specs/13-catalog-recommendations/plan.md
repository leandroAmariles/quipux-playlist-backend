# Implementation Plan: Recomendaciones sobre un catálogo (RAG)

**Branch**: `feature/13-catalog-recommendations` (backend; la de infra se crea al empezar sus cambios) |
**Date**: 2026-11-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/13-catalog-recommendations/spec.md`

## Summary

El endpoint de HU-12 pasa a recomendar canciones de un catálogo propio. El caso de uso genera el embedding de la lista,
busca en pgvector las 20 canciones más cercanas que no estén en ella y pide al modelo que elija hasta 5; los datos
salen del catálogo. Si falla el modelo responde con las más cercanas (`SIMILITUD`) y si falla la búsqueda, con la
lista por defecto (`RESPALDO`). La indexación es un trabajo aparte de la composición. Detalle y evidencia en
[research.md](research.md).

## Technical Context

**Language/Version**: Java 21; un script de comprobación en Python (solo biblioteca estándar)

**Primary Dependencies**: las de HU-12. Nuevas, solo en `infrastructure`: `spring-ai-pgvector-store` (BOM 2.0.3),
`spring-boot-starter-jdbc`, `postgresql`; en pruebas, `testcontainers-postgresql` y `testcontainers-junit-jupiter`
(2.0.5, BOM de Boot)

**Storage**: H2 con R2DBC sin cambios; PostgreSQL 18.7 con pgvector 0.8.7 (JDBC) solo para el catálogo; caché y cuotas
en memoria

**Testing**: JUnit 5, AssertJ, Mockito, StepVerifier, WireMock, Testcontainers, BlockHound, ArchUnit, JaCoCo,
Checkstyle

**Target Platform**: Windows (desarrollo) y Linux (CI y contenedores); Ollama en CPU o GPU NVIDIA

**Project Type**: web-service, más composición de Docker en un segundo repositorio

**Performance Goals**: menos de 31 s en el peor caso y de 6 s con el catálogo caído (SC-003)

**Constraints**: JDBC siempre fuera del *event loop*; `domain` y `application` sin Spring AI ni JDBC; arranque sin
PostgreSQL, modelos ni claves; una petición de embeddings y una de chat como máximo; 80 % de cobertura; sin secretos
en repositorio ni registros

**Scale/Scope**: 0 endpoints nuevos, 1 base de datos y 1 integración nuevas, 1 tabla, 200 a 500 canciones; 40
requisitos

## Constitution Check

*La constitución sigue sin rellenar. Se usan como puertas los ADR-0001 y 0002, las reglas de `CLAUDE.md` y la
definición de terminado de los dos backlogs.*

| Puerta | Antes de Phase 0 | Tras Phase 1 |
|---|---|---|
| Arquitectura hexagonal; Spring AI y JDBC solo en `infrastructure` | Cumple | Cumple |
| DTO y dominio separados, con mapeadores | Cumple | Cumple |
| Puertos con `Mono`; sin `.block()` | Cumple | Cumple salvo la entrada de indexación, un proceso sin web que espera el resultado |
| Nada bloqueante en el *event loop* | Excepción acordada (JDBC, ADR) | Cumple: todo en `boundedElastic` (R1, R10) |
| Errores tipados y mensajes en enums | Cumple | Cumple |
| Sin valores fijos en el código; listas en ficheros | Cumple | Cumple; el catálogo en JSON (ver abajo) |
| Cobertura 80 % y Checkstyle | Cumple | Cumple: las pruebas sin Docker cubren los adaptadores (R13) |
| Sin secretos en el repositorio ni en registros | Cumple | Cumple |
| Identificadores en inglés | Cumple | Cumple |
| La IA no condiciona arranque, salud ni otros endpoints | Cumple | Cumple (R1, R6) |
| Archivos compartidos: solo se añaden bloques | Cumple | Cumple en lo común; se editan bloques propios de la IA |

## Project Structure

### Documentation (this feature)

```text
specs/13-catalog-recommendations/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── rest-api.md
│   ├── model-provider.md
│   ├── catalog.md
│   ├── configuration.md
│   └── ai-compose.md
├── checklists/requirements.md
└── tasks.md              # lo crea /speckit-tasks
```

### Source Code

Backend (`quipux-playlist-backend`), paquete base `com.quipux.playlist`. `+` nuevo, `~` cambia.

```text
pom.xml · infrastructure/pom.xml · bootstrap/pom.xml   ~ dependencias
tools/catalog/verify_catalog.py        +
docs/catalog-verification.md           +
README.md · AI-LOG.md · postman/       ~

domain/.../domain/
├── model/        + CatalogSong, SongCatalog, CatalogRules, ModelChoice, CatalogIndexPlan, IndexReport
│                 ~ Recommendation, RecommendationSet, RecommendationRules, RecommendationCacheKey
│   ├── constants/  ~ RecommendationConstraints
│   └── enums/      ~ RecommendationOrigin, ErrorMessageEnum
└── exception/    + SongCatalogUnavailableException

application/.../application/
├── port/in/      + IndexCatalogPort
├── port/out/     + SongCatalogPort, CatalogIndexPort   ~ RecommendationProviderPort
└── usecase/      + IndexCatalogUseCase                 ~ GetRecommendationsUseCase

infrastructure/.../infrastructure/
├── in/rest/      ~ mapper/RecommendationDtoMapper, doc/ApiOperationDocEnum, esquemas
├── out/catalog/  + FileSongCatalog, PgVectorSongCatalogAdapter, UnavailableSongCatalogAdapter, CatalogText
│   └── config/   + CatalogConfig
└── out/recommendation/
                  ~ PromptTemplate, LlmResponseParser, LlmRecommendationProviderAdapter,
                    SimulatedRecommendationProviderAdapter, LlmSettings, config/RecommendationConfig
                  + CandidateAliases, ChoiceSchema
infrastructure/src/main/resources/     + prompts/recommendations/v2.txt, catalog/songs.json
infrastructure/src/test/resources/     + catalog/init.sql

bootstrap/.../            + CatalogIndexApplication   ~ PlaylistApplication, config/UseCaseConfig
bootstrap/src/main/resources/application.yml           ~
bootstrap/src/test/...    ~ AiApiTestSupport y pruebas de recomendaciones, listas de OpenAPI y Postman
```

Infra (`quipux-playlist-infra`): ver [contracts/ai-compose.md](contracts/ai-compose.md).

**Structure Decision**: se mantienen los cuatro módulos; el catálogo va en `out/catalog`, junto a `out/genre` y
`out/recommendation`. `CatalogIndexApplication` no lleva estereotipo, para que la aplicación web no la recoja.

### Flujo

| Caso | Recorrido |
|---|---|
| Recomendar | ruta, token y rol → cuota → lista → ¿sin canciones? → caché → candidatas → modelo → guardar |
| Recuperación | `boundedElastic`: embedding y búsqueda (5 s) → quitar las de la lista y las no vigentes → 20 |
| Elección | identificadores cortos y esquema → `stream()` con el tiempo restante → traducir → validar |
| Indexar | `index-catalog` → leer lo indexado → plan → guardar por lotes → borrar sobrantes → salir |
| Arranque | cargar y validar catálogo, prompt y lista por defecto → elegir adaptadores según la configuración, sin tocar PostgreSQL |

### Orden de trabajo

Los bloques A, B y C pueden avanzar a la vez.

**A. Datos del catálogo**: `songs.json`, script de comprobación e informe. Necesita el contacto para MusicBrainz.

**B. Backend**

1. Dominio, puertos y casos de uso, con pruebas primero (incluye `SIMILITUD` y la caché por versión).
2. Contrato, mapeador, OpenAPI y Postman; adaptador de catálogo no disponible. **Primer punto entregable**: compila y
   pasa sin PostgreSQL.
3. Prompt `v2`, esquema, identificadores cortos y opciones mutadas, contra WireMock.
4. Adaptador pgvector: dependencias, `DataSource` aislado, búsqueda e indexación; Testcontainers.
5. Entrada `index-catalog`, configuración, extremo a extremo, BlockHound y registros sin secretos.

**C. Infra**: `catalog-db` y esquema, `additional_drop_params`, contexto de Ollama, `catalog-index`, `.env.example`.

**D. Cierre**: quickstart completo con mediciones, README de ambos repositorios, `AI-LOG.md`, `requests.http`,
criterios en `backlog-ia.md`, integrar `main`.

Si el plazo no alcanza, la historia se entrega completa o queda como diseño documentado, anotando lo no hecho. No se
recortan clave, tiempo máximo, respaldo, prueba de fallo ni `AI-LOG.md`.

### Estrategia de pruebas

| Módulo | Qué prueba |
|---|---|
| `domain` | Identificadores y versión estables, validez del catálogo, candidatas, elección, similitud, plan de indexación, clave de caché |
| `application` | Orden de pasos y cada origen; tiempo restante; no se guarda `SIMILITUD` ni `RESPALDO`; no se oculta `404` ni fallo de H2; indexación por lotes e interrupción; registros |
| `infrastructure` sin Docker | Fichero del repositorio; adaptador con almacén simulado (hilo `boundedElastic`, tiempo, errores traducidos); cuerpo de chat (modelo, `max_tokens`, esquema), bloques e inyección; analizador; configuración de cada fila de «Qué se activa» |
| `infrastructure` con Docker | pgvector: indexar, repetir, cambiar, buscar más de 40, base detenida; H2 sin tablas del catálogo |
| `bootstrap` | API con catálogo en memoria y WireMock: `IA`, `SIMILITUD`, `RESPALDO`, caché, una petición de cada tipo; arranque con PostgreSQL inalcanzable y transacciones R2DBC; `index-catalog`; un extremo a extremo con pgvector; BlockHound, ArchUnit, contrato, Postman y secretos |

### Riesgos

| Riesgo | Respuesta |
|---|---|
| `llama3.2:3b` elige como al azar entre las candidatas (medido) | Las candidatas ya son las más cercanas; `qwen2.5:7b` configurable; decisión pendiente (R9). HU-14 lo medirá |
| En CPU la respuesta con 50 canciones tarda hasta 18 s, sin medir con el contexto final | Medir; ajustar `AI_TIMEOUT`; `SIMILITUD` cubre el exceso |
| Reunir y comprobar 200 canciones consume buena parte de las 2,5 h | Empezar por el mínimo; script; bloque en paralelo |
| El contexto de 16384 usa unos 1,5 GB más | Medir; documentar |
| Listas con campos muy largos: el embedding solo ve las primeras | Límite conocido y documentado (R2) |
| Pruebas de HU-12 con IA dependen ahora de un catálogo | Catálogo en memoria en su soporte de pruebas |
| `catalog-index` falla y nadie lo ve | Código de salida, registro y quickstart; se responde `RESPALDO` y se puede repetir |
| Avisos nuevos en la imagen de PostgreSQL | Revisión y Trivy al implementar |

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| JDBC bloqueante en una API reactiva | Excepción acordada en el ADR para `PgVectorStore` | Adaptador R2DBC propio: descartado en el ADR |
| Segunda base de datos | Búsqueda vectorial | Ya decidido (ADR, sección 4) |
| SQL propio además de `PgVectorStore` | La indexación idempotente necesita saber qué hay | El almacén no permite listar |
| Segunda entrada de arranque (`index-catalog`) | Indexar sin atar la API a PostgreSQL | La aplicación no arranca sin web; una tarea al inicio necesita reintentos |
| Esquema JSON por petición, no pedido por el ADR | Sin él casi ninguna respuesta del modelo sirve | Cambiar el prompt altera un texto aceptado |
| Dos límites sobre la llamada al modelo | El tiempo total incluye la recuperación | Un reparto fijo quita tiempo al modelo |
| Catálogo en JSON, no en `.properties` | Cientos de registros de cinco campos | Más de 1000 líneas de claves |
| Esquema SQL copiado en las pruebas | Dos repositorios y esquema creado por la composición | Que lo cree la aplicación incumple FR-015 |
