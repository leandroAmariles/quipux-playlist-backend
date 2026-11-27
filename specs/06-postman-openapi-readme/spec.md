# Feature Specification: Postman, OpenAPI y README

**Feature Branch**: `feature/06-postman-openapi-readme`

**Created**: 2026-11-26

**Status**: Draft

**Input**: User description: "HU-06: como evaluador, quiero un cliente configurado, el contrato documentado e instrucciones claras, para probar el proyecto sin ayuda." Fuentes: `docs/backlog.md` (HU-06), `docs/adr/0001-arquitectura-api-listas-reproduccion.md` (contexto y secciones 1, 3, 4 y 10) y los contratos de HU-02, HU-03 y HU-04.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ejecutar el proyecto con las instrucciones (Priority: P1)

Como evaluador, leo el README y pongo el proyecto en marcha y ejecuto sus pruebas, para revisarlo sin preguntar nada.

**Why this priority**: Es la puerta de entrada. Sin instrucciones completas no se llega a usar la colección ni el contrato.

**Independent Test**: En una copia recién clonada y siguiendo solo el README, arrancar la API, iniciar sesión y ejecutar las pruebas.

**Acceptance Scenarios**:

1. **Given** una copia recién clonada y los requisitos instalados, **When** sigue el README, **Then** ejecuta las pruebas, arranca la API e inicia sesión con los dos usuarios de demostración.
2. **Given** el README, **When** busca cómo configurar la aplicación, **Then** encuentra cada variable de entorno, si es obligatoria, para qué sirve y su valor por defecto cuando lo tiene.
3. **Given** el README, **When** se pregunta por la persistencia, **Then** encuentra en una sección con título propio que se usa **R2DBC en lugar de JPA**, que es una desviación del enunciado, por qué y qué cuesta.
4. **Given** el README, **When** consulta los géneros y recibe el origen `LOCAL`, **Then** encuentra en una sección con título propio la limitación de Spotify, su efecto y cómo ver el origen `SPOTIFY` con un servicio simulado.
5. **Given** el README, **When** quiere ver las decisiones y el resto del proyecto, **Then** encuentra enlaces que funcionan a los ADR y a los tres repositorios.
6. **Given** el README, **When** quiere usar la aplicación web, **Then** sabe que está en el repositorio de frontend y llega a sus instrucciones.

---

### User Story 2 - Probar la API con un cliente configurado (Priority: P1)

Como evaluador, importo una colección de Postman y pruebo la API y sus errores, para no escribir las peticiones a mano.

**Why this priority**: El enunciado pide un cliente REST configurado y es la forma más rápida de comprobar el comportamiento.

**Independent Test**: Importar la colección, escribir las dos contraseñas y ejecutarla completa contra la API recién arrancada.

**Acceptance Scenarios**:

1. **Given** la API en marcha y la colección importada con las contraseñas escritas, **When** ejecuta el inicio de sesión, **Then** el token queda guardado en una variable y las demás peticiones lo usan sin copiarlo.
2. **Given** la colección, **When** la ejecuta completa y en orden, **Then** crea una lista, la consulta, la ve en el listado, consulta los géneros y la borra, y todas las comprobaciones pasan.
3. **Given** la colección, **When** ejecuta los casos de error, **Then** obtiene `400` (datos no válidos y género fuera del catálogo), `401` (credenciales incorrectas y petición sin token), `403` (`USER` crea o borra), `404` (consultar o borrar una lista que no existe) y `409` (nombre repetido).
4. **Given** una ejecución completa anterior, **When** la repite sin reiniciar la API, **Then** todas las comprobaciones vuelven a pasar.
5. **Given** los archivos de Postman del repositorio, **When** se revisan, **Then** no contienen contraseñas, tokens, claves ni credenciales de Spotify.

---

### User Story 3 - Consultar el contrato documentado (Priority: P1)

Como evaluador, abro la documentación interactiva de la API y pruebo las operaciones desde ella, para entender el contrato sin leer el código.

**Why this priority**: Es un criterio de la historia y la referencia que describe lo que la colección solo ejemplifica.

**Independent Test**: Abrir la documentación en un navegador sin sesión, iniciar sesión desde ella, autorizar con el token y ejecutar una operación protegida.

**Acceptance Scenarios**:

