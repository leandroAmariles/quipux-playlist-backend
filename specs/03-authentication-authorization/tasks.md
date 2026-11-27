---
description: "Tareas de HU-03: autenticación y autorización"
---

# Tasks: Autenticación y autorización

**Input**: documentos de diseño en `specs/03-authentication-authorization/`

**Tests**: incluidas (SC-002 y plan). En cada fase las pruebas se escriben primero y se confirma que fallan.

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
- Sin `.block()`, también en pruebas (`StepVerifier` o `WebTestClient`). Única excepción: T037.
- Los valores de negocio van en enums o constantes, no sueltos en el código.
- Ninguna contraseña, resumen, clave o token aparece en logs, mensajes de error o `toString`.
- Ningún secreto real en el repositorio; los valores de prueba deben verse falsos.
- No restaurar comentarios que el usuario haya quitado.

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Añadir a `infrastructure/pom.xml` `spring-boot-starter-security-oauth2-resource-server` y, en `test`,
      `spring-boot-starter-security-oauth2-resource-server-test`, sin versión (BOM de Boot). `verify` no volverá a estar
      en verde hasta T008: la seguridad por defecto de Boot bloquea todo

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: dejar Spring Security presente sin proteger todavía nada, con las pruebas de HU-02 en verde. Incluye la
regla de nombres (FR-018), porque el filtro de rutas actúa desde que la seguridad está en el classpath.

- [X] T002 [P] Ampliar `{domain-test}/model/PlaylistTest.java`: `data-model.md` `Playlist.name` rechaza cada
      `ForbiddenNameSequence` en mayúsculas, minúsculas y en medio de otro texto; acepta `%`, `%25`, `%2E`, `%2`, `2F`,
      `a;b` y `100%`
- [X] T003 [P] Actualizar las pruebas de extremo a extremo: en `{boot-test}/SpecialNames.java` quitar `%2F` y añadir
      `%`, `%25`, `%2E` y `%2`; en `{boot-test}/PlaylistApiCreateTest.java` añadir `400` para nombres con `%2F`, `%00` y
      U+2028 sin guardar nada; crear `{boot-test}/PathProtectionApiTest.java`: `GET /lists/%252F` y una ruta con `//`
      dan `400`
- [X] T004 Crear `{domain}/model/enums/ForbiddenNameSequence.java` y aplicarlo en `{domain}/model/Playlist.java` según
      `data-model.md` `Playlist.name`, con `InvalidPlaylistException`
- [X] T005 Crear `{infra}/in/rest/config/SecurityConfig.java` (`@EnableWebFluxSecurity`): cadena sin estado que de
      momento permite todo, sin CSRF, HTTP Basic, formulario, cierre de sesión ni caché de peticiones, con
      `NoOpServerSecurityContextRepository` (R4)
- [X] T006 Añadir a `{infra}/in/rest/config/SecurityConfig.java` el bean `StrictServerWebExchangeFirewall` que permite
      solo `;` y `%` (R1)
- [X] T007 Importar `SecurityConfig` en `{infra-test}/in/rest/ControllerTestSupport.java` para que `@WebFluxTest` use
      la configuración real y no la de Boot (R11)
- [X] T008 Ejecutar `./mvnw verify` (raíz, `pom.xml`): todo en verde, incluidas las pruebas de HU-02

**Checkpoint**: mismo comportamiento que HU-02, salvo los nombres de FR-018.

---

## Phase 3: User Story 1 - Iniciar sesión (Priority: P1) 🎯 MVP

**Goal**: `POST /auth/login` entrega un JWT a los dos usuarios de demostración.

**Independent Test**: respuestas de `POST /auth/login` de `contracts/rest-api.md` y arranque sin variables. Las listas
siguen abiertas hasta la fase 4.

### Tests for User Story 1

- [X] T009 [P] [US1] Crear `{domain-test}/model/CredentialsTest.java` y `{domain-test}/model/UserTest.java`:
      `data-model.md` `Credentials.username`, `Credentials.password`, `User.username`, `User.passwordHash` y
      `User.role`; valores sin recortar; `toString` sin contraseña ni resumen
