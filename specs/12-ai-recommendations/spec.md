# Feature Specification: Recomendaciones con IA

**Feature Branch**: `feature/12-ai-recommendations`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "HU-12: como usuario, quiero recomendaciones generadas por IA para una lista, con respuesta aunque la IA falle, para descubrir música sin quedarme sin servicio." Fuentes, en la raíz del espacio de trabajo: `backlog-ia.md` (HU-12) y `0002-recomendaciones-con-ia.md` (ADR-0002). Contratos vigentes: `specs/02-playlist-management` y `specs/03-authentication-authorization`.

**Repositorios afectados**: backend (endpoint, cliente, pruebas y documentación) e infra (composición de la IA y documentación). Los dos bloques pueden avanzar a la vez.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pedir recomendaciones de una lista (Priority: P1)

Como usuario, pido recomendaciones para una de las listas y siempre recibo una respuesta con el mismo formato, aunque la IA no esté activada.

**Why this priority**: Es la base de la historia y el requisito del módulo. Entrega valor por sí sola: funciona con la configuración por defecto, sin modelos ni claves.

**Independent Test**: Arrancar la aplicación sin configurar la IA, pedir recomendaciones de una lista con cada tipo de usuario y comprobar la respuesta y los errores.

**Acceptance Scenarios**:

1. **Given** la IA desactivada (configuración por defecto, sin servicios de modelos ni `AI_API_KEY`) y una lista con canciones, **When** un `USER` pide `GET /lists/{listName}/recommendations`, **Then** recibe `200` con la lista por defecto, `origen` `RESPALDO` y `versionPrompt` vacío.
2. **Given** la misma situación, **When** la pide un `ADMIN`, **Then** recibe la misma respuesta.
3. **Given** una lista que no existe, **When** un usuario autorizado pide sus recomendaciones, **Then** recibe `404` con `ProblemDetail` y no se llama al modelo.
4. **Given** una petición sin token, o con un token inválido o vencido, **When** pide recomendaciones, **Then** recibe `401` y no se consulta la lista ni se llama al modelo.
5. **Given** un token válido sin un rol permitido, **When** pide recomendaciones, **Then** recibe `403`.
6. **Given** una dirección con un nombre de lista no válido (rechazada por la protección de rutas), **When** se pide, **Then** recibe `400` con `ProblemDetail`.
7. **Given** la IA activada y una lista sin canciones, **When** pide sus recomendaciones, **Then** recibe `200` con `origen` `RESPALDO` sin llamar al modelo.

---

### User Story 2 - Recibir sugerencias de la IA (Priority: P1)

Como usuario, recibo hasta 5 canciones sugeridas por la IA según las canciones de la lista, con el motivo de cada una, para descubrir música.

**Why this priority**: Es el objetivo de la historia. La respuesta del modelo no es de fiar, así que su validación forma parte de la entrega.

**Independent Test**: Con el proveedor de modelos simulado, devolver respuestas correctas y defectuosas y revisar qué recibe el usuario.

**Acceptance Scenarios**:

1. **Given** la IA activada y una lista con canciones, **When** el modelo responde con sugerencias válidas, **Then** el usuario recibe `200` con `origen` `IA`, `versionPrompt` `v1`, el nombre de la lista y entre 1 y 5 sugerencias con `idCatalogo` vacío.
2. **Given** una respuesta con una canción que ya está en la lista (mismo título y artista, sin distinguir mayúsculas ni espacios de los extremos), **When** se procesa, **Then** esa sugerencia se descarta.
3. **Given** una respuesta con sugerencias repetidas entre sí, **When** se procesa, **Then** cada canción aparece una sola vez.
4. **Given** una respuesta con más de 5 sugerencias válidas, **When** se procesa, **Then** se entregan las 5 primeras.
5. **Given** una respuesta en la que unas sugerencias son válidas y otras no (sin título o artista, o con un campo demasiado largo), **When** se procesa, **Then** se entregan solo las válidas, con `origen` `IA`.
6. **Given** una respuesta sin ninguna sugerencia válida, **When** se procesa, **Then** se responde con `RESPALDO`.
7. **Given** una canción cuyo título contiene instrucciones para el modelo, **When** se piden recomendaciones, **Then** el título se envía como dato delimitado, las instrucciones del sistema no cambian y la respuesta cumple las mismas validaciones.

