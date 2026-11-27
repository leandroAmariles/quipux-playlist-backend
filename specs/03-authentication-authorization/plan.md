# Implementation Plan: Autenticación y autorización

**Branch**: `feature/03-authentication-authorization` | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/03-authentication-authorization/spec.md`

## Summary

Añadir `POST /auth/login`, que emite un JWT firmado para dos usuarios de demostración guardados con BCrypt, y proteger
el resto de la API por rol con Spring Security reactivo. El inicio de sesión sigue las capas hexagonales; la validación
del token y las reglas de acceso viven en `infrastructure`. Detalle en [research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.3 (WebFlux, Data R2DBC), Spring Security 7.1.3, Reactor, Jackson 3, MapStruct
1.6.3. Nuevas: `spring-boot-starter-security-oauth2-resource-server` y, solo en pruebas,
`spring-boot-starter-security-oauth2-resource-server-test`; versiones del BOM de Boot

**Storage**: H2 en memoria mediante `r2dbc-h2`; tabla nueva `app_user`

**Testing**: JUnit 5, AssertJ, Mockito, StepVerifier, `@WebFluxTest`, `@DataR2dbcTest`, `WebTestClient`, ArchUnit,
BlockHound, JaCoCo y Checkstyle

**Target Platform**: Windows (desarrollo) y Linux (CI y contenedores)

**Project Type**: web-service

**Performance Goals**: inicio de sesión en menos de 1 segundo (SC-005); se mantiene el de HU-02

**Constraints**: sin llamadas bloqueantes en el event loop; `application` solo depende de `domain` y Reactor; `domain`
de nada; 80 % de cobertura por módulo; sin secretos en el repositorio

**Scale/Scope**: 1 endpoint nuevo, 4 protegidos, 1 tabla, 2 usuarios y 2 roles

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

La constitución (`.specify/memory/constitution.md`) sigue sin rellenar: **no hay principios ratificados que evaluar**.
Se usan como puertas el ADR-0001 y las reglas de `CLAUDE.md`.

| Puerta | Antes de Phase 0 | Tras Phase 1 |
|---|---|---|
| Arquitectura hexagonal en cuatro módulos (ADR 2) | Cumple | Cumple |
| DTO, entidad y dominio separados, con mapeadores (ADR 2) | Cumple | Cumple |
| Puertos con `Mono` y `Flux`; sin `.block()` (ADR 1 y 2) | Cumple | Desviación solo en el arranque |
| JWT con expiración, BCrypt, roles y clave por entorno (ADR 9) | Cumple | Cumple |
| `ProblemDetail` y códigos tipados (ADR 8, `CLAUDE.md`) | Cumple | Cumple |
| Un único punto que traduce errores a HTTP (`CLAUDE.md`) | Cumple | Desviación, ver abajo |
| Sin valores de negocio fijos en el código (enums y constantes) | Cumple | Cumple |
| Cobertura 80 % por módulo y Checkstyle (ADR 11) | Cumple | Cumple |
| Sin secretos en el repositorio (definición de terminado) | Cumple | Cumple |

## Project Structure

### Documentation (this feature)

```text
specs/03-authentication-authorization/
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

Raíz: repositorio `quipux-playlist-backend`. Paquete base: `com.quipux.playlist`. Solo se muestra lo nuevo (`+`) y lo
que cambia (`~`).

```text
domain/src/main/java/.../domain/
├── model/                    + User, Credentials, AccessToken   ~ Playlist
│   ├── constants/            + PasswordConstraints
│   └── enums/                + Role, DemoUser, ForbiddenNameSequence   ~ ResponseCodeEnum
└── exception/                + InvalidLoginRequestException, InvalidCredentialsException

application/src/main/java/.../application/
├── port/in/                  + LoginPort, RegisterDemoUsersPort
├── port/out/                 + UserRepositoryPort, PasswordHasherPort, TokenIssuerPort
└── usecase/                  + LoginUseCase, RegisterDemoUsersUseCase

infrastructure/src/main/java/.../infrastructure/
├── in/rest/                  + AuthController
│   ├── dto/                  + LoginRequestDto, TokenResponseDto
│   ├── mapper/               + AuthDtoMapper
│   ├── handler/              + ProblemDetails   ~ GlobalExceptionHandler
│   ├── security/             + SecurityErrorHandlers, TokenExpiryValidator
│   └── config/               + SecurityConfig (cadenas, filtro de rutas, decodificador)
├── security/                 + SigningKey, TokenClaim (comunes a emitir y validar)
│   └── config/               + JwtConfig (clave, reloj, codificador)
├── out/security/             + BCryptPasswordHasherAdapter, JwtTokenIssuerAdapter
└── out/persistence/          + UserPersistenceAdapter
    ├── entity/               + UserEntity
    ├── mapper/               + UserEntityMapper
    └── repository/           + UserR2dbcRepository
infrastructure/src/main/resources/        ~ schema.sql

bootstrap/src/main/java/com/quipux/playlist/
├── config/                   ~ UseCaseConfig
└── startup/                  + DemoUsersInitializer
bootstrap/src/main/resources/             ~ application.yml
```