1. **Given** la API en marcha y ningún token, **When** abre la documentación interactiva o descarga el contrato OpenAPI, **Then** los recibe sin tener que autenticarse.
2. **Given** el contrato, **When** lo revisa, **Then** encuentra las seis operaciones de la API, los esquemas de petición, respuesta y error, y los códigos de respuesta de cada una.
3. **Given** un token obtenido con el inicio de sesión, **When** lo introduce en la autorización Bearer y ejecuta `GET /lists`, **Then** recibe `200`.
4. **Given** la documentación sin autorizar, **When** ejecuta una operación protegida, **Then** recibe `401`.
5. **Given** que la documentación es pública, **When** se llama sin token a cualquier operación de listas o géneros, **Then** la respuesta sigue siendo `401`.

---

### Edge Cases

- **Contraseñas sin escribir en la colección**: el inicio de sesión responde `400`, porque el campo va en blanco (contrato de HU-03); la colección y el README indican dónde escribirlas.
- **Token vencido** (30 minutos): se vuelve a ejecutar el inicio de sesión y las demás peticiones usan el token nuevo.
- **Otro puerto**: la dirección de la API se cambia en un solo lugar de la colección. La documentación interactiva llama al puerto desde el que se abrió.
- **Spotify disponible, caído o simulado**: la colección crea listas con un género tomado de la consulta de géneros, así que pasa con cualquier origen.
- **Ejecución interrumpida**: si quedó creada la lista de prueba, la siguiente ejecución completa sigue pasando.
- **Nombres con espacios**: la colección usa una lista cuyo nombre tiene espacios y la consulta y borra correctamente.
- **Dirección rechazada por la protección de rutas**: el contrato documenta el `400` en las operaciones que llevan el nombre de la lista.
- **Rutas no documentadas**: abrir la documentación sin token no abre ninguna otra ruta.

## Requirements *(mandatory)*

### Functional Requirements

**Colección de Postman**

- **FR-001**: El repositorio de backend incluye, en `postman/`, una colección importable que cubre las seis operaciones: `POST /auth/login`, `GET /lists`, `GET /lists/{listName}`, `GET /genres`, `POST /lists` y `DELETE /lists/{listName}`.
- **FR-002**: Incluye los errores principales: `400`, `401`, `403`, `404` y `409`, en las situaciones del escenario 3 de la historia 2.
- **FR-003**: El inicio de sesión guarda el token en una variable. `USER` y `ADMIN` usan variables distintas y cada petición usa la del rol que le corresponde.
- **FR-004**: Cada petición comprueba el código de respuesta esperado, y la colección completa se puede ejecutar de una vez, en orden y de forma repetida.
- **FR-005**: La dirección de la API y las contraseñas de los usuarios de demostración son variables. Las contraseñas se entregan vacías.
- **FR-006**: Ningún archivo de Postman contiene secretos reales ni tokens.

**OpenAPI**

- **FR-007**: La aplicación en marcha entrega un contrato OpenAPI y una documentación interactiva (Swagger UI) para explorarlo y probarlo.
- **FR-008**: El contrato describe las seis operaciones con su resumen, el rol que exigen, parámetros, cuerpo de petición y todos sus códigos de respuesta:

  | Operación | Rol | Respuestas |
  |---|---|---|
  | `POST /auth/login` | público | `200`, `400`, `401`, `413`, `415`, `500` |
  | `GET /lists` | `USER`, `ADMIN` | `200`, `401`, `403`, `500` |
  | `GET /lists/{listName}` | `USER`, `ADMIN` | `200`, `400`, `401`, `403`, `404`, `500` |
  | `GET /genres` | `USER`, `ADMIN` | `200`, `401`, `403`, `500` |
  | `POST /lists` | `ADMIN` | `201` con `Location`, `400`, `401`, `403`, `409`, `413`, `415`, `500` |
  | `DELETE /lists/{listName}` | `ADMIN` | `204`, `400`, `401`, `403`, `404`, `500` |