---

### User Story 3 - Seguir respondiendo cuando la IA falla (Priority: P1)

Como usuario, recibo la lista por defecto cuando la IA falla o tarda demasiado, para no quedarme sin servicio.

**Why this priority**: Es parte del enunciado de la historia y uno de los puntos que el backlog no permite recortar.

**Independent Test**: Simular cada fallo del proveedor y comprobar la respuesta, el número de peticiones enviadas y que el resto de la API sigue funcionando.

**Acceptance Scenarios**:

1. **Given** el proveedor responde con un error (`4xx`, incluidos `401` y `429`, o `5xx`) o no se puede establecer la conexión, **When** se piden recomendaciones, **Then** el usuario recibe `200` con `RESPALDO`.
2. **Given** el proveedor no termina de responder dentro del tiempo máximo, **When** se cumple ese tiempo, **Then** el usuario recibe `200` con `RESPALDO`.
3. **Given** el proveedor responde vacío, con contenido que no es JSON o con un JSON de otra forma, **When** se procesa, **Then** el usuario recibe `200` con `RESPALDO`.
4. **Given** cualquiera de los fallos anteriores, **When** se atiende una petición, **Then** el proveedor recibe como máximo una petición: no hay reintentos.
5. **Given** que los últimos intentos han fallado hasta abrir el cortacircuitos, **When** llega otra petición, **Then** se responde con `RESPALDO` sin llamar al proveedor.
6. **Given** el cortacircuitos abierto y el proveedor recuperado, **When** pasa el tiempo de espera y llega una petición, **Then** se vuelve a probar y, si responde bien, el usuario recibe `origen` `IA`.
7. **Given** una lista que no existe o un fallo de la base de datos, **When** se piden recomendaciones, **Then** recibe `404` o `500`: el respaldo no oculta esos errores.
8. **Given** los servicios de modelos detenidos, **When** se usa el resto de la API, **Then** funciona con normalidad y la aplicación puede arrancar.

---

### User Story 4 - Dejar constancia de la autoría y la revisión de la IA (Priority: P1)

Como evaluador, reviso qué generó el asistente de IA, qué se corrigió y por qué, para valorar el criterio aplicado.

**Why this priority**: El módulo lo exige y el backlog no permite recortar `AI-LOG.md`. Además condiciona el orden del trabajo: el código generado debe guardarse antes de modificarlo.

**Independent Test**: Leer `AI-LOG.md` y el README del backend y contrastarlos con el código y el historial.

**Acceptance Scenarios**:

1. **Given** el encargo de la implementación completa del cliente a un asistente, **When** se consulta `AI-LOG.md`, **Then** están el prompt y el código tal como se generó, antes de cualquier cambio.
2. **Given** el código generado, **When** se lee la revisión, **Then** cubre secretos, bloqueo, timeout, reintentos acumulados, errores, validación, prompt injection, logs y pruebas, y describe cada cambio aplicado con su motivo.
3. **Given** las pruebas entregadas, **When** se consulta `AI-LOG.md`, **Then** distingue los casos generados por IA de los añadidos a mano.
4. **Given** el README del backend, **When** se lee la sección "Autoría IA", **Then** refleja lo que ocurrió.

---

### User Story 5 - Controlar el uso del modelo (Priority: P2)

Como responsable del servicio, limito cuánto se usa el modelo, para que nadie consuma sus recursos sin control.

**Why this priority**: El módulo pide control de coste y abuso, pero necesita que el endpoint y la llamada al modelo ya funcionen.

**Independent Test**: Repetir peticiones con el proveedor simulado y contar las llamadas que recibe y las respuestas `429`.

**Acceptance Scenarios**:

