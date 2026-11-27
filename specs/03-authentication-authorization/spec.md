# Feature Specification: Autenticación y autorización

**Feature Branch**: `feature/03-authentication-authorization`

**Created**: 2026-11-26

**Status**: Draft

**Input**: User description: "HU-03: como usuario, quiero autenticarme y operar según mi rol, para usar el sistema con los datos protegidos." Fuentes: `docs/backlog.md` (HU-03), `docs/adr/0001-arquitectura-api-listas-reproduccion.md` (sección 9) y `specs/02-playlist-management/research.md` (riesgo para HU-03).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Iniciar sesión (Priority: P1)

Como usuario, inicio sesión con mi nombre de usuario y contraseña y recibo un token temporal, para identificarme en las siguientes peticiones.

**Why this priority**: Sin token no se puede usar ninguna operación protegida.

**Independent Test**: Iniciar sesión con credenciales válidas e inválidas y revisar las respuestas.

**Acceptance Scenarios**:

1. **Given** el usuario de demostración con rol `USER`, **When** inicia sesión con su contraseña correcta, **Then** recibe `200` con un token JWT, su tipo (`Bearer`) y los segundos que faltan para que venza.
2. **Given** el usuario de demostración con rol `ADMIN`, **When** inicia sesión con su contraseña correcta, **Then** recibe `200` con un token que lo identifica con ese rol.
3. **Given** un usuario existente, **When** inicia sesión con una contraseña incorrecta, **Then** recibe `401` y ningún token.
4. **Given** un nombre de usuario que no existe, **When** intenta iniciar sesión, **Then** recibe `401` con la misma respuesta que ante una contraseña incorrecta.
5. **Given** una petición sin `username` o sin `password`, **When** se envía al inicio de sesión, **Then** recibe `400`.

---

### User Story 2 - Exigir un token válido (Priority: P1)

Como responsable del sistema, quiero que las listas solo sean accesibles con un token válido y vigente, para que los datos estén protegidos.

**Why this priority**: Es la protección de los datos; hoy cualquiera puede crear y borrar listas.

**Independent Test**: Llamar a cada operación sobre listas sin token, con un token alterado y con uno vencido.

**Acceptance Scenarios**:

1. **Given** una petición sin token, **When** se llama a cualquiera de las cuatro operaciones sobre listas, **Then** responde `401` y no se lee ni se modifica nada.
2. **Given** un token mal formado, alterado o firmado con otra clave, **When** se usa en cualquier operación, **Then** responde `401`.
3. **Given** un token vencido, **When** se usa en cualquier operación, **Then** responde `401`.
4. **Given** una petición sin token para una lista que no existe, **When** se consulta o se borra, **Then** responde `401`, no `404`.

---

### User Story 3 - Operar según el rol (Priority: P1)

Como usuario consulto las listas, y como administrador además las creo y las borro, para que solo quien tiene permiso modifique los datos.

**Why this priority**: Completa el requisito de autorización del enunciado.

**Independent Test**: Ejecutar las cuatro operaciones con un token de cada rol.

**Acceptance Scenarios**:

1. **Given** un token de `USER`, **When** consulta todas las listas o una lista, **Then** recibe la misma respuesta definida en HU-02 (`200` o `404`).
2. **Given** un token de `USER`, **When** intenta crear una lista válida, **Then** recibe `403` y la lista no se crea.
3. **Given** un token de `USER` y la lista "Rock", **When** intenta borrarla, **Then** recibe `403` y la lista conserva sus canciones.
4. **Given** un token de `ADMIN`, **When** crea, consulta y borra una lista, **Then** recibe `201`, `200` y `204`.
5. **Given** un token de `USER`, **When** intenta borrar una lista que no existe o crear una con datos inválidos, **Then** recibe `403`, no `404` ni `400`.
6. **Given** una lista llamada `a;b` o `100%`, creada por un `ADMIN`, **When** se consulta y se borra con la dirección devuelta al crearla, **Then** recibe `200` y `204`.
7. **Given** un token de `ADMIN`, **When** intenta crear una lista cuyo nombre contiene `%2F` o un separador U+2028, **Then** recibe `400` y no se guarda nada.

