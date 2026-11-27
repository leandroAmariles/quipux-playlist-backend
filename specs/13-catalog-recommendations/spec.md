# Feature Specification: Recomendaciones sobre un catálogo (RAG)

**Feature Branch**: `feature/13-catalog-recommendations`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "HU-13: como usuario, quiero sugerencias tomadas de un catálogo, para que sean canciones existentes." Fuentes, en la raíz del espacio de trabajo: `backlog-ia.md` (HU-13) y `0002-recomendaciones-con-ia.md` (ADR-0002, secciones 3 a 10). Contrato y comportamiento vigentes: `specs/12-ai-recommendations`.

**Repositorios afectados**: backend (catálogo, indexación, recuperación, pruebas y documentación) e infra (base de datos vectorial en la composición de la IA y documentación).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tener el catálogo disponible e indexado (Priority: P1)

Como quien instala o evalúa el proyecto, levanto la composición de la IA y el catálogo propio de canciones queda preparado para buscar en él, sin instalar nada fuera de Docker.

**Why this priority**: Sin catálogo indexado no hay de dónde recomendar. Se puede comprobar por sí sola, antes de cambiar el endpoint.

**Independent Test**: Desde un volumen vacío, levantar la composición, comprobar el número de canciones indexadas, repetir la indexación y comparar.

**Acceptance Scenarios**:

1. **Given** una instalación sin datos y las variables de entorno definidas, **When** se levanta la composición base junto con `compose.ai.yaml`, **Then** arranca PostgreSQL con la extensión `vector` y el esquema creado, y el catálogo completo queda indexado sin pasos manuales.
2. **Given** un catálogo ya indexado y sin cambios, **When** se repite la indexación, **Then** el número de canciones indexadas no cambia y no se generan embeddings nuevos.
3. **Given** un catálogo en el que se cambió, añadió o quitó una canción, **When** se vuelve a indexar, **Then** lo indexado coincide con el catálogo vigente, sin duplicados ni versiones antiguas.
4. **Given** una indexación interrumpida o que falló porque los modelos o la base de datos no estaban disponibles, **When** se repite, **Then** se completa sin duplicar lo ya indexado.
5. **Given** la composición en marcha, **When** se revisan los puertos publicados, **Then** la base de datos del catálogo no publica ninguno o solo escucha en `127.0.0.1`.
6. **Given** PostgreSQL detenido o sin configurar, **When** arranca la API, **Then** arranca y el resto de la API funciona con normalidad.
7. **Given** el catálogo del repositorio, **When** se ejecutan las pruebas, **Then** se comprueba que tiene entre 200 y 500 canciones, completas, sin repetidos y con géneros de la lista local.

---

### User Story 2 - Recibir sugerencias que existen en el catálogo (Priority: P1)

Como usuario, recibo hasta 5 canciones del catálogo afines a las de mi lista, con el motivo de cada una, para descubrir música que existe.

**Why this priority**: Es el objetivo de la historia.

**Independent Test**: Con los modelos simulados y un catálogo de prueba indexado en pgvector, pedir recomendaciones y comprobar que todo lo entregado sale del catálogo.

**Acceptance Scenarios**:

1. **Given** la IA activada, el catálogo indexado y una lista con canciones, **When** el modelo elige entre las candidatas, **Then** el usuario recibe `200` con `origen` `IA`, `versionPrompt` `v2` y entre 1 y 5 sugerencias con `idCatalogo` relleno.
2. **Given** la misma situación, **When** se recuperan las candidatas, **Then** son las 20 canciones del catálogo más cercanas a la lista que no están en ella.
3. **Given** una canción del catálogo que ya está en la lista (mismo título y artista, sin distinguir mayúsculas ni espacios de los extremos), **When** se piden recomendaciones, **Then** no se ofrece como candidata ni se entrega, aunque esté más allá de la canción 50.
4. **Given** una respuesta del modelo con identificadores que no están entre las candidatas (inventados, o de otras canciones del catálogo), **When** se procesa, **Then** esas sugerencias se descartan.
5. **Given** una respuesta del modelo con identificadores repetidos o más de 5 válidos, **When** se procesa, **Then** cada canción aparece una sola vez y se entregan las 5 primeras.
6. **Given** una respuesta del modelo que incluye un título, artista o género distinto del real, **When** se procesa, **Then** se entregan los datos del catálogo y del modelo solo se toman la elección y el motivo.
7. **Given** una canción cuyo título contiene instrucciones para el modelo, **When** se piden recomendaciones, **Then** lista y candidatas se envían como datos delimitados, las instrucciones no cambian y la respuesta cumple las mismas validaciones.