- [X] T010 [P] [US1] Crear `{app-test}/usecase/LoginUseCaseTest.java`: éxito; contraseña mala; usuario inexistente
      compara contra el señuelo y da la misma excepción; 72 bytes se comparan y 73 (también con caracteres de varios
      bytes) dan `InvalidCredentialsException` sin comparar; propaga errores de los puertos
- [X] T011 [P] [US1] Crear `{app-test}/usecase/RegisterDemoUsersUseCaseTest.java`: guarda un usuario por cada
      `DemoUser` con el resumen, nunca la contraseña
- [X] T012 [P] [US1] Crear `{infra-test}/out/persistence/UserPersistenceAdapterTest.java` (`@DataR2dbcTest`): guardar y
      buscar; búsqueda exacta por mayúsculas; inexistente vacío; guardar dos veces el mismo `username` actualiza resumen
      y rol sin duplicar
- [X] T013 [P] [US1] Crear `{infra-test}/out/security/BCryptPasswordHasherAdapterTest.java`: el resumen es BCrypt,
      difiere de la contraseña, coincide con la correcta y no con otra
- [X] T014 [P] [US1] Crear `{infra-test}/security/SigningKeyTest.java` (nula, en blanco, 31 y 32 bytes UTF-8; el mensaje
      nombra `JWT_SECRET` y no contiene el valor) y `{infra-test}/out/security/JwtTokenIssuerAdapterTest.java` con
      `Clock` fijo: datos de la sección "Token" de `data-model.md`, HS256 y `expiresIn` igual a la vigencia
- [X] T015 [P] [US1] Crear `{infra-test}/in/rest/AuthControllerTest.java` (`@WebFluxTest` con `SecurityConfig`
      importada, `LoginPort` simulado): respuestas y códigos de `contracts/rest-api.md` (`200`, cada caso de `400`,
      `401`, `415`)
- [X] T016 [P] [US1] Crear `{boot-test}/AuthLoginApiTest.java` con servidor real: ambos usuarios reciben `200`, un
      token con su rol y `expiresIn` 1800 por defecto; usuario inexistente y contraseña mala dan cuerpos idénticos; 73 bytes da `401`; cabecera
      `Authorization: Bearer abc` no impide el `200`; sin `Set-Cookie`; los resúmenes guardados en `app_user` empiezan
      por `$2` y no contienen la contraseña
- [X] T017 [P] [US1] Crear `{boot-test}/StartupConfigurationTest.java`: el arranque falla, nombrando la variable y sin
      mostrar el valor, si falta o es corta `JWT_SECRET`, si una contraseña falta, está en blanco o tiene 73 bytes, o si
      `JWT_TTL` no es positiva. Usar una base H2 distinta de la compartida
- [X] T018 [P] [US1] Actualizar `{boot-test}/SchemaTest.java`: la lista de tablas incluye `APP_USER`

### Implementation for User Story 1

- [X] T019 [P] [US1] Crear `{domain}/model/enums/Role.java`, `{domain}/model/enums/DemoUser.java` y
      `{domain}/model/constants/PasswordConstraints.java` según `data-model.md`
- [X] T020 [US1] Crear `{domain}/exception/InvalidLoginRequestException.java` e `InvalidCredentialsException.java`, y
      añadir `INVALID_CREDENTIALS` a `{domain}/model/enums/ResponseCodeEnum.java` (`data-model.md`, "Errores")
- [X] T021 [US1] Crear como `record`, con `toString` seguro, `User`, `Credentials` y `AccessToken` en `{domain}/model/`:
      `data-model.md` `User.username`, `User.passwordHash`, `User.role`, `Credentials.username`,
      `Credentials.password`, `AccessToken.value` y `AccessToken.expiresInSeconds`
- [X] T022 [US1] Crear los puertos `{app}/port/in/LoginPort.java` y `RegisterDemoUsersPort.java`, y
      `{app}/port/out/UserRepositoryPort.java` (`findByUsername`, `save` que inserta o actualiza),
      `PasswordHasherPort.java` y `TokenIssuerPort.java`, todos con `Mono`