---

### Edge Cases

- **Otro esquema de autorización**: una cabecera `Authorization` con un esquema distinto de `Bearer`, o con `Bearer` sin token, responde `401`.
- **Token sin firma**: un token que declara no estar firmado responde `401`.
- **Vencimiento**: un token se rechaza desde el instante en que vence, sin margen de tolerancia.
- **Token sin rol reconocido**: un token válido que no trae un rol conocido responde `403`.
- **Credenciales en blanco o mal enviadas**: `username` o `password` ausentes, nulos, vacíos, solo con espacios o con un tipo de dato incorrecto, y un cuerpo ausente o mal formado, responden `400`.
- **Mayúsculas**: el nombre de usuario y la contraseña se comparan exactamente.
- **Contraseña muy larga**: una contraseña de más de 72 bytes, que supera lo que el algoritmo de resumen procesa, responde `401`; nunca se acepta recortada ni produce un error del servidor.
- **Ruta inexistente**: sin token responde `401`; con un token válido, `404`.
- **Inicio de sesión con un token previo**: un token vencido o inválido en la cabecera no impide iniciar sesión.
- **Configuración incompleta**: sin la clave de firma o sin alguna contraseña de demostración, la aplicación no arranca.
- **Dirección rechazada por la protección de rutas**: responde `400` antes de comprobar el token y sin consultar los datos.
- **Nombres parecidos a los rechazados**: `%`, `%25`, `%2E`, `2F` o `%2` siguen siendo válidos.

## Requirements *(mandatory)*

### Functional Requirements

**Inicio de sesión (`POST /auth/login`)**

- **FR-001**: Con `username` y `password` válidos debe responder `200` con `accessToken` (JWT), `tokenType` (`Bearer`) y `expiresIn` (segundos de vigencia).
- **FR-002**: El token debe identificar al usuario y su rol, tener fecha de vencimiento e ir firmado, de modo que cualquier cambio en su contenido lo invalide.
- **FR-003**: La vigencia del token es de 30 minutos por defecto y se puede configurar.
- **FR-004**: Las credenciales inválidas responden `401`, con una respuesta idéntica para un usuario inexistente y para una contraseña incorrecta.
- **FR-005**: Una petición de inicio de sesión incompleta o mal formada responde `400`.

**Usuarios y secretos**

- **FR-006**: Al arrancar deben existir dos usuarios de demostración: uno con rol `USER` y otro con rol `ADMIN`.
- **FR-007**: Las contraseñas se guardan únicamente como resumen BCrypt. Ni la contraseña ni su resumen aparecen en respuestas o registros.
- **FR-008**: La clave de firma se toma de una variable de entorno, sin valor por defecto en el repositorio. Si falta o tiene menos de 256 bits, la aplicación no arranca y explica el motivo sin mostrar la clave.
- **FR-009**: La contraseña de cada usuario de demostración se toma de su propia variable de entorno, sin valor por defecto en el repositorio. Si falta, está en blanco o supera los 72 bytes, la aplicación no arranca y explica el motivo sin mostrar su valor.

**Autorización**

- **FR-010**: Todas las rutas exigen un token válido y vigente en la cabecera `Authorization: Bearer`, excepto `POST /auth/login`.
- **FR-011**: Sin token, o con un token inválido o vencido, la respuesta es `401` con la cabecera `WWW-Authenticate: Bearer`.
- **FR-012**: El rol `USER` puede consultar (`GET /lists` y `GET /lists/{listName}`) y recibe `403` al crear o borrar.
- **FR-013**: El rol `ADMIN` puede consultar, crear y borrar.
- **FR-014**: La autenticación y el rol se comprueban antes de leer o validar los datos: una petición rechazada con `401` o `403` no revela si una lista existe ni modifica nada.
- **FR-015**: Cada petición se autentica por sí sola con su token: no hay sesión en el servidor ni cookies.

**Compatibilidad y restricciones transversales**

