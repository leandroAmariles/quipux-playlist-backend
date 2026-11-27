# Feature Specification: Géneros

**Feature Branch**: `feature/04-genres`

**Created**: 2026-11-26

**Status**: Draft

**Input**: User description: "HU-04: como usuario, quiero obtener los géneros disponibles, para clasificar canciones." Fuentes: `docs/backlog.md` (HU-04), `docs/adr/0001-arquitectura-api-listas-reproduccion.md` (secciones 10 y 11) y `specs/02-playlist-management/research.md` (nota sobre los campos desconocidos).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Verificar qué ofrece Spotify (Priority: P1)

Como desarrollador, compruebo con las credenciales reales qué consultas de géneros permite Spotify, para decidir si la integración usa el servicio real o uno simulado.

**Why this priority**: Es el primer paso de la historia y el mayor riesgo: no está confirmado que Spotify siga entregando géneros a una aplicación nueva.

**Independent Test**: Revisar el ADR y comprobar que anota el resultado de cada consulta probada y la decisión tomada.

**Acceptance Scenarios**:

1. **Given** las credenciales de la aplicación de Spotify en variables de entorno, **When** se pide un token con *client credentials*, **Then** se obtiene un token o queda registrado el error recibido.
2. **Given** un token obtenido, **When** se prueban `/recommendations/available-genre-seeds` y `/browse/categories`, **Then** el ADR anota la fecha, la respuesta de cada una y si sirve como catálogo de géneros.
3. **Given** que la integración real falla o resulta complicada, **When** se cierra la verificación, **Then** el ADR anota que Spotify se simula con WireMock.

---

### User Story 2 - Consultar los géneros (Priority: P1)

Como usuario, consulto los géneros disponibles y de dónde vienen, para clasificar las canciones.

**Why this priority**: Es el valor de la historia y el requisito del enunciado de consumir el servicio de géneros de Spotify. La web (HU-05) lo necesita para el selector de género.

**Independent Test**: Consultar los géneros con Spotify simulado y revisar la lista y el origen.

**Acceptance Scenarios**:

1. **Given** un token de `USER` y Spotify disponible, **When** consulta `GET /genres`, **Then** recibe `200` con los géneros de Spotify y el origen `SPOTIFY`.
2. **Given** un token de `ADMIN` y Spotify disponible, **When** consulta `GET /genres`, **Then** recibe la misma respuesta.
3. **Given** una petición sin token, o con un token inválido o vencido, **When** consulta `GET /genres`, **Then** recibe `401` y no se llama a Spotify.

---

### User Story 3 - Seguir respondiendo cuando Spotify falla (Priority: P1)

Como usuario, recibo una lista local de géneros cuando Spotify no está disponible, para poder seguir clasificando canciones.

**Why this priority**: Un servicio externo no puede dejar inutilizable la aplicación, y la disponibilidad de Spotify no está garantizada.

**Independent Test**: Simular cada fallo de Spotify y comprobar que la respuesta trae la lista local.

**Acceptance Scenarios**:

1. **Given** que Spotify rechaza las credenciales al pedir el token, **When** se consulta `GET /genres`, **Then** recibe `200` con la lista local y el origen `LOCAL`.
2. **Given** que la consulta de géneros de Spotify responde con un error (`4xx`, incluido `429`, o `5xx`), **When** se consulta `GET /genres`, **Then** recibe `200` con la lista local y el origen `LOCAL`.
3. **Given** que Spotify no responde dentro del tiempo máximo o no se puede establecer la conexión, **When** se consulta `GET /genres`, **Then** recibe `200` con la lista local y el origen `LOCAL`.
4. **Given** que Spotify responde con un contenido ilegible o sin ningún género, **When** se consulta `GET /genres`, **Then** recibe `200` con la lista local y el origen `LOCAL`.
5. **Given** que no hay credenciales de Spotify configuradas, **When** arranca la aplicación y se consulta `GET /genres`, **Then** arranca con normalidad y responde con la lista local sin llamar a Spotify.
6. **Given** una consulta anterior respondida con la lista local, **When** Spotify vuelve a estar disponible y se consulta de nuevo, **Then** recibe los géneros de Spotify.