---

### User Story 3 - Seguir respondiendo cuando falla una parte (Priority: P1)

Como usuario, recibo sugerencias similares del catálogo cuando falla el modelo, y la lista por defecto cuando no se puede buscar, para no quedarme sin servicio.

**Why this priority**: Mantiene el compromiso de HU-12 de responder siempre, que el backlog no permite recortar.

**Independent Test**: Simular el fallo del modelo, de los embeddings y de la base de datos del catálogo, y comprobar el origen de cada respuesta y las peticiones enviadas.

**Acceptance Scenarios**:

1. **Given** una búsqueda correcta y un modelo que falla (error, tiempo agotado, respuesta vacía, JSON inválido o sin ningún identificador válido), **When** se piden recomendaciones, **Then** el usuario recibe `200` con `origen` `SIMILITUD`: hasta 5 candidatas, de la más cercana a la menos, con `idCatalogo` relleno y `versionPrompt` vacío.
2. **Given** el cortacircuitos del modelo abierto o el máximo de llamadas simultáneas al modelo en curso, **When** se piden recomendaciones, **Then** se busca en el catálogo y se responde con `SIMILITUD` sin llamar al modelo.
3. **Given** que falla la generación del embedding de la lista (error, tiempo agotado o tamaño distinto de 768), **When** se piden recomendaciones, **Then** el usuario recibe `200` con `RESPALDO` sin llamar al modelo de chat.
4. **Given** la base de datos del catálogo detenida, lenta o sin configurar, **When** se piden recomendaciones, **Then** el usuario recibe `200` con `RESPALDO` dentro del tiempo máximo de recuperación, sin llamar al modelo de chat.
5. **Given** un catálogo sin indexar o una búsqueda sin candidatas, **When** se piden recomendaciones, **Then** el usuario recibe `200` con `RESPALDO` sin llamar al modelo de chat.
6. **Given** cualquiera de los fallos anteriores, **When** se atiende una petición, **Then** se envía como máximo una petición de embeddings y una de chat: no hay reintentos.
7. **Given** la IA desactivada (configuración por defecto) o una lista sin canciones, **When** se piden recomendaciones, **Then** se responde con `RESPALDO` sin generar embeddings, buscar ni llamar al modelo.
8. **Given** una lista que no existe o un fallo de la base de datos de usuarios y listas, **When** se piden recomendaciones, **Then** recibe `404` o `500`: ninguna degradación oculta esos errores.

---

### User Story 4 - Reutilizar respuestas sin quedarse con un catálogo antiguo (Priority: P2)

Como responsable del servicio, reutilizo las respuestas de la IA mientras no cambien la lista, el prompt, los modelos ni el catálogo, para ahorrar recursos sin entregar datos obsoletos.

**Why this priority**: Amplía el control de uso de HU-12; necesita que las sugerencias del catálogo ya funcionen.

**Independent Test**: Repetir peticiones contando llamadas, cambiar el catálogo y volver a pedir.

**Acceptance Scenarios**:

1. **Given** una respuesta anterior con `origen` `IA` y sin cambios, **When** se vuelve a pedir antes de que caduque, **Then** se entrega la misma sin generar embeddings, buscar ni llamar al modelo.
2. **Given** una respuesta guardada, **When** se despliega una versión distinta del catálogo y se vuelve a pedir, **Then** se busca y se llama al modelo de nuevo.
3. **Given** una respuesta anterior con `origen` `SIMILITUD` o `RESPALDO`, **When** se vuelve a pedir y todo funciona, **Then** se recibe `origen` `IA`: esos orígenes no se guardan.

