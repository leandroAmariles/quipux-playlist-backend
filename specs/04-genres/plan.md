# Implementation Plan: Géneros

**Branch**: `feature/04-genres` | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/04-genres/spec.md`

## Summary

Añadir `GET /genres`, que obtiene el catálogo de Spotify con *client credentials* y recurre a una lista local ante
cualquier fallo, y rechazar al crear una lista los géneros fuera de ese catálogo. Spotify real no entrega géneros, así
que el origen `SPOTIFY` se prueba con WireMock. Detalle en [research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.3 (WebFlux y `WebClient`, Security), Reactor, Jackson 3, MapStruct. Nueva,
solo en pruebas: `org.wiremock:wiremock-standalone`

**Storage**: sin cambios

**Testing**: JUnit 5, AssertJ, Mockito, StepVerifier, `@WebFluxTest`, WireMock, BlockHound, ArchUnit, JaCoCo,
Checkstyle

**Target Platform**: Windows (desarrollo) y Linux (CI y contenedores)

**Project Type**: web-service

**Performance Goals**: con Spotify caído, respuesta en menos de 4 s (SC-002)

**Constraints**: sin bloqueos en el event loop; `application` solo depende de `domain` y Reactor; 80 % de cobertura por
módulo; sin credenciales en el repositorio ni en los registros

**Scale/Scope**: 1 endpoint nuevo, 1 modificado, 1 integración externa, 0 tablas

## Constitution Check

La constitución sigue sin rellenar. Se usan como puertas el ADR-0001 y las reglas de `CLAUDE.md`.

| Puerta | Antes de Phase 0 | Tras Phase 1 |
|---|---|---|
| Arquitectura hexagonal en cuatro módulos | Cumple | Cumple |
| DTO y dominio separados, con mapeadores | Cumple | Cumple |
| Puertos con `Mono`; sin `.block()` | Cumple | Cumple |
| Errores tipados y mensajes en enums | Cumple | Cumple |
| Sin valores fijos en el código; listas en `.properties` | Cumple | Cumple |
| Cobertura 80 % y Checkstyle | Cumple | Cumple |
| Sin secretos en el repositorio | Cumple | Cumple |

## Project Structure

Raíz: `quipux-playlist-backend`. Paquete base: `com.quipux.playlist`. `+` nuevo, `~` cambia.

```text
domain/src/main/java/.../domain/
├── model/            + GenreCatalog   ~ Playlist
│   └── enums/        + GenreSource   ~ ErrorMessageEnum, ResponseCodeEnum
└── exception/        + GenreProviderUnavailableException

application/src/main/java/.../application/
├── port/in/          + GetGenresPort
├── port/out/         + GenreProviderPort
└── usecase/          + GetGenresUseCase   ~ CreatePlaylistUseCase

infrastructure/src/main/java/.../infrastructure/
├── in/rest/          + GenreController   ~ config/SecurityConfig, handler/ProblemDetails
│   ├── dto/          + GenresResponseDto
│   └── mapper/       + GenreDtoMapper
└── out/genre/        + SpotifyGenreProviderAdapter, LocalGenreProviderAdapter, SpotifySettings, SpotifyEndpoint
    ├── config/       + GenreProviderConfig
    └── dto/          + SpotifyTokenDto, SpotifyGenreSeedsDto
infrastructure/src/main/resources/    + local-genres.properties

bootstrap/src/main/java/.../config/   ~ UseCaseConfig
bootstrap/src/main/resources/         ~ application.yml
```

### Flujo

| Caso | Recorrido |
|---|---|
| Consultar géneros | token y rol → Spotify (token → géneros, 3 s en total) → filtrar → si falla o queda vacío, registrar y usar la lista local |
| Crear lista | token y rol → reglas de HU-02 → ¿alguna canción con género? → catálogo (una vez) → `400` o guardar |
| Arranque | cargar la lista local (si está vacía, no arranca) → avisar si faltan credenciales |

### Orden de trabajo

1. Dominio y puertos.
2. Consulta de géneros con respaldo: caso de uso, adaptadores y controlador.
3. Validación al crear.
4. Cierre: documentación y casos HTTP.

### Estrategia de pruebas

| Módulo | Qué prueba |
|---|---|
| `domain` | Filtrado y comparación del catálogo; `declaredGenres` |
| `application` | Respaldo ante error y catálogo vacío; validación al crear con puertos simulados |
| `infrastructure` | Adaptador de Spotify contra WireMock (éxito y cada fallo); lista local; controlador |
| `bootstrap` | `GET /genres` y `POST /lists` de extremo a extremo; roles; tiempo máximo; arranque; BlockHound |

Las pruebas se escriben antes que el código que comprueban.

### Cierre de la historia

README (variables y endpoint), `docs/backlog.md`, notas en el contrato y los casos HTTP de HU-02, y `requests.http`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| La excepción del proveedor no conserva la causa | La causa puede incluir el token | Registrarla expondría secretos |
| `WebClient.builder()` en lugar del *builder* de Boot | Evita sumar `spring-boot-starter-webclient` | No se necesita su personalización |
