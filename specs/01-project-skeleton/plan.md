# Implementation Plan: Repositorios y esqueleto del proyecto

**Branch**: `feature/01-project-skeleton` | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/01-project-skeleton/spec.md`

## Summary

Crear el backend como proyecto Maven de cuatro módulos que arranca sin servicios externos, crea el esquema y falla la
verificación ante dependencias prohibidas, violaciones de estilo o cobertura insuficiente. Los tres repositorios ya
existen; solo se verifican. Detalle de las decisiones en [research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.3 (WebFlux, Data R2DBC), Reactor

**Storage**: H2 en memoria mediante `r2dbc-h2`; esquema en `schema.sql`

**Testing**: JUnit 5, AssertJ, StepVerifier, Spring Boot Test, ArchUnit 1.5.1, JaCoCo 0.8.16, Checkstyle 14.4.0

**Target Platform**: Windows (desarrollo) y Linux (CI y contenedores)

**Project Type**: web-service

**Performance Goals**: arranque en menos de 30 segundos en el equipo de desarrollo (SC-002)

**Constraints**: sin llamadas bloqueantes; sin secretos en los repositorios; `application` solo depende de `domain` y
Reactor; `domain` de nada

**Scale/Scope**: 4 módulos, 2 tablas, ningún endpoint

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

La constitución (`.specify/memory/constitution.md`) es la plantilla sin rellenar: **no hay principios ratificados que
evaluar**. Se usan como puertas el ADR-0001 y las reglas de `CLAUDE.md`.

| Puerta | Antes de Phase 0 | Tras Phase 1 |
|---|---|---|
| Arquitectura hexagonal en cuatro módulos (ADR 2) | Cumple | Cumple |
| Reactivo, sin JDBC en el acceso a datos (ADR 1) | Cumple | Cumple |
| Cobertura 80 % y Checkstyle (ADR 11) | Cumple | Cumple |
| Identificadores en inglés y Conventional Commits | Cumple | Cumple |
| Sin secretos | Cumple | Cumple |
| PMD (guías de `CLAUDE.md`) | Desviación | Desviación, ver abajo |

## Project Structure

### Documentation (this feature)

```text
specs/01-project-skeleton/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/build.md
├── checklists/requirements.md
└── tasks.md                 # lo genera /speckit-tasks
```

### Source Code (repository root)

Raíz: repositorio `quipux-playlist-backend`. Paquete base: `com.quipux.playlist`.

```text
pom.xml                      # padre: versiones, JaCoCo, Checkstyle
mvnw, mvnw.cmd, .mvn/
config/checkstyle/checkstyle.xml
domain/
└── src/main/java/com/quipux/playlist/domain/package-info.java
application/
└── src/main/java/com/quipux/playlist/application/package-info.java
infrastructure/
├── src/main/java/com/quipux/playlist/infrastructure/package-info.java
└── src/main/resources/schema.sql
bootstrap/
├── src/main/java/com/quipux/playlist/PlaylistApplication.java
├── src/main/resources/application.yml
└── src/test/java/com/quipux/playlist/
    ├── ArchitectureTest.java
    ├── PlaylistApplicationTest.java     # arranque, reactivo, sin DataSource
    ├── SchemaTest.java                  # tablas, nombre único, clave foránea
    └── archfixture/                     # clases que incumplen a propósito
```

**Structure Decision**: la de la sección 2 del ADR-0001. `domain` y `application` quedan sin clases hasta HU-02.

### Código previo a esta especificación

En la rama hay archivos sin commit, escritos antes de la especificación. No cuentan como implementados:

- Cada archivo se revisa contra las tareas antes de aceptarlo.
- `Playlist`, `Song`, `InvalidPlaylistException` y `PlaylistTest` se eliminan: son reglas de negocio de HU-02.
- La regla de arquitectura de dominio debe pasar a permitir conjuntos vacíos.
- Faltan las pruebas de restricciones (FR-013), de no bloqueo (FR-014), la clase de ejemplo para `application` y el
  permiso de ejecución de `mvnw`.
- Los tests de `bootstrap` nunca llegaron a ejecutarse.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| No se configura PMD | El ADR acordó Checkstyle; Qodana cubrirá el análisis estático en HU-08 | Añadirlo duplica herramientas que nadie ha pedido |
| No se excluye `model` de la cobertura (las guías sí) | El dominio tendrá reglas que deben probarse | Excluirlo dejaría sin medir el código más importante |
| Módulos distintos de los de la guía hexagonal del equipo | El ADR fija `domain`, `application`, `infrastructure`, `bootstrap` | La guía permite adaptar los nombres al proyecto |
| `maven-antrun-plugin` para un `jacoco.exec` vacío | Sin él, un módulo sin pruebas pasa la cobertura (R3) | Revisarlo a mano no cumple FR-009 |
| Especificaciones dentro del backend y no en `specs/` de la raíz | La raíz no es un repositorio Git | Quedarían sin versionar |