---

### Edge Cases

- **Orden de comprobaciones**: dirección no válida (`400`), token (`401`), rol (`403`), límite de peticiones (`429`), existencia de la lista (`404`), lista vacía, IA desactivada, sin clave o catálogo sin configurar (`RESPALDO`), respuesta guardada, embedding y búsqueda (`RESPALDO` si fallan) y, por último, modelo (`SIMILITUD` si falla).
- **Menos de 20 candidatas** (catálogo casi agotado por la lista): se usan las que haya; sin ninguna, `RESPALDO`.
- **Menos de 5 elegidas por el modelo**: con al menos una válida, se entregan solo las válidas; no se completa con similares.
- **Sugerencia similar**: lleva un motivo fijo que no inventa datos ("Similar a las canciones de la lista").
- **Índice desactualizado**: una candidata que ya no esté en el catálogo vigente se descarta; los datos entregados son siempre los del catálogo vigente.
- **Índice incompleto**: se recomienda con lo indexado; las respuestas guardadas así duran hasta que caducan o se reinicia la API.
- **Cambio del modelo detrás del mismo alias**: la aplicación no lo detecta; la documentación explica cómo reindexar desde cero y reiniciar la API.
- **Catálogo del repositorio no válido** (tamaño, campos, repetidos o géneros): la aplicación no arranca y explica el motivo.
- **Proveedor o base de datos del catálogo caídos con respuesta guardada**: se entrega la guardada, con `origen` `IA`.
- **Texto que imita un delimitador**: un título o artista con la marca que cierra un bloque de datos no rompe el bloque, ni en la lista ni en las candidatas.
- **Campos sobrantes del modelo**: se ignoran. **Texto alrededor del JSON**: respuesta no válida.
- **Motivo de más de 120 caracteres**: se recorta; vacío se admite.
- **Dos listas con las mismas canciones**: buscan igual y pueden compartir la respuesta guardada, con `lista` igual al nombre pedido.
- **Instalación de HU-12 que se actualiza**: la composición pide la nueva variable de la base de datos del catálogo; la documentación lo indica.
- Los casos de HU-12 sobre límite por usuario, `HEAD`, reinicio, caducidad y lista por defecto no cambian.

## Requirements *(mandatory)*

### Functional Requirements

**Catálogo**

- **FR-001**: El repositorio de backend incluye un catálogo propio de 200 a 500 canciones, cada una con título, artista, álbum, año y género, sin datos obtenidos de Spotify.
- **FR-002**: Las canciones del catálogo son reales, y la documentación indica su origen y cómo se comprobaron.
- **FR-003**: Cada canción cumple las reglas de una canción de una lista y usa un género de la lista local, de modo que se puede añadir a una lista sin que la validación la rechace. No hay dos con el mismo título y artista, sin distinguir mayúsculas ni espacios de los extremos.
- **FR-004**: Cada canción tiene un identificador UUID derivado de su contenido, estable entre ejecuciones y equipos. Es el valor de `idCatalogo`.
- **FR-005**: El catálogo tiene una versión que cambia cuando cambia su contenido o el modelo de embeddings configurado, y que se puede conocer sin consultar la base de datos ni los modelos.

**Indexación**

- **FR-006**: La indexación genera los embeddings con el alias `recomendador-embeddings` a través de la pasarela y los guarda en el almacén vectorial.
- **FR-007**: Es idempotente, por huella del contenido y del modelo: repetirla sin cambios no duplica ni genera embeddings; con cambios, deja indexado exactamente el catálogo vigente.
- **FR-008**: Al levantar la composición de la IA se ejecuta sin pasos manuales. Se puede repetir, informa de cuántas canciones añadió, conservó y quitó, y de por qué falló. No se expone en la API pública.
- **FR-009**: La API no espera a la indexación para arrancar ni para atender peticiones.

**Almacén vectorial (excepción al diseño reactivo, ADR-0002, secciones 3 y 4)**