- [X] T023 [US1] Crear `{app}/usecase/LoginUseCase.java` según el flujo de `plan.md` y `data-model.md`
      `Credentials.password`, con el resumen señuelo calculado una vez (`cache`) (R7)
- [X] T024 [US1] Crear `{app}/usecase/RegisterDemoUsersUseCase.java`: resume y guarda cada `DemoUser`
- [X] T025 [US1] Añadir la tabla `app_user` a `infrastructure/src/main/resources/schema.sql` según `data-model.md`
      `User.username`, `User.passwordHash` y `User.role`
- [X] T026 [US1] Crear `UserEntity` (`@Table("APP_USER")`, `toString` seguro), `UserR2dbcRepository`,
      `UserEntityMapper` (MapStruct) y `UserPersistenceAdapter` en `{infra}/out/persistence/` y sus subpaquetes
- [X] T027 [P] [US1] Crear `{infra}/security/SigningKey.java` (mínimo 32 bytes UTF-8, R8) y
      `{infra}/security/TokenClaim.java` (enum con el nombre del dato `role`)
- [X] T028 [P] [US1] Crear `{infra}/out/security/BCryptPasswordHasherAdapter.java`, en `boundedElastic` (R7, R10)
- [X] T029 [US1] Crear `{infra}/security/config/JwtConfig.java`: beans `SigningKey`, `Clock` y `JwtEncoder`
      (`NimbusJwtEncoder`), con `playlist.security.jwt.secret` y `playlist.security.jwt.ttl` (30 min, positiva); leer los
      valores sin Bean Validation al enlazar (R8)
- [X] T030 [US1] Crear `{infra}/out/security/JwtTokenIssuerAdapter.java` (HS256, `sub`, `role`, `iat`, `exp`) (R2)
- [X] T031 [US1] Añadir a `infrastructure/src/test/resources/application.yml` una clave de firma falsa e importar
      `JwtConfig` donde las pruebas lo necesiten
- [X] T032 [US1] Crear `LoginRequestDto` (`toString` seguro) y `TokenResponseDto` en `{infra}/in/rest/dto/` y
      `{infra}/in/rest/mapper/AuthDtoMapper.java` según `contracts/rest-api.md`
- [X] T033 [US1] Crear `{infra}/in/rest/AuthController.java` con `POST /auth/login` (solo JSON; `tokenType` en una
      constante) y añadir el caso `INVALID_CREDENTIALS` → `401` a `{infra}/in/rest/handler/GlobalExceptionHandler.java`
- [X] T034 [US1] Anteponer en `{infra}/in/rest/config/SecurityConfig.java` la cadena de `POST /auth/login`, sin
      validación de token (R4)
- [X] T035 [US1] Añadir a `{boot}/config/UseCaseConfig.java` los beans de `LoginPort` y `RegisterDemoUsersPort`
- [X] T036 [US1] Añadir a `bootstrap/src/main/resources/application.yml` las propiedades `playlist.security.jwt.secret`
      (`${JWT_SECRET:}`), `...jwt.ttl` (`${JWT_TTL:30m}`), `playlist.security.demo-users.user-password`
      (`${DEMO_USER_PASSWORD:}`) y `...admin-password` (`${DEMO_ADMIN_PASSWORD:}`), y crear
      `bootstrap/src/test/resources/config/application.yml` con valores falsos
- [X] T037 [US1] Crear `{boot}/startup/DemoUsersInitializer.java` (`SmartInitializingSingleton`): valida cada contraseña
      (en blanco y `data-model.md` `Credentials.password`) nombrando la variable, y registra con `block()` (R9)
- [X] T038 [US1] Ejecutar `./mvnw verify` (raíz, `pom.xml`)

**Checkpoint**: los dos usuarios inician sesión.

---

## Phase 4: User Story 2 - Exigir un token válido (Priority: P1)

**Goal**: fuera del inicio de sesión, sin token válido y vigente no se accede a nada.