- **FR-016**: Las respuestas `400`, `401` y `403` usan el mismo formato `ProblemDetail` que el resto de errores (HU-02, FR-002), sin indicar por qué se rechazó el token.
- **FR-017**: Para una petición autorizada, las cuatro operaciones sobre listas se comportan como en HU-02, salvo por los nombres que rechaza FR-018.
- **FR-018**: Un nombre de lista que contiene, sin distinguir mayúsculas, el texto `%2F`, `%5C`, `%00`, `%0A` o `%0D`, o los separadores Unicode U+2028 o U+2029, pasa a rechazarse con `400`. Los demás nombres que HU-02 acepta, incluidos los que contienen `;` o `%`, se siguen pudiendo crear, consultar y borrar (HU-02, FR-009). La protección de las rutas solo se relaja para `;` y `%`.
- **FR-019**: El inicio de sesión y la comprobación del token deben atenderse sin bloquear el hilo que recibe las peticiones.

### Key Entities *(include if feature involves data)*

- **Usuario**: nombre de usuario único, resumen de su contraseña y un rol.
- **Rol**: `USER` (consultar) o `ADMIN` (consultar, crear y borrar).
- **Token de acceso**: identifica a un usuario y su rol durante un tiempo limitado. No se guarda en el servidor.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con cada usuario de demostración se puede iniciar sesión y ejecutar las operaciones que su rol permite.
- **SC-002**: Todos los criterios de aceptación de HU-03 en el backlog tienen al menos una prueba automática que los comprueba.
- **SC-003**: El 100 % de las peticiones a listas sin un token válido, o sin el rol necesario, se rechazan y producen 0 cambios en los datos.
- **SC-004**: El repositorio contiene 0 claves de firma o contraseñas reales y los datos guardados contienen 0 contraseñas en claro.
- **SC-005**: El inicio de sesión responde en menos de 1 segundo en el equipo de desarrollo.
- **SC-006**: Ninguna petición rechazada por datos del cliente produce un error del servidor.
- **SC-007**: La verificación del proyecto pasa, incluidas las pruebas de HU-02, con al menos 80 % de cobertura por módulo y sin violaciones de estilo.

## Assumptions

- **Decisiones del ADR-0001**: JWT con expiración corta, usuarios de demostración en la base en memoria, BCrypt, roles `USER` y `ADMIN` y clave de firma por variable de entorno ya están acordados.
- **Contrato del inicio de sesión**: ni el enunciado ni el ADR definen los campos; se adoptan los nombres habituales (`username`, `password`, `accessToken`, `tokenType`, `expiresIn`).
- **Vigencia**: "expiración corta" se concreta en 30 minutos (FR-003).
- **Un rol por usuario**.
- **Arranque**: para ejecutar la aplicación hay que definir la clave de firma y las dos contraseñas; el `README.md` lo documenta. HU-07 las pasará a Docker Compose.
- **Cambio en el contrato de HU-02**: FR-018 rechaza nombres que HU-02 aceptaba. Se decidió así el 2026-11-26, al comprobar que aceptarlos exigía desactivar diez reglas de la protección de rutas en lugar de dos (research R1). No hay datos previos que migrar, porque la base está en memoria.
- **Cambio incompatible**: las operaciones que HU-02 dejó abiertas pasan a exigir token. Sus pruebas, casos HTTP y el aviso del `README.md` se actualizan en esta historia.
- **Origen del navegador**: no se configura CORS, porque la web llamará a la API por el mismo origen (ADR-0001, sección 12).
- **Fuera de alcance**: registro y gestión de usuarios, cambio o recuperación de contraseña, renovación y revocación de tokens, cierre de sesión en el servidor y limitación de intentos de inicio de sesión. Tampoco la aplicación web (HU-05), OpenAPI y Postman (HU-06) ni las rutas de salud sin token (HU-09), que cada historia abrirá cuando las añada.
- **Riesgo aceptado**: sin limitación de intentos, el inicio de sesión admite ataques de fuerza bruta.
- **Dependencia**: las operaciones sobre listas de HU-02.
