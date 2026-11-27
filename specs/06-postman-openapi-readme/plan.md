# Implementation Plan: Postman, OpenAPI y README

**Branch**: `feature/06-postman-openapi-readme` | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/06-postman-openapi-readme/spec.md`

## Summary

Publicar el contrato OpenAPI y Swagger UI con springdoc, completando desde enums lo que no se genera solo (códigos,
errores y Bearer JWT); entregar una colección de Postman ejecutable y repetible, y reorganizar el README. Las rutas de
documentación leen del JAR de forma bloqueante, así que se ejecutan en `boundedElastic`. Detalle en
[research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.3 (WebFlux y Security), Reactor, Jackson 3. Nueva:
`org.springdoc:springdoc-openapi-starter-webflux-ui:3.2.0`

**Storage**: sin cambios

**Testing**: JUnit 5, AssertJ, StepVerifier, WebFlux, BlockHound, ArchUnit, JaCoCo, Checkstyle; `newman` 6.2.3 a mano

**Target Platform**: Windows (desarrollo) y Linux (CI y contenedores)

**Project Type**: web-service

**Performance Goals**: sin objetivo propio; las operaciones existentes mantienen los suyos

**Constraints**: sin bloqueos en el event loop; sin cambios de comportamiento (FR-020); 80 % de cobertura por módulo; sin
secretos en el repositorio

**Scale/Scope**: 6 operaciones documentadas, 4 rutas públicas nuevas, 1 colección, 1 README, 0 tablas

## Constitution Check

La constitución sigue sin rellenar. Se usan como puertas el ADR-0001 y las reglas de `CLAUDE.md`.

| Puerta | Antes de Phase 0 | Tras Phase 1 |
|---|---|---|
| Arquitectura hexagonal en cuatro módulos | Cumple | Cumple: todo queda en `infrastructure` |
| Sin llamadas bloqueantes en el event loop | Por comprobar | Cumple con el filtro (R4) |
| Textos y valores en enums, sin comentarios | Cumple | Cumple |
| Cobertura 80 % y Checkstyle | Cumple | Cumple |
| Sin secretos en el repositorio | Cumple | Cumple |
| Compatibilidad con Boot 4 (ADR, sección 1) | Por comprobar | Cumple (R1) |

## Project Structure

### Documentation (this feature)

```text
specs/06-postman-openapi-readme/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/        openapi.md, postman-collection.md
├── requests.http     al terminar la implementación
└── tasks.md          /speckit-tasks
```

### Source Code (repository root)

Raíz: `quipux-playlist-backend`. Paquete base: `com.quipux.playlist`. `+` nuevo, `~` cambia.

```text
pom.xml                                  ~ versión de springdoc
infrastructure/
├── pom.xml                              ~ dependencia
└── src/main/java/.../infrastructure/in/rest/
    ├── doc/                             + DocumentationPath, DocumentationSchedulingFilter,
    │                                      ApiOperationDocEnum, ApiResponseDocEnum, OpenApiContractCustomizer
    ├── config/                          + OpenApiConfig   ~ SecurityConfig
    ├── dto/                             ~ @Schema en los cinco DTO
    └── PlaylistController.java          ~ constante pública con la ruta de una lista
bootstrap/
├── src/main/resources/application.yml   ~ springdoc.default-produces-media-type, playlist.openapi.version
└── src/test/                            + OpenApiContractTest, DocumentationAccessApiTest, PostmanCollectionTest,
                                           resources/openapi-operations.properties   ~ PlaylistNonBlockingTest
postman/                                 + quipux-playlist.postman_collection.json
README.md                                ~ reorganizado
docs/                                    ~ backlog.md, adr/0001 (concreción en la sección 4)
```

**Structure Decision**: la documentación es un detalle del adaptador web, así que vive en `infrastructure/in/rest`.
`domain` y `application` no cambian. La lógica va en `doc/`, que cuenta para la cobertura; `config/` solo ensambla.

### Flujo

| Caso | Recorrido |
|---|---|
| Abrir la documentación | ruta permitida sin token → filtro pasa a `boundedElastic` → springdoc o archivo estático |
| Generar el contrato | springdoc lee controladores y DTO → el personalizador aplica los enums → se cachea |
| Cualquier otra petición | sin cambios: el filtro no la toca |

### Orden de trabajo

1. Contrato y Swagger UI: pruebas, dependencia, rutas públicas, filtro y personalizador.
2. Colección de Postman: prueba del archivo, colección y dos ejecuciones con `newman`.
3. README, con los enlaces y la validación desde una copia limpia.
4. Cierre.

La spec da P1 a las tres historias. El README va al final porque describe lo que entregan las otras dos.

### Estrategia de pruebas

| Módulo | Qué prueba |
|---|---|
| `infrastructure` | El personalizador (códigos, errores, `Location`, seguridad); el filtro cambia de hilo solo en documentación |
| `bootstrap` | Contrato publicado contra `openapi-operations.properties`; rutas públicas y cerradas; BlockHound; archivo de Postman |
| manual | Swagger UI en el navegador, `newman` dos veces y README ([quickstart.md](quickstart.md)) |

Las pruebas se escriben antes que el código que comprueban. `openapi-operations.properties` se escribe a partir de la
spec, no del enum, para que la prueba detecte diferencias.

### Cierre de la historia

README, `docs/backlog.md` (criterios marcados y los dos añadidos: FR-004 y FR-012), concreción en el ADR-0001,
`requests.http` con las rutas de documentación y revisión de comentarios para `code-review.md`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Lecturas bloqueantes en las rutas de documentación, movidas a `boundedElastic` | Son de springdoc y de Spring | Precargar el contrato no cubre otros idiomas ni los archivos |
| Los roles se repiten en `ApiOperationDocEnum` y en `SecurityConfig` | El contrato debe decir quién puede | Derivarlos de la seguridad exige acoplar el personalizador a sus reglas |
| Jackson 2 convive con Jackson 3 | Lo exige swagger-core | springdoc no ofrece otra vía |