**Independent Test**: las cuatro operaciones sin token, con tokens inválidos y con uno vencido.

### Tests for User Story 2

- [X] T039 [P] [US2] Crear `{infra-test}/in/rest/security/TokenExpiryValidatorTest.java` con `Clock` fijo: acepta en
      `exp - 1 ms`; rechaza en `exp`, después y sin `exp` (R3)
- [X] T040 [P] [US2] Crear `{infra-test}/in/rest/SecurityErrorResponseTest.java` (`@WebFluxTest`): `401` y `400` de ruta
      rechazada con `application/problem+json`, `status`, `detail` y `code` de `contracts/rest-api.md`;
      `WWW-Authenticate` es exactamente `Bearer` (R6)
- [X] T041 [P] [US2] Crear `{boot-test}/TokenRequiredApiTest.java`: cada operación sin token da `401` y no cambia nada;
      token ilegible, alterado, firmado con otra clave, `alg: none`, vencido, sin `exp`, `Bearer` vacío y esquema
      `Basic` dan `401`; lista inexistente sin token, `401`; ruta inexistente, `401` sin token y `404` con él
- [X] T042 [US2] Adaptar las pruebas de HU-02 para autenticarse: en `{boot-test}/ApiTestSupport.java` iniciar sesión como
      `ADMIN` y enviar el token, también en el `WebTestClient` de `PlaylistApiCreateTest.java`, en
      `PlaylistPerformanceTest.java`, `PlaylistNonBlockingTest.java` y `PathProtectionApiTest.java`
- [X] T043 [US2] Adaptar `{infra-test}/in/rest/ControllerTestSupport.java`: enviar un token real de `ADMIN` firmado con
      la clave de prueba (R11)

### Implementation for User Story 2

- [X] T044 [US2] Añadir `AUTHENTICATION_REQUIRED` y `ACCESS_DENIED` a `{domain}/model/enums/ResponseCodeEnum.java`, y
      extraer a `{infra}/in/rest/handler/ProblemDetails.java` el estado de cada código y la construcción del
      `ProblemDetail`, usado por `GlobalExceptionHandler.java`
- [X] T045 [P] [US2] Crear `{infra}/in/rest/security/TokenExpiryValidator.java`: exige `exp` y rechaza si
      `ahora >= exp` (R3)
- [X] T046 [US2] Crear `{infra}/in/rest/security/SecurityErrorHandlers.java`: punto de entrada, acceso denegado y ruta
      rechazada, con `ProblemDetails`, sin decir por qué se rechazó el token (R6)
- [X] T047 [US2] En `{infra}/in/rest/config/SecurityConfig.java`: `NimbusReactiveJwtDecoder` HS256 con
      `TokenExpiryValidator`, resource server con los manejadores propios, `ServerExchangeRejectedHandler`, y
      `anyExchange().authenticated()` en la cadena principal
- [X] T048 [US2] Ejecutar `./mvnw verify` (raíz, `pom.xml`)

**Checkpoint**: la API está cerrada; todavía cualquier token válido puede todo.

---

## Phase 5: User Story 3 - Operar según el rol (Priority: P1)

**Goal**: `USER` consulta; `ADMIN` además crea y borra.

**Independent Test**: la matriz de rol por operación de `contracts/rest-api.md`.

### Tests for User Story 3

- [X] T049 [P] [US3] Crear `{boot-test}/RoleAccessApiTest.java`: matriz completa con `GET`, `HEAD`, `POST` y `DELETE`;
      `USER` recibe `403` con `AUTH_ERROR_003` y la lista conserva sus canciones; `403` antes que `404` y `400`; token
      sin rol o con rol desconocido, `403`; con `USER`, `/lists;x=1/{n}`, `/lists/{n};x=1`, `/%6Cists/{n}` y barra final
      no permiten borrar; `ADMIN` crea, consulta y borra `a;b` y `100%` siguiendo `Location`

### Implementation for User Story 3

- [X] T050 [US3] En `{infra}/in/rest/config/SecurityConfig.java`: convertir `TokenClaim` del rol en autoridad `ROLE_` y
      aplicar las reglas de R5 usando `Role` (`data-model.md` `User.role`)