- **FR-010**: El catálogo se guarda en PostgreSQL con la extensión `vector`, mediante `PgVectorStore` de Spring AI, con 768 dimensiones, distancia coseno e índice HNSW.
- **FR-011**: El acceso queda encapsulado en un adaptador del puerto del catálogo. Los puertos no exponen tipos de Spring AI ni de JDBC, y las reglas de arquitectura siguen cumpliéndose.
- **FR-012**: Toda llamada JDBC (indexar y buscar), y cualquier otra llamada bloqueante, se ejecuta en `boundedElastic`, nunca en los hilos que reciben las peticiones; una prueba lo verifica.
- **FR-013**: El pool de conexiones es pequeño y tiene tiempos máximos de conexión y de consulta.
- **FR-014**: La conexión a PostgreSQL se declara de forma explícita y no la toma ninguna otra parte de la aplicación: `schema.sql` y las transacciones de las listas siguen en H2 con R2DBC, comprobado con una prueba.
- **FR-015**: La aplicación no crea el esquema vectorial: lo crea un script de la base de datos en la composición.
- **FR-016**: La API arranca con PostgreSQL detenido o sin configurar. Su caída no afecta al arranque, a los demás endpoints ni al estado de salud.

**Recomendación con recuperación**

- **FR-017**: La consulta se construye solo con las canciones de la lista (las mismas y con los mismos recortes que se envían al modelo).
- **FR-018**: Se recuperan hasta 20 candidatas, las más cercanas del catálogo vigente que no estén en la lista completa.
- **FR-019**: El modelo de chat recibe la lista y las candidatas como datos JSON delimitados y elige hasta 5. No tiene herramientas.
- **FR-020**: El prompt es un archivo nuevo y versionado, `v2` (texto de la sección 10 del ADR-0002). `v1` se conserva en el repositorio. Desde esta historia no se pide al modelo que recomiende sin candidatas.
- **FR-021**: De la respuesta del modelo solo se toman `idCatalogo` y `motivo`. Se descartan los identificadores que no estén entre las candidatas y los repetidos, el motivo se recorta a 120 caracteres y se entregan como máximo 5, en el orden del modelo. Título, artista y género salen del catálogo.

**Contrato**

- **FR-022**: El endpoint, los roles, la forma de la respuesta y los errores de HU-12 no cambian. `origen` admite además `SIMILITUD`:

  | `origen` | Cuándo | `idCatalogo` | `versionPrompt` | `motivo` |
  |---|---|---|---|---|
  | `IA` | El modelo eligió al menos una candidata válida | Relleno | `v2` | Del modelo |
  | `SIMILITUD` | Hay candidatas pero el modelo no da un resultado | Relleno | Vacío | Fijo |
  | `RESPALDO` | No hay candidatas, o la IA no se usa | Vacío | Vacío | De la lista por defecto |

- **FR-023**: La especificación OpenAPI y la colección de Postman reflejan el nuevo origen y el uso de `idCatalogo`, y sus pruebas se actualizan.

**Resiliencia y control de uso**

- **FR-024**: Si falla el modelo habiendo candidatas, se responde `SIMILITUD`; si fallan los embeddings o la búsqueda, o no hay candidatas, `RESPALDO`. En todos los casos, `200`.
- **FR-025**: El tiempo máximo total de HU-12 (30 segundos por defecto) incluye la recuperación y el modelo. Embedding y búsqueda tienen además un máximo conjunto propio, de 5 segundos por defecto.
- **FR-026**: No hay reintentos en ninguna capa: cada petición del usuario produce como máximo una petición de embeddings y una de chat.
- **FR-027**: El cortacircuitos y el máximo de llamadas simultáneas de HU-12 se aplican al modelo de chat. Su estado no impide la búsqueda, y los fallos de la recuperación no cuentan en ese cortacircuitos.
- **FR-028**: Las respuestas guardadas se identifican además por la versión del catálogo. Solo se guarda `origen` `IA`; el resto de las reglas de caché, límite por usuario y tamaños de HU-12 no cambia.
- **FR-029**: Un `404` o un error de H2 no se sustituyen por ninguna degradación.
- **FR-030**: El número de candidatas, el tiempo de recuperación, la conexión al catálogo y su pool son configurables sin modificar el código. La contraseña se toma solo de una variable de entorno, sin valor por defecto en el repositorio.
- **FR-031**: Cada respuesta deja en el registro, además de lo de HU-12, la versión del catálogo, el número de candidatas, el tiempo de recuperación y el motivo de cada degradación. Nunca se registran claves, contraseñas, tokens ni el prompt completo.