---

### User Story 4 - Aceptar solo géneros del catálogo (Priority: P2)

Como administrador, al crear una lista se rechazan los géneros que no están en el catálogo, para que las canciones queden clasificadas de forma consistente.

**Why this priority**: Depende de que la consulta y el respaldo ya funcionen, y cambia una operación que existe desde HU-02.

**Independent Test**: Crear listas con géneros dentro y fuera del catálogo, con Spotify disponible y caído.

**Acceptance Scenarios**:

1. **Given** un token de `ADMIN` y un catálogo que contiene `rock`, **When** crea una lista con una canción de género `rock`, **Then** recibe `201`.
2. **Given** un catálogo que contiene `rock`, **When** crea una lista con género `Rock`, **Then** recibe `201` y el género se guarda como `Rock`.
3. **Given** un catálogo que no contiene `no-existe`, **When** crea una lista con dos canciones y una de ellas tiene ese género, **Then** recibe `400` y no se guarda nada.
4. **Given** que Spotify no está disponible, **When** crea una lista con un género de la lista local, **Then** recibe `201`.
5. **Given** que Spotify no está disponible, **When** crea una lista con un género que no está en la lista local, **Then** recibe `400` y no se guarda nada.
6. **Given** canciones sin género (ausente, nulo, vacío o solo con espacios), **When** crea la lista, **Then** recibe `201` sin consultar el catálogo.
7. **Given** una petición sin token o con rol `USER` y un género fuera del catálogo, **When** intenta crear la lista, **Then** recibe `401` o `403`, no `400`, y no se llama a Spotify.

---

### Edge Cases

- **Nombre repetido y género no válido**: responde `400`; el `409` solo se evalúa con datos válidos.
- **Petición rechazada por otra regla**: si ya incumple una regla de HU-02 (por ejemplo, un género de más de 100 caracteres), responde `400` sin consultar el catálogo.
- **Espacios alrededor del género**: `" rock "` no coincide con `rock` y responde `400`.
- **Cambio de origen entre consultar y crear**: un género de Spotify que no esté en la lista local se rechaza si Spotify falla al crear.
- **Listas anteriores**: consultar o borrar una lista no valida sus géneros ni consulta el catálogo.
- **Credenciales incompletas**: con solo el identificador o solo el secreto, o con alguno en blanco, se comporta como si no hubiera credenciales.
- **Entradas no válidas de Spotify**: los géneros vacíos, repetidos o de más de 100 caracteres (el máximo de una canción, HU-02) se descartan. Si no queda ninguno, se usa la lista local.
- **Campos adicionales**: los campos de la respuesta de Spotify que la aplicación no usa se ignoran y no provocan el uso de la lista local.
- **Listas mezcladas**: una respuesta nunca combina géneros de Spotify con géneros locales.
- **Token de Spotify rechazado en la consulta**: se responde con la lista local, sin devolver un error al usuario.
- **Dos tokens distintos**: el token del usuario nunca se envía a Spotify, y el token de Spotify nunca se devuelve al usuario.
- **Lista local vacía**: si la lista local no está configurada o no tiene géneros, la aplicación no arranca y explica el motivo.

## Requirements *(mandatory)*

### Functional Requirements

**Verificación de Spotify**

- **FR-001**: Antes de implementar la consulta se debe comprobar, con las credenciales reales, la obtención del token con *client credentials* y las consultas `/recommendations/available-genre-seeds` y `/browse/categories`.
- **FR-002**: El ADR-0001 debe registrar el resultado de la verificación, la decisión entre Spotify real o simulado y la validación del género (FR-017), sin incluir credenciales ni tokens.