1. **Given** una respuesta anterior con `origen` `IA` y las canciones sin cambios, **When** se vuelven a pedir recomendaciones antes de que caduque, **Then** se entrega la misma respuesta sin llamar al modelo.
2. **Given** una lista borrada y creada de nuevo con otras canciones, **When** se piden recomendaciones, **Then** se llama al modelo.
3. **Given** una respuesta anterior con `origen` `RESPALDO`, **When** se vuelve a pedir y el proveedor funciona, **Then** se llama al modelo y se recibe `origen` `IA`.
4. **Given** un usuario con 10 peticiones en el último minuto, **When** hace la undécima, **Then** recibe `429` con la cabecera `Retry-After`, `ProblemDetail` y el código `RECOMMENDATION_ERROR_001`.
5. **Given** un usuario que recibió `429`, **When** pasa el tiempo indicado en `Retry-After`, **Then** su siguiente petición se atiende.
6. **Given** un usuario que alcanzó su límite, **When** otro usuario pide recomendaciones, **Then** se atiende.
7. **Given** el máximo de llamadas simultáneas al modelo en curso, **When** llega otra petición que necesitaría el modelo, **Then** no espera turno y recibe `200` con `RESPALDO`.
8. **Given** una lista con más de 50 canciones, **When** se piden recomendaciones, **Then** se envían al modelo como máximo 50.

---

### User Story 6 - Levantar los modelos en el propio equipo (Priority: P2)

Como quien instala o evalúa el proyecto, levanto los modelos y la pasarela con un archivo de composición adicional, sin instalar nada fuera de Docker y sin enviar datos a terceros.

**Why this priority**: Permite ver la IA real, pero el resto de la historia se desarrolla y se prueba sin ella.

**Independent Test**: En un equipo con Docker, levantar la composición con la IA, esperar la descarga de modelos y pedir recomendaciones.

**Acceptance Scenarios**:

1. **Given** una clave `AI_API_KEY` en el entorno, **When** se levanta la composición base junto con `compose.ai.yaml`, **Then** arrancan los modelos y la pasarela, la API queda con la IA activada y los alias `recomendador-chat` y `recomendador-embeddings` responden.
2. **Given** modelos ya descargados, **When** se vuelve a levantar la composición, **Then** no se descargan de nuevo.
3. **Given** un equipo con GPU NVIDIA compatible, **When** se añade `compose.ai.gpu.yaml`, **Then** se comprueba que el modelo de chat se ejecuta en la GPU.
4. **Given** un equipo sin GPU, **When** se levanta sin ese archivo, **Then** todo arranca en CPU.
5. **Given** los servicios de IA caídos o sin terminar de arrancar, **When** se levanta la API, **Then** arranca sin esperar por ellos.
6. **Given** la composición en marcha, **When** se revisan los puertos publicados, **Then** el servicio de modelos no publica ninguno y los demás servicios de IA solo escuchan en `127.0.0.1`.
7. **Given** la composición base sin `compose.ai.yaml`, **When** se levanta, **Then** funciona como antes de esta historia, sin pedir `AI_API_KEY`.

---

### Edge Cases