**Structure Decision**: la de la sección 2 del ADR-0001. La lógica con reglas queda fuera de los paquetes `config`, que
la cobertura excluye.

### Flujo

| Caso | Recorrido |
|---|---|
| Iniciar sesión | DTO a `Credentials` (`400`) → más de 72 bytes (`401`) → buscar usuario → comparar con su resumen o con el señuelo (`401`) → emitir token → `200` |
| Petición protegida | filtro de rutas (`400`) → token: firma y vencimiento (`401`) → rol (`403`) → flujo de HU-02 |
| Arranque | validar clave y contraseñas → resumir → guardar los dos usuarios → abrir el puerto |

`Credentials` hace de comando de inicio de sesión: no se añade una clase `Command` idéntica.

### Orden de trabajo

1. Regla de nombres (FR-018) y ajuste de las pruebas de HU-02 que usan `%2F`.
2. Inicio de sesión, de dominio a controlador, y usuarios de demostración.
3. Seguridad: cadenas, reglas por rol, errores y filtro de rutas; adaptar las pruebas de HU-02.
4. Cierre.

La seguridad va después del inicio de sesión para que las pruebas existentes puedan pedir un token real.

### Estrategia de pruebas

| Módulo | Qué prueba |
|---|---|
| `domain` | Reglas y límites de [data-model.md](data-model.md), incluidos los nombres parecidos que sí se aceptan y que `toString` no muestra secretos |
| `application` | Inicio de sesión con puertos simulados: éxito, usuario inexistente (compara con el señuelo), contraseña mala, 72 y 73 bytes; registro de demostración |
| `infrastructure` | Controlador (`@WebFluxTest`); persistencia de usuarios; BCrypt; emisión y validación del token; vencimiento en `exp - 1 ms`, `exp` y sin `exp`; clave de 31 y 32 bytes |
| `bootstrap` | Matriz de rol por operación; `401` por cada forma de token inválido; `403` antes que `404` y `400`; sin cookies; `;` como parámetro de matriz; nombres especiales; arranque sin variables; BlockHound y tiempos |

Las pruebas se escriben antes que el código que comprueban.

### Cierre de la historia

- README: quitar el aviso de que no hay autenticación y documentar las variables y el inicio de sesión.
- Anotar en el ADR-0001 (secciones 8 y 9) los nombres que se dejan de aceptar y las reglas relajadas del filtro.
- Añadir al contrato y a `requests.http` de HU-02 la nota de que ahora exigen token.
- Marcar los criterios de HU-03 en `docs/backlog.md`.
- Generar `requests.http` en esta carpeta (regla del equipo).

### Efectos en historias posteriores

- HU-06: OpenAPI y Swagger UI tendrán que abrirse sin token.
- HU-07 y HU-09: Compose y Kubernetes tendrán que definir las tres variables, y las probes abrirse sin token.

## Cambios durante la implementación

Salen de la [revisión de código](code-review.md):

- `ErrorMessageEnum` y `FieldNameEnum` en `domain/model/enums`; los mensajes dejan de estar en el código que los lanza.
- Los códigos de autenticación son `AUTH_ERROR_001` a `AUTH_ERROR_004`.
- `TokenLifetime` en `infrastructure/security`; los `@Value` van en atributos.
- Los nombres especiales de las pruebas están en `special-names.properties`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Errores de seguridad escritos fuera de `GlobalExceptionHandler` | Los filtros actúan antes de los controladores; el estado se decide en `ProblemDetails`, compartido | El manejador no recibe esos errores |
| `block()` al registrar los usuarios en el arranque | Deben existir antes de abrir el puerto; es el hilo principal | `ApplicationRunner` deja un instante sin usuarios |
| Nombres de lista que HU-02 aceptaba pasan a `400` | Decisión del usuario (research R1) | Aceptarlos desactiva diez reglas del filtro de rutas |
| Dos cadenas de seguridad | Un token inválido no debe impedir iniciar sesión | Con una sola, el inicio de sesión daría `401` |
| Validador de vencimiento propio | La spec rechaza en el instante exacto | El de Spring lo acepta |
| Desviaciones de HU-02 (sin `ResponseDto`, Lombok, PMD ni `TechnicalException`) | Siguen vigentes | Ver el plan de HU-02 |
