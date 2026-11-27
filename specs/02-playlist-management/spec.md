# Feature Specification: Gestión de listas de reproducción

**Feature Branch**: `feature/02-playlist-management`

**Created**: 2026-11-26

**Status**: Draft

**Input**: User description: "HU-02: como usuario, quiero consultar las listas, y como administrador crearlas y borrarlas, para organizar la música." Fuentes: `docs/backlog.md` (HU-02) y `docs/adr/0001-arquitectura-api-listas-reproduccion.md` (requisitos del enunciado y sección 8).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Crear una lista (Priority: P1)

Como administrador, creo una lista con su descripción y sus canciones en una sola operación, para organizar la música.

**Why this priority**: Sin listas creadas no hay nada que consultar ni que borrar.

**Independent Test**: Crear una lista y comprobar la respuesta, la dirección devuelta y los casos rechazados.

**Acceptance Scenarios**:

1. **Given** que no existe una lista llamada "Rock", **When** creo una lista válida con ese nombre, descripción y dos canciones, **Then** recibo `201`, la cabecera `Location` con la dirección de la lista y la lista creada en el cuerpo.
2. **Given** una petición sin `nombre`, con `nombre` nulo, vacío o formado solo por espacios, **When** intento crearla, **Then** recibo `400` con el detalle del error y no se guarda nada.
3. **Given** que ya existe la lista "Rock", **When** intento crear otra con el mismo nombre, **Then** recibo `409` y la lista original no cambia.
4. **Given** una lista válida cuya segunda canción no se puede guardar, **When** intento crearla, **Then** la operación falla y no quedan guardadas ni la lista ni ninguna canción.
5. **Given** un nombre con espacios como "Mis favoritas", **When** creo la lista, **Then** recibo `201`, `Location` lleva el nombre codificado (`/lists/Mis%20favoritas`) y al seguirla obtengo esa lista.
6. **Given** una petición sin canciones, **When** creo la lista, **Then** recibo `201` y la lista tiene un arreglo de canciones vacío.

---

### User Story 2 - Consultar todas las listas (Priority: P1)

Como usuario, consulto todas las listas con sus canciones, para saber qué música hay disponible.

**Why this priority**: Es la entrada para descubrir las listas y la base de la futura aplicación web.

**Independent Test**: Consultar sin listas y después de crear algunas.

**Acceptance Scenarios**:

1. **Given** dos listas con canciones, **When** consulto todas las listas, **Then** recibo `200` con ambas, cada una con su nombre, descripción y sus propias canciones.
2. **Given** que no hay listas, **When** consulto todas las listas, **Then** recibo `200` con un arreglo vacío.
3. **Given** una lista sin canciones, **When** consulto todas las listas, **Then** aparece con un arreglo de canciones vacío.
4. **Given** que creé "Rock" y después "Jazz", cada una con varias canciones, **When** consulto todas las listas, **Then** "Rock" aparece antes que "Jazz" y las canciones conservan el orden en que se enviaron.

---

### User Story 3 - Consultar una lista (Priority: P1)

Como usuario, consulto una lista por su nombre, para ver su descripción y sus canciones.

**Why this priority**: Es un requisito del enunciado y el destino de la dirección que devuelve la creación.

**Independent Test**: Consultar una lista existente y un nombre que no existe.

**Acceptance Scenarios**:

1. **Given** que existe la lista "Rock", **When** la consulto por su nombre, **Then** recibo `200` con `nombre`, `descripcion` y `canciones`.
2. **Given** que no existe la lista "Jazz", **When** la consulto, **Then** recibo `404` con el detalle del error.

---

### User Story 4 - Borrar una lista (Priority: P1)

Como administrador, borro una lista por su nombre, para retirar la que ya no sirve.

**Why this priority**: Completa las cuatro operaciones que exige el enunciado.

**Independent Test**: Borrar una lista, consultarla después y volver a borrarla.

**Acceptance Scenarios**:

1. **Given** que existe la lista "Rock", **When** la borro, **Then** recibo `204` sin cuerpo, y al consultarla recibo `404`.
2. **Given** que no existe la lista "Jazz", **When** intento borrarla, **Then** recibo `404`.
3. **Given** una lista con canciones, **When** la borro, **Then** la lista y sus canciones se eliminan juntas, y las demás listas conservan las suyas.
4. **Given** que el borrado falla a medias, **When** consulto la lista, **Then** conserva todas sus canciones.
5. **Given** que borré la lista "Rock", **When** creo otra con el mismo nombre, **Then** recibo `201` y no contiene canciones de la anterior.

---

### Edge Cases