- **Orden de comprobaciones**: dirección no válida (`400`), token (`401`), rol (`403`), límite de peticiones (`429`), existencia de la lista (`404`), lista vacía o IA desactivada (`RESPALDO`), respuesta guardada y, por último, llamada al modelo.
- **Qué cuenta para el límite**: toda petición que supera token y rol, termine en `200`, `404` o `500`, venga o no de una respuesta guardada. Las rechazadas con `400`, `401`, `403` o `429` no cuentan.
- **Peticiones `HEAD`**: se tratan como las `GET` (mismos permisos, mismo límite y mismo trabajo).
- **Límite con la IA desactivada**: se aplica igual.
- **Dos listas con las mismas canciones**: pueden compartir la respuesta guardada, pero `lista` es siempre el nombre pedido.
- **Dos usuarios, la misma lista**: comparten la respuesta guardada; cada uno consume su propio límite.
- **Peticiones simultáneas sin respuesta guardada**: cada una puede llamar al modelo, hasta el máximo de llamadas simultáneas.
- **Proveedor caído con respuesta guardada**: se entrega la guardada, con `origen` `IA`.
- **Caducidad o espacio agotado**: una respuesta caducada o descartada por falta de espacio vuelve a pedirse al modelo.
- **Reinicio**: las respuestas guardadas y los contadores de peticiones se pierden.
- **Cambios más allá de la canción 50**: cualquier cambio en las canciones descarta la respuesta guardada, y ninguna sugerencia puede repetir una canción de la lista completa.
- **Texto que imita el delimitador**: un título o artista con la marca que cierra el bloque de datos no rompe el bloque.
- **Modelo que rellena `idCatalogo`**: el valor se ignora y se entrega vacío.
- **Campos sobrantes del modelo**: se ignoran.
- **Texto alrededor del JSON**: la respuesta se considera inválida y se usa el respaldo.
- **Motivo de más de 120 caracteres**: se recorta; la sugerencia no se descarta.
- **Respaldo y canciones de la lista**: las canciones de la lista por defecto que ya estén en la lista se omiten; la respuesta puede traer menos de 5, incluso ninguna.
- **Lista por defecto que no cumple FR-021** (ausente, mal formada, incompleta o con repetidos): la aplicación no arranca y explica el motivo.
- **IA activada sin `AI_API_KEY` o con la clave en blanco**: la aplicación arranca, avisa en el registro y responde con `RESPALDO` sin llamar al proveedor.
- **Lista llamada `recommendations`**: se consulta y se recomienda como cualquier otra.

## Requirements *(mandatory)*

### Functional Requirements

**Consulta y contrato (`GET /lists/{listName}/recommendations`)**

- **FR-001**: Debe responder `200` a los roles `USER` y `ADMIN` con `lista`, `origen`, `versionPrompt` y `recomendaciones`; cada recomendación tiene `idCatalogo`, `titulo`, `artista`, `genero` y `motivo`:

  ```json
  {
    "lista": "Lista 1",
    "origen": "IA",
    "versionPrompt": "v1",
    "recomendaciones": [
      { "idCatalogo": "", "titulo": "", "artista": "", "genero": "", "motivo": "" }
    ]
  }
  ```

- **FR-002**: `origen` es `IA` o `RESPALDO`. `SIMILITUD` queda reservado para HU-13 y no se produce en esta historia.
- **FR-003**: `versionPrompt` es la versión del prompt usado cuando `origen` es `IA` y va vacío en cualquier otro caso. `idCatalogo` va siempre vacío: las sugerencias no están verificadas contra un catálogo.
- **FR-004**: `recomendaciones` tiene como máximo 5 elementos y está siempre presente, aunque esté vacío.
- **FR-005**: Los errores `400`, `401`, `403`, `404` y `500` siguen las mismas convenciones que `GET /lists/{listName}`, con `ProblemDetail` y sus códigos. El `429` usa `ProblemDetail`, el código propio `RECOMMENDATION_ERROR_001` y `Retry-After` en segundos.
- **FR-006**: Las comprobaciones se hacen en el orden descrito en "Edge Cases".
- **FR-007**: La especificación OpenAPI y la colección de Postman incluyen el endpoint, y se actualizan las listas acordadas con las que sus pruebas los comparan.

**Activación y configuración**

- **FR-008**: La IA está desactivada por defecto. La aplicación arranca sin servicios de modelos y sin `AI_API_KEY`, y un proveedor simulado hace que el endpoint responda con `RESPALDO`.
- **FR-009**: La clave se toma únicamente de la variable de entorno `AI_API_KEY`, sin valor por defecto en el repositorio. Con la IA activada y sin clave se aplica el caso descrito en "Edge Cases".
- **FR-010**: La activación, la dirección del proveedor, los alias de los modelos, y los valores de FR-016, FR-017, FR-020, FR-026, FR-027 y FR-028 son configurables sin modificar el código.

**Sugerencias de la IA**