- [X] T051 [US3] Ejecutar `./mvnw verify` (raíz, `pom.xml`)

**Checkpoint**: HU-03 funciona completa.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T052 [P] Ampliar `{boot-test}/PlaylistNonBlockingTest.java` con inicio de sesión (correcto y fallido), `401` y
      `403`, calentando antes de instalar BlockHound (R10, FR-019). No añadir llamadas a la lista blanca sin avisar
- [X] T053 [P] Añadir a `{boot-test}/PlaylistPerformanceTest.java` el inicio de sesión en menos de 1 segundo (SC-005)
- [X] T054 [P] Actualizar `README.md`: quitar el aviso de que no hay autenticación; variables, inicio de sesión, roles y
      enlace al contrato
- [X] T055 [P] Anotar en las secciones 8 y 9 de `docs/adr/0001-arquitectura-api-listas-reproduccion.md` los nombres que
      dejan de aceptarse y las dos reglas relajadas del filtro
- [X] T056 [P] Actualizar `specs/02-playlist-management/contracts/rest-api.md` y
      `specs/02-playlist-management/requests.http`: ahora exigen token (login y `Authorization`), sin nombres que
      rechace FR-018; conservar las peticiones añadidas a mano
- [X] T057 [P] Generar `specs/03-authentication-authorization/requests.http` según la regla de peticiones HTTP del
      equipo, con un caso por requisito y escenario de `spec.md`; contraseñas como variables sin valor real
- [X] T058 Ejecutar el recorrido manual de `specs/03-authentication-authorization/quickstart.md`
- [X] T059 Actualizar el estado de cada decisión de `specs/03-authentication-authorization/research.md` con el
      resultado real
- [X] T060 Comprobar que cada criterio de HU-03 tiene una prueba (SC-002) y marcarlo en `docs/backlog.md`
- [X] T061 Ejecutar `./mvnw clean verify` y revisar `git status` y el diff completo desde la raíz (`.gitignore`): sin
      archivos temporales ni secretos
- [ ] T062 Con la aprobación del usuario: commits atómicos `…(HU-03): …` en `feature/03-authentication-authorization`,
      push y PR hacia `main` (artefactos de `specs/03-authentication-authorization/` incluidos)

---

## Dependencies & Execution Order

- Setup (T001) → Foundational (T002 a T008) → US1 → US2 → US3 → Polish. T001 y la fase 2 se completan juntas.
- Las historias van en orden: US2 valida los tokens que emite US1 y US3 decide sobre peticiones que US2 ya autentica.
  Las tres editan `SecurityConfig.java`.
- Dentro de US1: T019 → T020 → T021 → T022 → T023 y T024; T027 antes de T029 y T030; T035 a T037 al final.
- En US2, T042 y T043 dejan las pruebas de HU-02 listas antes de cerrar la API en T047.
- T062 es la única tarea que publica algo.

## Parallel Opportunities

- Foundational: T002 y T003
- US1: T009 a T018; en la implementación, T027 y T028
- US2: T039, T040 y T041; T045
- US3: una sola tarea de pruebas y una de implementación
- Polish: T052 a T057

## Parallel Example: User Story 1

```text
T009  {domain-test}/model/CredentialsTest.java y UserTest.java
T010  {app-test}/usecase/LoginUseCaseTest.java
T012  {infra-test}/out/persistence/UserPersistenceAdapterTest.java
T016  {boot-test}/AuthLoginApiTest.java
```

## Implementation Strategy

**MVP**: Setup, Foundational y US1, para comprobar que se emiten tokens. **No es entregable**: las listas siguen
abiertas. **Entregable**: las tres historias, porque todas son P1 y una HU a medias no se entrega. En cada checkpoint se
valida antes de seguir.

Las pruebas de una fase compilan solo cuando existen sus tipos: "fallan" significa primero error de compilación y, con
las firmas creadas, fallo de la prueba. La base en memoria se comparte dentro de un módulo.