- **Cuerpo ilegible o ausente**: una petición de creación sin cuerpo, con JSON mal formado o con un tipo de dato incorrecto (por ejemplo, `canciones` que no es un arreglo) devuelve `400`.
- **Canción nula**: un elemento nulo dentro de `canciones` devuelve `400`.
- **Longitud máxima**: un valor con la longitud máxima exacta se acepta; con un carácter más devuelve `400`.
- **Mayúsculas**: "Rock" y "rock" son listas distintas.
- **Espacios alrededor**: " Rock " no es "Rock". El nombre se conserva tal como se envía.
- **Caracteres especiales**: los nombres con tildes, `ñ`, `&`, `?`, `#` o `+` se aceptan y se pueden consultar y borrar con su dirección codificada.
- **Caracteres no direccionables**: un nombre con `/`, `\` o caracteres de control, o igual a `.` o `..`, devuelve `400`.
- **Creación simultánea**: si dos peticiones crean el mismo nombre a la vez, una recibe `201` y la otra `409`, nunca un error del servidor.
- **Canciones repetidas**: una lista puede contener canciones iguales; se guardan todas.

## Requirements *(mandatory)*

### Functional Requirements

**Contrato**

- **FR-001**: Una lista se representa con `nombre`, `descripcion` y `canciones`. Cada canción, con `titulo`, `artista`, `album`, `anno` y `genero`. No se exponen identificadores internos.
- **FR-002**: Los errores deben responder en formato `ProblemDetail` (RFC 9457), con el código de estado y un detalle comprensible, sin trazas ni datos internos.

**Crear (`POST /lists`)**

- **FR-003**: Una petición válida debe guardar la lista con sus canciones y responder `201`, con la cabecera `Location` apuntando a `/lists/{nombre}` y la lista creada en el cuerpo.
- **FR-004**: `nombre` es obligatorio. Si falta, es nulo, está vacío o solo tiene espacios, la respuesta es `400` y no se guarda nada.
- **FR-005**: `nombre` es único, distinguiendo mayúsculas y minúsculas. Un nombre repetido responde `409` y no modifica la lista existente, también ante peticiones simultáneas.
- **FR-006**: `descripcion`, `canciones` y los campos de cada canción son opcionales. `canciones` ausente o nulo equivale a un arreglo vacío. `anno` es texto libre.
- **FR-007**: Las longitudes máximas son: `nombre` 255, `descripcion` 1000, `titulo`, `artista` y `album` 255, `anno` 10 y `genero` 100 caracteres. Superarlas responde `400`.
- **FR-008**: La lista y sus canciones se guardan juntas: si falla cualquiera, no queda nada guardado.
- **FR-009**: Se aceptan nombres con espacios. `Location` debe ir codificada para URL, y todo nombre aceptado debe poder consultarse y borrarse con esa dirección.
- **FR-010**: Un nombre con `/`, `\` o caracteres de control, o igual a `.` o `..`, responde `400`.
- **FR-011**: Un cuerpo ausente, mal formado, con tipos incorrectos o con una canción nula responde `400`.

**Consultar (`GET /lists` y `GET /lists/{listName}`)**

- **FR-012**: `GET /lists` responde `200` con todas las listas y sus canciones, o con un arreglo vacío si no hay ninguna.
- **FR-013**: Las listas se devuelven en orden de creación, y las canciones en el orden en que se enviaron.
- **FR-014**: `GET /lists/{listName}` responde `200` con la lista cuyo nombre coincide exactamente, o `404` si no existe.

**Borrar (`DELETE /lists/{listName}`)**

- **FR-015**: Debe eliminar la lista y responder `204` sin cuerpo, o `404` si no existe.
- **FR-016**: La lista y sus canciones se borran en la misma transacción: se borra todo o no se borra nada.

**Restricciones transversales**

- **FR-017**: Las cuatro operaciones deben atenderse sin bloquear el hilo que recibe las peticiones.

### Key Entities *(include if feature involves data)*

- **Lista**: tiene un nombre, que la identifica, una descripción y cero o más canciones.
- **Canción**: título, artista, álbum, año y género. Pertenece a una sola lista y no existe sin ella.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Las cuatro operaciones del enunciado funcionan de principio a fin: una lista se crea, aparece en el listado, se consulta y se borra.
- **SC-002**: Todos los criterios de aceptación de HU-02 en el backlog tienen al menos una prueba automática que los comprueba.
- **SC-003**: Tras cualquier creación o borrado fallido quedan 0 datos parciales.
- **SC-004**: El 100 % de los nombres aceptados se pueden consultar y borrar con la dirección devuelta al crearlos.
- **SC-005**: Ninguna petición rechazada por datos del cliente produce un error del servidor.
- **SC-006**: Con 100 listas de 20 canciones, cada operación responde en menos de 1 segundo en el equipo de desarrollo.
- **SC-007**: La verificación del proyecto pasa, con al menos 80 % de cobertura por módulo y sin violaciones de estilo.

## Assumptions

- **Autenticación y roles**: llegan con HU-03. En esta historia las cuatro operaciones quedan abiertas; la distinción entre usuario y administrador solo describe quién las usará.
- **Campos opcionales**: el enunciado no dice qué campos de canción son obligatorios; se asume que ninguno (FR-006).
- **Longitudes**: el enunciado no las define; se adoptan como definitivas las provisionales del esquema de HU-01 (FR-007).
- **Nombres**: la unicidad exacta, la conservación de los espacios y el rechazo de caracteres no direccionables (FR-005, FR-010) no vienen del enunciado.
- **Decisiones del ADR-0001**: el `409` por nombre duplicado, los nombres JSON en español, `anno` como texto y `ProblemDetail` ya están acordados.
- **Datos en memoria**: las listas se pierden al reiniciar la aplicación.
- **Fuera de alcance**: modificar una lista o sus canciones, paginación, búsqueda y filtros, validar `genero` contra el catálogo (HU-04), aplicación web (HU-05), OpenAPI y Postman (HU-06), caché (HU-10) y eventos (HU-11).
- **Dependencia**: el esqueleto de HU-01, ya fusionado en `main`.