- **FR-011**: La integración usa Spring AI 2.0.x (2.0.3 o un parche posterior) contra la pasarela de modelos, compatible con la API de OpenAI. El backend no llama directamente al servicio de modelos.
- **FR-012**: Spring AI queda solo en la capa de infraestructura: los puertos no exponen sus tipos y las reglas de arquitectura siguen cumpliéndose.
- **FR-013**: El prompt está en un archivo versionado del repositorio (`v1`, texto de la sección 10 del ADR-0002).
- **FR-014**: Las canciones se envían como datos JSON delimitados, tratados como datos y no como instrucciones. El modelo no tiene herramientas.
- **FR-015**: La respuesta del modelo se valida antes de entregarla: forma esperada, `titulo` y `artista` presentes, tamaños máximos (`titulo` y `artista`, 255; `genero`, 100; `motivo`, 120), sin duplicados, sin canciones que ya están en la lista y como máximo 5. Las sugerencias que no cumplen se descartan, salvo el motivo largo, que se recorta. Sin ninguna válida, se usa el respaldo.
- **FR-016**: Se envían al modelo como máximo 50 canciones (las primeras de la lista), con cada campo recortado a 100 caracteres, y se limita la respuesta a 600 tokens.

**Resiliencia y respaldo**

- **FR-017**: La llamada al modelo tiene un tiempo máximo total de 30 segundos por defecto, incluida la recepción completa de la respuesta.
- **FR-018**: No hay reintentos en ninguna capa (librería de IA, cliente del proveedor y pasarela): cada petición del usuario produce como máximo una petición al proveedor.
- **FR-019**: Tiempo agotado, fallo de conexión, error HTTP, respuesta vacía, JSON inválido o sin sugerencias válidas devuelven `200` con `RESPALDO`.
- **FR-020**: Un cortacircuitos deja de llamar al proveedor cuando falla de forma repetida y vuelve a probar pasado un tiempo. Por defecto se abre si falla al menos el 50 % de las últimas 10 llamadas (con 5 como mínimo) y espera 30 segundos. Cuentan los tiempos agotados, los fallos de conexión y los errores HTTP.
- **FR-021**: La lista por defecto está en el repositorio, tiene al menos 5 canciones con motivo y géneros de la lista local, sin repetidos, y se puede cambiar sin modificar el código.
- **FR-022**: Un `404` o un error de la base de datos no se sustituyen por el respaldo.
- **FR-023**: La caída o la lentitud de la IA no afecta al arranque, a los demás endpoints ni al estado de salud de la API.
- **FR-024**: La consulta se atiende sin bloquear los hilos que reciben las peticiones, comprobado con BlockHound.
- **FR-025**: Cada respuesta de recomendaciones deja en el registro la latencia, el origen, el uso de una respuesta guardada, el modelo y la versión del prompt; cuando se usa el respaldo, también el motivo. Nunca se registran claves, tokens ni el prompt completo.

**Control de uso**

- **FR-026**: Las respuestas con `origen` `IA` se guardan en una caché local acotada (por defecto, 500 respuestas) durante 24 horas, identificadas por las canciones de la lista, la versión del prompt y los modelos. El respaldo no se guarda.
- **FR-027**: Cada usuario puede hacer 10 peticiones por minuto; las siguientes reciben `429` hasta que pase el tiempo indicado.
- **FR-028**: Hay un máximo de llamadas simultáneas al modelo (por defecto, 2). Alcanzado, no se encola: se responde con `RESPALDO`.
- **FR-029**: Una lista sin canciones responde con `RESPALDO` sin llamar al modelo.

**Pasarela local de modelos (repositorio de infra)**