**Composición (repositorio de infra)**

- **FR-032**: `compose.ai.yaml` añade PostgreSQL con la extensión `vector`, con imagen fijada por versión y digest, sin avisos de seguridad pendientes al implementar, datos en un volumen persistente y esquema creado por script. Configura la API para usarlo. `compose.yaml` no se modifica.
- **FR-033**: El servicio `api` no depende de la base de datos del catálogo, de la indexación ni de los servicios de IA para arrancar.
- **FR-034**: PostgreSQL solo está en la red interna o en `127.0.0.1`. `.env.example` incluye su variable de contraseña sin valor.

**Pruebas**

- **FR-035**: Hay una prueba de integración contra pgvector real con Testcontainers, que indexa, repite la indexación y busca.
- **FR-036**: Los modelos de chat y de embeddings se simulan: las pruebas pasan sin Ollama, sin modelos y sin `AI_API_KEY` real.
- **FR-037**: Cubren como mínimo: validez del catálogo, indexación idempotente, exclusión de las canciones de la lista, identificadores desconocidos, datos tomados del catálogo, `SIMILITUD` por fallo y por tiempo del modelo, `RESPALDO` por fallo de embeddings y por base de datos caída, caché por versión del catálogo, arranque sin PostgreSQL, aislamiento de H2, ejecución en `boundedElastic`, prompt injection y ausencia de secretos en los registros.

**Documentación**

- **FR-038**: El README del backend explica el catálogo, su origen, la indexación (automática, repetida y desde cero) y la nueva configuración. El de infra explica el servicio de PostgreSQL, sus variables y los requisitos adicionales.
- **FR-039**: `AI-LOG.md` se actualiza con cada prompt relevante, lo devuelto, lo aceptado o corregido y cómo se verificó, incluido lo generado con IA para el catálogo.
- **FR-040**: No hay secretos en los archivos ni en el historial de Git.

### Key Entities *(include if feature involves data)*

- **Canción del catálogo**: identificador derivado de su contenido, título, artista, álbum, año y género.
- **Catálogo**: conjunto versionado de canciones del repositorio; fuente de los datos que se entregan.
- **Entrada indexada**: representación de una canción del catálogo que permite compararla por cercanía, ligada a su contenido y al modelo con el que se generó.
- **Candidata**: canción del catálogo cercana a la lista y ausente de ella, entre las que se elige.
- **Origen**: `IA`, `SIMILITUD` o `RESPALDO`, según la tabla de FR-022.
- **Respuesta guardada**: respuesta de la IA ligada a unas canciones, una versión del prompt, unos modelos y una versión del catálogo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las sugerencias con origen `IA` o `SIMILITUD` existe en el catálogo vigente, con sus datos exactos; el 0 % repite una canción de la lista o a otra sugerencia.
- **SC-002**: El 100 % de las peticiones autorizadas, dentro del límite y sobre una lista existente, recibe una respuesta correcta aunque fallen el modelo, los embeddings o el catálogo (salvo fallo de la base de datos de listas).
- **SC-003**: Ninguna petición tarda más de 31 segundos con la configuración por defecto; con el almacén del catálogo caído, responde en menos de 6 segundos.
- **SC-004**: Repetir la indexación de un catálogo sin cambios deja el mismo número de canciones y genera 0 embeddings.
- **SC-005**: Pedir 10 veces seguidas las recomendaciones de una lista sin cambios, con una primera respuesta de origen `IA`, produce 1 búsqueda y 1 llamada al modelo.
- **SC-006**: Una persona con Docker y la documentación levanta la solución desde cero y obtiene sugerencias del catálogo sin instalar nada más para ejecutarla ni lanzar la indexación a mano.
- **SC-007**: El repositorio, su historial y los registros contienen 0 claves, contraseñas o tokens.
- **SC-008**: La verificación del proyecto pasa sin modelos ni claves, con las pruebas de las historias anteriores, al menos 80 % de cobertura por módulo y sin violaciones de estilo.
- **SC-009**: El 100 % de los criterios de aceptación de HU-13 tiene una prueba automática o, si no es automatizable, una comprobación anotada.