- **FR-009**: Describe los esquemas con los nombres de campo reales (`nombre`, `descripcion`, `canciones`, `titulo`, `artista`, `album`, `anno`, `genero`, `generos`, `origen`, los del inicio de sesión y los del error `ProblemDetail` con `code`), indicando cuáles son obligatorios.
- **FR-010**: Declara el esquema de seguridad Bearer JWT, aplicado a todas las operaciones salvo el inicio de sesión, y permite introducir el token desde la documentación interactiva.
- **FR-011**: El contrato y la documentación interactiva se abren sin token. No se abre sin token ninguna otra ruta.
- **FR-012**: Una prueba automática falla si falta en el contrato una operación, uno de sus códigos de respuesta de FR-008 o el esquema Bearer JWT, o si el contrato publica una operación que no está en FR-008.
- **FR-013**: El contrato y sus ejemplos no contienen contraseñas, tokens ni claves reales.

**README**

- **FR-014**: El README del backend explica requisitos, variables de entorno, cómo ejecutar y cómo probar, y remite al README del frontend para la aplicación web.
- **FR-015**: Indica cómo abrir la documentación interactiva y cómo importar y ejecutar la colección de Postman.
- **FR-016**: Justifica **R2DBC en lugar de JPA** en una sección visible, con título propio y antes de las instrucciones de ejecución: que el enunciado pide JPA, el motivo del cambio y sus consecuencias.
- **FR-017**: Explica la limitación de Spotify en una sección con título propio: qué respondió Spotify, que se usa la lista local y cómo ver el origen `SPOTIFY` con un servicio simulado.
- **FR-018**: Enlaza los ADR de `docs/adr/` y los tres repositorios (backend, frontend e infra).
- **FR-019**: Describe el estado real del proyecto: no presenta como disponible lo que corresponde a historias posteriores.

**Restricciones transversales**

- **FR-020**: Las operaciones existentes no cambian su comportamiento.

### Key Entities *(include if feature involves data)*

- **Colección de Postman**: peticiones ordenadas, sus comprobaciones y las variables (dirección, contraseñas, tokens y datos de la lista de prueba).
- **Contrato OpenAPI**: descripción de las operaciones, esquemas, códigos de respuesta y seguridad de la API.
- **README**: punto de entrada del proyecto.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona que no conoce el proyecto, siguiendo solo el README y con los requisitos instalados, crea su primera lista en menos de 15 minutos, sin contar las descargas.
- **SC-002**: La colección ejecutada dos veces seguidas contra la API recién arrancada termina con el 100 % de las comprobaciones en verde.
- **SC-003**: El 100 % de las operaciones y códigos de respuesta de FR-008 aparecen en el contrato.
- **SC-004**: El repositorio contiene 0 contraseñas, tokens o claves reales.
- **SC-005**: El 100 % de los enlaces del README funcionan.
- **SC-006**: Todos los criterios de aceptación de HU-06 en el backlog quedan comprobados, por prueba automática o por revisión anotada.
- **SC-007**: La verificación del proyecto pasa, con las pruebas anteriores, al menos 80 % de cobertura por módulo y sin violaciones de estilo.

## Assumptions

- **Decisiones del ADR-0001**: OpenAPI con Swagger UI y Bearer JWT (sección 4), colección en `postman/` del backend y README del backend como punto de entrada (sección 3) ya están acordados.
- **Documentación pública**: contrato e interfaz se abren sin token, como dejó previsto el plan de HU-03, y quedan siempre activos. Es un proyecto de demostración que no debe exponerse en una red pública.
- **Idioma**: el README y las descripciones del contrato y de la colección van en español. Los identificadores y los mensajes de error de la API no cambian.
- **Errores principales**: son `400`, `401`, `403`, `404` y `409`. `413`, `415` y `500` se documentan en el contrato, sin caso en la colección.
- **Comprobaciones automáticas en la colección** (FR-004): el backlog no las pide; se incluyen para que el evaluador vea el resultado sin comparar respuestas a mano.
- **Estado actual del README**: ya explica requisitos, variables, comandos y enlaza los tres repositorios; esta historia completa lo que falta y lo reorganiza.
- **ADR existentes**: hoy hay un solo ADR (0001); se enlaza ese y no se escriben ADR nuevos.
- **R2DBC**: la consulta a quien envió la prueba sigue sin respuesta (ADR-0001, "Pendiente"); el README expone la desviación sin darla por aceptada.
- **Fuera de alcance**: Docker y Compose y sus instrucciones (HU-07), CI (HU-08), colección o documentación de las rutas de salud (HU-09), cambios en la aplicación web y publicar el contrato fuera de la aplicación en marcha.
- **Dependencias**: HU-02 a HU-05, ya integradas en `main`.