- **FR-030**: `compose.ai.yaml` levanta el servicio de modelos (Ollama) y la pasarela (LiteLLM), conectada por la red interna a `http://ollama:11434`, y activa la IA en la API. `compose.yaml` no se modifica.
- **FR-031**: El servicio `api` no depende de los servicios de IA para arrancar.
- **FR-032**: La imagen de Ollama es la oficial, `0.40.18` o posterior, sin avisos de seguridad pendientes al implementar y fijada por digest.
- **FR-033**: La imagen de LiteLLM es la oficial, `1.111.0` o posterior, sin avisos de seguridad pendientes al implementar, con la firma comprobada y fijada por digest. No se usa `latest` ni etiquetas móviles.
- **FR-034**: Un servicio de inicialización descarga los modelos de chat y de embeddings en un volumen persistente solo si faltan.
- **FR-035**: Existen los alias `recomendador-chat` y `recomendador-embeddings`, y ambos responden.
- **FR-036**: Los modelos se precargan al arrancar, y la documentación anota la latencia medida en frío y en caliente.
- **FR-037**: La GPU se reserva en un archivo aparte, `compose.ai.gpu.yaml`; sin él todo arranca en CPU.
- **FR-038**: El servicio de modelos no publica puertos; los que publiquen los demás servicios de IA solo se abren en `127.0.0.1`.
- **FR-039**: `.env.example` incluye `AI_API_KEY` sin valor y `.env` está ignorado por Git.

**Pruebas**

- **FR-040**: Las pruebas automáticas simulan la pasarela con WireMock: pasan sin Ollama, sin modelos, sin `AI_API_KEY` real y sin acceso a internet.
- **FR-041**: Cubren como mínimo: éxito, `401`, `404`, lista vacía, caché, `429`, proveedor con error y con timeout (ambos con respaldo), una sola petición al proveedor, respuesta mal formada, intento de prompt injection, ausencia de la clave en los registros y ausencia de llamadas bloqueantes.

**Autoría IA y documentación**

- **FR-042**: Se pide a un asistente la implementación completa del cliente, y `AI-LOG.md` conserva el prompt y el código tal como se generó.
- **FR-043**: `AI-LOG.md` registra la revisión crítica (secretos, bloqueo, timeout, reintentos acumulados, errores, validación, prompt injection, logs y pruebas), los cambios aplicados con su motivo y, para cada prompt relevante, la respuesta, lo aceptado o corregido y cómo se verificó. Distingue las pruebas generadas por IA de las añadidas a mano.
- **FR-044**: El README del backend incluye la sección "Autoría IA", fiel a lo ocurrido.
- **FR-045**: Los README de backend e infra explican, según les corresponda, `AI_API_KEY`, los archivos de composición de la IA, la descarga de modelos, la GPU, la memoria de Docker y los requisitos de hardware.
- **FR-046**: Si Dependabot ya está configurado al integrar la historia, ignora Spring AI 2.1 o superior y Spring Boot 4.2 o superior.
- **FR-047**: No hay secretos en los archivos ni en el historial de Git.

### Key Entities *(include if feature involves data)*

- **Respuesta de recomendaciones**: nombre de la lista, origen, versión del prompt y hasta 5 recomendaciones.
- **Recomendación**: canción sugerida con título, artista, género y motivo. Su identificador de catálogo va vacío hasta HU-13.
- **Origen**: `IA` (sugerida por el modelo y validada) o `RESPALDO` (tomada de la lista por defecto).
- **Lista por defecto**: conjunto fijo de recomendaciones del repositorio, usado como respaldo.
- **Versión del prompt**: identificador del texto de instrucciones con el que se generó una respuesta.
- **Respuesta guardada**: respuesta de la IA conservada temporalmente, ligada a unas canciones, una versión del prompt y unos modelos.
- **Cuota de usuario**: número de peticiones recientes de un usuario y momento en que puede volver a pedir.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las peticiones autorizadas, dentro del límite y sobre una lista existente reciben una respuesta correcta, funcione o no la IA (salvo fallo de la propia base de datos).
- **SC-002**: Con la configuración por defecto, ninguna petición tarda más de 31 segundos; con la IA desactivada o sin llamada al modelo, responde en menos de 1 segundo.
- **SC-003**: El 0 % de las respuestas de la IA entregadas contiene canciones de la lista, duplicados, campos fuera de tamaño o más de 5 sugerencias.
- **SC-004**: Pedir 10 veces seguidas las recomendaciones de una lista sin cambios, con una primera respuesta correcta, produce 1 sola llamada al modelo.
- **SC-005**: Un usuario no consigue más de 10 peticiones atendidas por minuto, y su límite no afecta a otros usuarios.
- **SC-006**: Una persona con Docker y la documentación levanta la solución con IA sin instalar ningún otro programa para ejecutarla, y en el segundo arranque no descarga modelos.
- **SC-007**: El repositorio, su historial y los registros contienen 0 claves o tokens.
- **SC-008**: La verificación del proyecto pasa sin modelos ni claves, con las pruebas de las historias anteriores, al menos 80 % de cobertura por módulo y sin violaciones de estilo.
- **SC-009**: El 100 % de los criterios de aceptación de HU-12 tiene una prueba automática o, si no es automatizable (infra, autoría y documentación), una comprobación anotada.