## Assumptions

**Decisiones ya tomadas (ADR-0002, aceptado el 2026-11-27)**

- PostgreSQL con pgvector solo para el catálogo (con JDBC) y H2 para usuarios y listas (con R2DBC); `PgVectorStore`, catálogo propio, escalera de degradación, caché local y prompt `v2`. Los nombres de productos y técnicas aparecen en los requisitos porque la historia los exige.

**Cambios respecto a HU-12**

- Se sustituyen su FR-002 (`SIMILITUD` ya se produce), FR-003 (`idCatalogo` relleno en `IA` y `SIMILITUD`) y FR-013 (el prompt vigente pasa a `v2`), y la parte de FR-015, FR-019 y FR-028 que fijaba `RESPALDO` cuando falla el modelo habiendo candidatas.
- Con la IA activada y sin catálogo utilizable ya no se recomienda directamente con el modelo: se responde `RESPALDO`, de modo que toda sugerencia de la IA está verificada.

**Valores y reglas fijados en esta especificación** (el backlog y el ADR no los concretan; revisables antes del plan)

- La indexación se lanza con la composición, sin endpoint de administración. El mecanismo (servicio de un solo uso o tarea en segundo plano) se decide en el plan.
- El catálogo se reúne a mano, con ayuda de IA si conviene, y cada canción se contrasta con una fuente abierta (por ejemplo, MusicBrainz, de datos básicos CC0). No se consulta esa fuente en ejecución.
- Tiempo máximo de recuperación de 5 segundos, dentro de los 30 totales; se mide en el equipo real. El prompt con 20 candidatas puede alargar la respuesta del modelo (HU-12 midió 26,9 s en frío): si acaba a menudo en `SIMILITUD`, se ajusta.
- La recuperación no tiene cortacircuitos ni límite de simultaneidad propios: la acotan sus tiempos máximos, el pool y el límite por usuario.
- Motivo fijo en `SIMILITUD`: "Similar a las canciones de la lista".
- De cada candidata se envían al modelo identificador, título, artista, género y año.
- La prueba con Testcontainers necesita Docker: sin él se omite de forma visible, y en CI se ejecuta siempre.

**Entorno y alcance**

- La IA y el catálogo siguen desactivados por defecto y en Kubernetes: los manifiestos no cambian y se responde `RESPALDO`.
- La composición se ejecuta con `-p quipux-ia`. La rama de infra se crea desde `origin/main` al empezar sus cambios.
- **Dependencias**: HU-12 terminada y con sus pruebas en verde (requisito del backlog); la lista local de géneros (HU-04); Docker. De HU-12 queda pendiente comprobar la descarga de modelos en un volumen vacío; se aprovecha para hacerlo al validar SC-006.
- El PR pasa el CI vigente (HU-08), incluidos el análisis de Trivy y la prueba con Testcontainers.
- **Recorte**: no se recortan la gestión de la clave, el tiempo máximo, el respaldo, la prueba de fallo del proveedor ni `AI-LOG.md`. Si el plazo no alcanza para la historia (2,5 h estimadas), queda como diseño documentado y se anota lo no entregado.
- **Fuera de alcance**: evaluación con RAGAS y su modo interno para obtener contextos, web, Langfuse y trazas (HU-14); administración del catálogo por API; búsqueda de canciones por el usuario; Redis, Kafka; llevar PostgreSQL o los modelos a Kubernetes; mover usuarios y listas a PostgreSQL.