**Consulta de géneros (`GET /genres`)**

- **FR-003**: Debe responder `200` con `generos` (arreglo de textos, nunca vacío) y `origen` (`SPOTIFY` o `LOCAL`).
- **FR-004**: Con Spotify disponible, `generos` contiene los géneros que Spotify entrega, en su orden, y `origen` es `SPOTIFY`.
- **FR-005**: Los roles `USER` y `ADMIN` pueden consultar. Sin token válido responde `401`, igual que el resto de operaciones (HU-03).
- **FR-006**: Si no se pueden obtener géneros de Spotify (token, error, tiempo agotado, conexión, contenido ilegible o sin géneros), responde `200` con la lista local y `origen` `LOCAL`.
- **FR-007**: La obtención desde Spotify, token incluido, tiene un tiempo máximo total de 3 segundos por defecto, configurable. Al superarlo se usa la lista local.
- **FR-008**: El fallo de una consulta no se recuerda: la siguiente vuelve a intentar con Spotify.
- **FR-009**: Cada uso de la lista local debe quedar en el registro de la aplicación con su motivo, sin credenciales ni tokens.
- **FR-010**: La lista local es un conjunto fijo de géneros, sin vacíos ni repetidos, que se puede cambiar sin modificar el código.

**Configuración y secretos**

- **FR-011**: Las credenciales de Spotify se toman únicamente de variables de entorno (`SPOTIFY_CLIENT_ID` y `SPOTIFY_CLIENT_SECRET`), sin valor por defecto en el repositorio.
- **FR-012**: Sin credenciales completas la aplicación arranca, avisa en el registro y responde siempre con la lista local.
- **FR-013**: La dirección base para pedir el token y la dirección base de la consulta de géneros son configurables, con las oficiales de Spotify como valor por defecto, para poder apuntar a un servicio simulado.

**Pruebas y restricciones transversales**

- **FR-014**: Las pruebas automáticas usan WireMock como sustituto de Spotify: no llaman al servicio real ni necesitan credenciales reales ni acceso a internet.
- **FR-015**: La consulta de géneros se atiende sin bloquear el hilo que recibe las peticiones.
- **FR-016**: Consultar y borrar listas no cambian y no dependen de Spotify. Crear listas solo cambia según FR-017 a FR-022 y sigue funcionando aunque Spotify no esté disponible.

**Validación del género al crear (`POST /lists`)**

- **FR-017**: Si alguna canción tiene un `genero` que no está en el catálogo, responde `400` con `ProblemDetail`, indicando que el problema es el género, y no guarda nada.
- **FR-018**: El catálogo usado para validar es el mismo que entregaría `GET /genres` en ese momento: el de Spotify o, si no se puede obtener, la lista local (FR-006, FR-007). Un fallo de Spotify nunca produce un error del servidor al crear.
- **FR-019**: La comparación no distingue mayúsculas de minúsculas y, en lo demás, es exacta. El género se guarda tal como se envió.
- **FR-020**: Un `genero` ausente, nulo, vacío o solo con espacios se sigue aceptando (HU-02, FR-006).
- **FR-021**: El catálogo se consulta como máximo una vez por petición, y solo después de superar la autenticación, el rol y las demás validaciones, y si alguna canción trae género.
- **FR-022**: La validación se aplica solo al crear: las listas ya guardadas no se revisan.

### Key Entities *(include if feature involves data)*