## Assumptions

**Decisiones ya tomadas (ADR-0002, aceptado el 2026-11-27)**

- Ollama en Docker detrás de LiteLLM, Spring AI 2.0.x en el backend, contrato, respaldo, caché local, límites y prompt `v1`. Los nombres de productos, versiones y rutas aparecen en los requisitos porque la historia los exige.
- La caché y el contador de peticiones viven en memoria: hay una sola réplica y la historia no depende de Redis (HU-10).
- El modelo de chat se elige después de medir, partiendo de uno pequeño; el de embeddings parte de `nomic-embed-text`. Los embeddings no se usan en las recomendaciones hasta HU-13: aquí solo se comprueba que su alias responde.

**Valores y reglas fijados en esta especificación** (el backlog y el ADR no los concretan; revisables antes del plan)

- Tamaños: 100 caracteres por campo enviado, 600 tokens de respuesta, 2 llamadas simultáneas y 500 respuestas guardadas.
- Cortacircuitos: 50 % de 10 llamadas, mínimo 5 y 30 segundos de espera. Una respuesta con contenido inválido usa el respaldo, pero no cuenta como proveedor caído. Tampoco cuentan los rechazos por llamadas simultáneas.
- Validación: se descartan las sugerencias defectuosas y se conservan las demás; el motivo largo se recorta; `genero` y `motivo` pueden quedar vacíos; no se comprueba el género contra el catálogo ni la existencia real de la canción.
- Respaldo: omite las canciones que ya están en la lista.
- Límite: toda petición autorizada cuenta, también las que terminan en `404`, como indica el orden del diagrama del ADR (límite antes de la lista).
- Nombre no válido: es el mismo `400` de `GET /lists/{listName}`, que la protección de rutas responde antes de comprobar el token.
- IA activada sin clave: arranca y usa el respaldo, igual que Spotify sin credenciales (HU-04). La composición `compose.ai.yaml`, en cambio, exige definir `AI_API_KEY`.

**Entorno y alcance**

- En desarrollo local, `AI_API_KEY` puede ser la clave maestra de la pasarela. Las claves virtuales con límites quedan fuera.
- El tiempo máximo de 30 segundos puede no bastar en CPU o en frío; se mide y se ajusta. Con 60 segundos o más hay que revisar el tiempo de espera del servidor web.
- La plantilla de `AI-LOG.md` sigue pendiente (ADR-0002). Si no se entrega antes de implementar, se usa una propia con los campos de FR-043. `AI-LOG.md` vive en la raíz del backend y se empieza desde ya.
- La composición se ejecuta con `-p quipux-ia` para no reemplazar los contenedores del trabajo base. La IA queda desactivada en Kubernetes: los manifiestos no cambian.
- **Recorte**: si el plazo no alcanza, se aplican los puntos de corte de `backlog-ia.md`. No se recortan la gestión de la clave, el tiempo máximo, el respaldo, la prueba de fallo del proveedor ni `AI-LOG.md`.
- **Fuera de alcance**: catálogo, pgvector, uso de embeddings y origen `SIMILITUD` (HU-13); evaluación, web, Langfuse y trazas (HU-14); Redis, Kafka y despliegue de los modelos en Kubernetes.
- **Dependencias**: HU-02 (listas), HU-03 (token y roles), HU-06 (OpenAPI y Postman) y HU-07 (Docker y Compose), entregadas; la lista local de géneros de HU-04; y Docker con memoria y disco suficientes para los modelos.
