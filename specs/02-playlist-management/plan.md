# Implementation Plan: Gestión de listas de reproducción

**Branch**: `feature/02-playlist-management` | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/02-playlist-management/spec.md`

## Summary

Crear, consultar y borrar listas con sus canciones mediante cuatro endpoints reactivos, repartidos en las capas
hexagonales ya existentes. Las reglas de datos viven en el dominio; la unicidad y la atomicidad, en la persistencia
R2DBC; los errores salen como `ProblemDetail` desde un único manejador. Detalle en [research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.3 (WebFlux, Data R2DBC), Reactor, Jackson 3. Nuevas: MapStruct 1.6.3 y, solo
en pruebas, BlockHound 1.0.19 y los módulos de prueba de WebFlux y R2DBC

**Storage**: H2 en memoria mediante `r2dbc-h2`; `schema.sql` sin cambios

**Testing**: JUnit 5, AssertJ, Mockito, StepVerifier, `@WebFluxTest`, `@DataR2dbcTest`, `WebTestClient`, ArchUnit,
BlockHound, JaCoCo y Checkstyle

**Target Platform**: Windows (desarrollo) y Linux (CI y contenedores)

**Project Type**: web-service

**Performance Goals**: cada operación en menos de 1 segundo con 100 listas de 20 canciones (SC-006)

**Constraints**: sin llamadas bloqueantes; `application` solo depende de `domain` y Reactor; `domain` de nada; 80 % de
cobertura por módulo

**Scale/Scope**: 4 endpoints, 2 tablas, sin autenticación ni paginación

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

La constitución (`.specify/memory/constitution.md`) sigue sin rellenar: **no hay principios ratificados que evaluar**.
Se usan como puertas el ADR-0001 y las reglas de `CLAUDE.md`.

| Puerta | Antes de Phase 0 | Tras Phase 1 |
|---|---|---|
| Arquitectura hexagonal en cuatro módulos (ADR 2) | Cumple | Cumple |
| DTO, entidad y dominio separados, con mapeadores (ADR 2) | Cumple | Cumple |
| Puertos con `Mono` y `Flux`; sin `.block()` (ADR 1 y 2) | Cumple | Cumple, con la salvedad de research R10 |
| Transacciones reactivas en el adaptador (ADR 1) | Cumple | Cumple |
| Contrato en español, `409`, `ProblemDetail` (ADR 8) | Cumple | Cumple |
| Cobertura 80 % por módulo y Checkstyle (ADR 11) | Cumple | Cumple |
| Controlador sin lógica; errores tipados y manejador único (`CLAUDE.md`) | Cumple | Cumple |
| Identificadores en inglés y Conventional Commits | Cumple | Cumple |
| Guías del equipo (PMD, Lombok, sobre de errores) | Desviación | Desviación, ver abajo |

## Project Structure

### Documentation (this feature)

```text
specs/02-playlist-management/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/rest-api.md
├── checklists/requirements.md
└── tasks.md                 # lo genera /speckit-tasks
```

### Source Code (repository root)

Raíz: repositorio `quipux-playlist-backend`. Paquete base: `com.quipux.playlist`.

```text
domain/src/main/java/.../domain/
├── model/                   # Playlist, Song
│   ├── constants/           # PlaylistConstraints
│   └── enums/               # ResponseCodeEnum, ReservedPlaylistName, ForbiddenNameCharacter
└── exception/               # BusinessException e Invalid, NotFound y Duplicate

application/src/main/java/.../application/
├── port/in/                 # CreatePlaylistPort, GetPlaylistsPort, GetPlaylistPort, DeletePlaylistPort
├── port/out/                # PlaylistRepositoryPort
└── usecase/                 # un caso de uso por puerto de entrada

infrastructure/src/main/java/.../infrastructure/
├── in/rest/                 # PlaylistController
│   ├── dto/                 # PlaylistDto, SongDto
│   ├── mapper/              # PlaylistDtoMapper
│   ├── handler/             # GlobalExceptionHandler
│   └── config/              # tipos estrictos en JSON
└── out/persistence/         # PlaylistPersistenceAdapter
    ├── entity/              # PlaylistEntity, SongEntity, PlaylistSongRow
    ├── mapper/              # PlaylistEntityMapper
    └── repository/          # repositorios R2DBC

bootstrap/src/main/java/com/quipux/playlist/config/    # UseCaseConfig: ensamblaje

*/src/test/java/             # pruebas por módulo; de extremo a extremo en bootstrap
```

**Structure Decision**: la de la sección 2 del ADR-0001. `domain` y `application` reciben sus primeras clases.

### Flujo

| Operación | Controlador → caso de uso → adaptador |
|---|---|
| Crear | DTO a dominio (valida) → guardar → en una transacción: insertar lista y canciones en orden → `201` |
| Listar | una consulta `LEFT JOIN` → agrupar → `200` |
| Consultar | la misma consulta filtrada; vacío → `PlaylistNotFoundException` |
| Borrar | en una transacción: borrar canciones y lista; 0 filas → `PlaylistNotFoundException` |

El `Playlist` del dominio hace de comando de creación: no se añade una clase `Command` idéntica.

### Estrategia de pruebas

| Módulo | Qué prueba |
|---|---|
| `domain` | Cada regla y límite de [data-model.md](data-model.md) |
| `application` | Casos de uso con el puerto simulado: éxito, no encontrado y propagación de errores |
| `infrastructure` | Controlador y manejador (`@WebFluxTest`); persistencia sobre H2: orden, duplicados y reversión provocada con restricciones temporales en la base de datos |
| `bootstrap` | Ciclo completo con el servidor real, nombres especiales y `Location`, creación simultánea, BlockHound y tiempos |

Las pruebas se escriben antes que el código que comprueban.

### Cierre de la historia

- Actualizar el README (endpoints y que aún no hay autenticación) y marcar los criterios en `docs/backlog.md`.
- Generar `requests.http` en esta carpeta (regla del equipo).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Errores con `ProblemDetail`, no con el sobre `ResponseDto` de `error-translation` | Lo fijan el ADR y la spec | Dos formatos de error confundirían al cliente |
| Sin `TechnicalException` | No hay integraciones externas; lo inesperado cae en el manejador de reserva | Una jerarquía sin usos |
| Sin Lombok; constantes en clases `final` | Los `record` bastan y el dominio no añade dependencias | Añadir Lombok solo para `@UtilityClass` |
| Módulos y paquetes distintos de la guía hexagonal | El ADR fija los cuatro módulos | La guía permite adaptar nombres |
| BlockHound y una opción de JVM en las pruebas de `bootstrap` | `r2dbc-h2` ejecuta en el hilo que llama (R10) | Revisar el código no detecta bloqueos del controlador de datos |
| Sin PMD ni exclusión de `model` en la cobertura | Mismas razones que en HU-01 | — |