- **Género**: nombre de un género musical, de hasta 100 caracteres, que puede asignarse a una canción.
- **Catálogo de géneros**: conjunto de géneros disponibles en una respuesta, junto con su origen.
- **Origen**: `SPOTIFY` (obtenidos del proveedor externo) o `LOCAL` (lista de respaldo).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las consultas autorizadas de géneros devuelven al menos un género, esté o no disponible Spotify.
- **SC-002**: Con Spotify caído o sin responder, consultar los géneros o crear una lista termina en menos de 4 segundos con la configuración por defecto.
- **SC-003**: Todos los criterios de aceptación de HU-04 en el backlog tienen una prueba automática o, en la verificación, un registro en el ADR.
- **SC-004**: El repositorio y los registros contienen 0 credenciales o tokens de Spotify.
- **SC-005**: Las pruebas pasan sin conexión a Spotify y sin credenciales reales.
- **SC-006**: La verificación del proyecto pasa, incluidas las pruebas de HU-02 y HU-03, con al menos 80 % de cobertura por módulo y sin violaciones de estilo.
- **SC-007**: El 100 % de las listas creadas desde esta historia contienen solo géneros del catálogo vigente al crearlas, o canciones sin género.

## Assumptions

- **Decisiones del ADR-0001**: puerto de géneros con un proveedor de Spotify y otro de respaldo, *client credentials*, credenciales por variables de entorno y WireMock como sustituto ya están acordados.
- **Verificación (2026-11-26)**: con las credenciales reales se obtuvo el token, pero `/recommendations/available-genre-seeds` respondió `404` y `/browse/categories`, `403`. Una consulta de control (`/search`) respondió `200` con el mismo token.
- **Fuente de géneros**: la integración sigue el formato documentado de `/recommendations/available-genre-seeds` y se simula con WireMock. Contra el Spotify real, las consultas responden con la lista local y `origen` `LOCAL`; el origen `SPOTIFY` solo se ve con el servicio simulado. No se derivan géneros de otras consultas de Spotify.
- **Efecto en la validación**: contra el Spotify real, crear una lista valida siempre contra la lista local (FR-018).
- **Contrato**: ni el enunciado ni el ADR definen la respuesta; se usan `generos` y `origen`, en español como el resto del contrato de listas (ADR-0001, sección 8).
- **Arranque sin credenciales**: a diferencia de los secretos de HU-03, las credenciales de Spotify son opcionales porque hay respaldo; así quien evalúe puede ejecutar el proyecto sin una cuenta de Spotify.
- **Tiempo máximo**: "si Spotify falla" incluye que tarde demasiado; se fija en 3 segundos (FR-007), sin reintentos.
- **Sin caché de géneros**: cada consulta, y cada creación con géneros, va a Spotify. La caché con Redis es HU-10.
- **Validación del género**: se decidió el 2026-11-26 validar contra el catálogo. El backlog no lo pedía; HU-02 lo había dejado para esta historia. Se añade el criterio al backlog.
- **Mayúsculas**: no se distinguen (FR-019), porque los géneros de Spotify van en minúsculas y escribir `Rock` es habitual.
- **Riesgo aceptado**: como el catálogo depende de la disponibilidad de Spotify, un género válido en un momento puede rechazarse en otro. La lista local usa la misma forma de nombrar que Spotify (minúsculas y guiones) e incluye los géneros más comunes para reducirlo.
- **Cambio en el contrato de HU-02**: crear una lista deja de aceptar cualquier texto como género. No hay datos previos que migrar, porque la base está en memoria. Las pruebas y los casos HTTP de HU-02 con géneros fuera del catálogo se actualizan, incluido el que espera `201` con un género de 100 caracteres.
- **Tiempo de respuesta de HU-02**: el límite de 1 segundo (HU-02, SC-006) se mantiene cuando el proveedor de géneros responde de inmediato.
- **Documentación**: esta historia añade al `README.md` las variables de Spotify. La explicación de la limitación y OpenAPI y Postman son HU-06.
- **Fuera de alcance**: caché (HU-10), selector de género en la web (HU-05), composición con Docker del servicio simulado (HU-07), otros datos de Spotify (canciones, artistas, álbumes) e inicio de sesión de usuarios con Spotify.
- **Dependencias**: HU-01 (esqueleto), HU-02 (creación de listas), HU-03 (autenticación y roles) y una aplicación de Spotify con cuenta Premium para la verificación.
