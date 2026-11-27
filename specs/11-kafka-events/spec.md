# Feature Specification: Eventos en Kafka

**Feature Branch**: `feature/11-kafka-events`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "HU-11: como auditor, quiero un evento por cada creación y borrado, para tener trazabilidad." Fuentes: `docs/backlog.md` (HU-11) y `docs/adr/0001-arquitectura-api-listas-reproduccion.md` (secciones 2, 6, 7, 11 y 13). Contratos vigentes: `specs/02-playlist-management`, `specs/03-authentication-authorization` y `specs/04-genres`.

**Repositorios afectados**: backend (modelos de evento de dominio, puerto de salida de publicación, adaptador productor y consumidor de auditoría en Kafka, integración post-commit en casos de uso de listas, configuración, pruebas con Testcontainers y documentación) e infra (servicio Kafka en modo KRaft en Docker Compose, manifiestos en Kubernetes y documentación).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Publicar eventos `PlaylistCreated` y `PlaylistDeleted` después del commit (Priority: P1)

Como auditor, quiero que cada creación y cada borrado confirmado de una lista de reproducción publique automáticamente un evento (`PlaylistCreated` o `PlaylistDeleted`) en Kafka únicamente después de confirmarse la transacción en base de datos, para contar con trazabilidad fiable de los cambios reales sin falsos eventos ante fallos o reversiones.

**Why this priority**: Es la capacidad central de la historia: emitir eventos precisos de negocio ligados exclusivamente a operaciones confirmadas sobre listas, manteniendo intacto el comportamiento funcional de `POST /lists` y `DELETE /lists/{listName}`.

**Independent Test**: Con Kafka disponible, crear una lista válida (`201`), provocar intentos fallidos de creación (`400`, `409`, fallo transaccional al guardar canciones) y borrar la lista (`204`), verificando que solo las operaciones confirmadas producen exactamente un evento `PlaylistCreated` y un evento `PlaylistDeleted` con sus datos esperados.

**Acceptance Scenarios**:

1. **Given** un administrador autenticado (`ADMIN`) y Kafka disponible, **When** crea una lista válida mediante `POST /lists` (recibiendo `201`), **Then** una vez confirmada la transacción en base de datos se publica en Kafka exactamente un evento `PlaylistCreated` con identificador único de evento, tipo `PlaylistCreated`, nombre de la lista, cantidad de canciones (`songCount`) y marca temporal UTC, usando el nombre de la lista como clave de partición.
2. **Given** un administrador autenticado (`ADMIN`), una lista existente y Kafka disponible, **When** borra la lista mediante `DELETE /lists/{listName}` (recibiendo `204`), **Then** una vez confirmada la transacción de borrado se publica en Kafka exactamente un evento `PlaylistDeleted` con identificador único de evento, tipo `PlaylistDeleted`, nombre de la lista y marca temporal UTC, usando el nombre de la lista como clave de partición.
3. **Given** una operación rechazada antes o durante el guardado (`400` por estructura o género inválido, `401` sin token válido, `403` por rol `USER`, `404` al borrar lista inexistente o `409` por nombre duplicado), **When** termina la petición, **Then** no se publica ningún evento en Kafka.
4. **Given** un fallo a mitad de la transacción de persistencia (por ejemplo, al guardar una de las canciones o al eliminar la lista tras borrar sus canciones) que provoca reversión (rollback), **When** falla la operación, **Then** no se publica ningún evento en Kafka.
5. **Given** consultas de solo lectura (`GET /lists`, `GET /lists/{listName}`, `GET /genres`, `GET /lists/{listName}/recommendations`), **When** responden al usuario, **Then** no se publica ningún evento en Kafka.
6. **Given** una secuencia de creación, borrado y nueva creación con el mismo nombre de lista, **When** se completan las tres operaciones, **Then** los eventos `PlaylistCreated`, `PlaylistDeleted` y `PlaylistCreated` mantienen el orden causal para ese nombre de lista y cada uno tiene su propio identificador y marca temporal.

---

### User Story 2 - Consumir y registrar los eventos en el log de auditoría (Priority: P1)

Como auditor, quiero que un consumidor conectado a Kafka reciba los eventos publicados y los registre de forma estructurada en el log de la aplicación, para poder inspeccionar el rastro de creaciones y borrados sin acceder directamente al broker ni a la base de datos.

**Why this priority**: Completa el ciclo de trazabilidad exigido por el backlog ("un consumidor los registra en el log") y materializa la auditoría operativa dentro del propio servicio.

**Independent Test**: Publicar o provocar `PlaylistCreated` y `PlaylistDeleted` con Kafka en ejecución y verificar que el registro de auditoría de la aplicación contiene una entrada estructurada `INFO` por cada evento con su tipo, nombre de lista, identificador de evento, marca temporal y conteo de canciones cuando aplique.

**Acceptance Scenarios**:

1. **Given** el consumidor de auditoría activo y suscrito al tópico de eventos de listas, **When** llega un evento `PlaylistCreated` válido, **Then** el consumidor escribe en el log (nivel `INFO`) un registro estructurado de auditoría con tipo `PlaylistCreated`, `playlistName`, `songCount`, `eventId` y `occurredAt`, sin incluir credenciales ni identificadores numéricos internos de base de datos.
2. **Given** el consumidor de auditoría activo, **When** llega un evento `PlaylistDeleted` válido, **Then** el consumidor escribe en el log (nivel `INFO`) un registro estructurado de auditoría con tipo `PlaylistDeleted`, `playlistName`, `eventId` y `occurredAt`.
3. **Given** un mensaje mal formado, vacío, ilegible o con un tipo de evento desconocido en el tópico, **When** el consumidor lo procesa, **Then** registra una advertencia con el motivo resumido, descarta el mensaje inválido y continúa procesando con normalidad los eventos válidos posteriores sin reiniciar ni bloquear el consumidor.
4. **Given** cualquier operación de consumo y registro de eventos en Kafka, **When** se ejecuta bajo detección de llamadas bloqueantes (`BlockHound`), **Then** el consumo y la publicación se ejecutan sin bloquear hilos del event loop.

---

### User Story 3 - Mantener la API operativa con Kafka caído o lento (Priority: P1)

Como responsable del sistema y administrador de listas, quiero que la API arranque, atienda `POST /lists` y `DELETE /lists/{listName}` y mantenga saludables sus sondas aunque Kafka esté caído, lento o inaccesible, para que un fallo en la mensajería de auditoría nunca interrumpa la gestión de listas de reproducción.

**Why this priority**: La publicación de eventos es de mejor esfuerzo (`ADR-0001`, sección 6) y la historia exige explícitamente que, con Kafka caído, la petición no falle.

**Independent Test**: Arrancar la API sin Kafka o detener Kafka en caliente, ejecutar `POST /lists` (`201`), `DELETE /lists/{listName}` (`204`) y consultar `/actuator/health/liveness` y `/actuator/health/readiness`, comprobando respuestas exitosas dentro de un tiempo acotado y reanudación automática al levantar Kafka de nuevo.

**Acceptance Scenarios**:

1. **Given** Kafka apagado o inaccesible al arrancar la aplicación, **When** inicia la API, **Then** el arranque finaliza con normalidad y todos los endpoints quedan disponibles para atender peticiones.
2. **Given** Kafka caído, apagado o inaccesible durante la ejecución, **When** un administrador crea una lista (`POST /lists`) o la borra (`DELETE /lists/{listName}`), **Then** la operación persiste en base de datos, devuelve `201` o `204` respectivamente sin errores `5xx` y registra una advertencia estructurada indicando que no pudo publicarse el evento.
3. **Given** Kafka lento o con conexión colgada que supera el tiempo máximo de publicación configurable (2 segundos por defecto), **When** se intenta publicar `PlaylistCreated` o `PlaylistDeleted`, **Then** la espera de publicación se cancela al vencer el límite, queda registrada como advertencia y la petición HTTP responde con su resultado confirmado sin demorarse esperando a Kafka.
4. **Given** que Kafka estuvo caído durante peticiones anteriores y vuelve a estar disponible, **When** se crean o borran nuevas listas, **Then** la publicación y el consumo en el log de auditoría se reanudan automáticamente sin reiniciar la aplicación.
5. **Given** Kafka caído o inaccesible, **When** Kubernetes o un cliente consulta `/actuator/health/liveness` y `/actuator/health/readiness`, **Then** ambas sondas siguen respondiendo `200` (el estado de Kafka no tumba ni saca de servicio a la API).

---

### User Story 4 - Verificar publicación, consumo y caída de Kafka con Testcontainers (Priority: P1)

Como desarrollador y responsable de calidad, quiero pruebas de integración automáticas con un contenedor Kafka real (modo KRaft) en Testcontainers, para garantizar en local y en CI que la publicación post-commit, el registro en el log del consumidor y la tolerancia a la caída de Kafka funcionan realmente.

**Why this priority**: El backlog y el `ADR-0001` exigen pruebas obligatorias con Testcontainers que cubran los tres escenarios (publicación, consumo y Kafka caído) para poder dar por terminada HU-11.

**Independent Test**: Ejecutar `mvn verify` en un entorno con Docker disponible (sin servicios externos ni acceso a internet) y verificar que las pruebas de integración con Kafka real y las pruebas de arquitectura, cobertura y no bloqueo pasan en verde.

**Acceptance Scenarios**:

1. **Given** un entorno con Docker disponible, **When** se ejecutan las pruebas de integración con Kafka en Testcontainers para creación y borrado de listas, **Then** se verifica que `POST /lists` (`201`) publica `PlaylistCreated` y `DELETE /lists/{listName}` (`204`) publica `PlaylistDeleted` con los campos esperados, mientras que operaciones fallidas (`400`, `404`, `409` o rollback) no publican eventos.
2. **Given** el contenedor Kafka en marcha y el consumidor de auditoría activo, **When** se crean y borran listas durante la prueba, **Then** se verifica que el consumidor recibe ambos eventos y escribe los registros correspondientes en el log de auditoría.
3. **Given** el contenedor Kafka detenido o un destino inalcanzable, **When** se ejecutan `POST /lists` y `DELETE /lists/{listName}` junto con las sondas de salud, **Then** se verifica que las operaciones HTTP completan con `201`/`204` dentro del tiempo acotado, las sondas responden `200` y se registra la advertencia sin error `5xx`.
4. **Given** el conjunto completo de pruebas del backend (`mvn verify`), **When** se ejecuta en local y en GitHub Actions, **Then** todas las pruebas (unitarias, ArchUnit, BlockHound y Testcontainers) pasan con al menos 80 % de cobertura de líneas por módulo y cero violaciones de Checkstyle.

---

### User Story 5 - Desplegar Kafka en modo KRaft en Docker Compose y Kubernetes (Priority: P2)

Como evaluador y responsable de operaciones, quiero que Kafka en modo KRaft esté integrado en Docker Compose y en los manifiestos de Kubernetes del repositorio de infraestructura, para poder levantar y evaluar la trazabilidad por eventos sin configuraciones manuales ni ZooKeeper, cumpliendo las reglas de seguridad GitOps.

**Why this priority**: Completa la ejecución integrada en local (`docker compose up`) y en el clúster Kubernetes/Argo CD, mientras que el núcleo funcional y las pruebas con Testcontainers en el backend pueden desarrollarse y validarse primero.

**Independent Test**: Levantar el entorno con `docker compose`, crear y borrar una lista para observar los registros de auditoría en los logs de `api`, y detener el servicio `kafka` comprobando que `api` sigue operando; en Kubernetes, ejecutar `kustomize build` y Trivy sobre `k8s/` y verificar la sincronización saludable en Argo CD.

**Acceptance Scenarios**:

1. **Given** la composición de `quipux-playlist-infra` (`compose/compose.yaml`), **When** se levanta con `docker compose up` y se crea y borra una lista, **Then** la API publica los eventos en el servicio `kafka` (modo KRaft, sin ZooKeeper) y el consumidor los registra en los logs de `api`; si se detiene `kafka`, `api` sigue arrancando y atendiendo peticiones con normalidad.
2. **Given** la definición de `kafka` en `compose/compose.yaml`, **When** se inspecciona el archivo, **Then** usa una versión explícita fijada (sin `latest`), opera en la red interna sin exposición pública innecesaria de puertos y no bloquea el arranque de `api`.
3. **Given** los manifiestos Kustomize en `k8s/` de `quipux-playlist-infra`, **When** se incorporan el despliegue y servicio de Kafka en modo KRaft y las variables no secretas en `ConfigMap`, **Then** el contenedor de Kafka corre endurecido (usuario sin privilegios, sin escalada de privilegios, sistema de archivos raíz de solo lectura con almacenamiento temporal acotado, capacidades eliminadas, imagen explícita fijada y recursos acotados) y `api` conserva una única réplica.
4. **Given** el pipeline de CI de `quipux-playlist-infra`, **When** valida los cambios de `k8s/`, **Then** `kustomize build`, Trivy de configuración y Trivy de secretos finalizan en verde sin hallazgos `HIGH` o `CRITICAL`, y Argo CD sincroniza la aplicación en estado `Synced` y `Healthy`.

---

### Edge Cases

- **Reversión transaccional en creación o borrado**: si falla el guardado de la segunda canción al crear una lista o falla el borrado a mitad de la transacción, los cambios en base de datos se revierten y no se emite ningún evento.
- **Rechazos previos al commit**: si una petición falla por falta de token (`401`), falta de rol `ADMIN` (`403`), nombre o estructura inválida (`400`), género desconocido fuera del catálogo (`400`), lista duplicada (`409`) o lista inexistente al borrar (`404`), no se publica ningún evento.
- **Nombres con espacios y caracteres especiales**: nombres como `"Mis favoritas"`, `"Música ñ & rock+pop?"` se conservan textualmente en el campo `playlistName` del evento, como clave de partición y en el registro de auditoría (sin codificación URL).
- **Lista creada sin canciones**: `POST /lists` sin canciones publica `PlaylistCreated` con `songCount: 0`.
- **Creación, borrado y recreación con el mismo nombre**: cada operación confirmada emite su propio evento con distinto `eventId` y marca temporal; al compartir `playlistName` como clave de partición, conservan su orden causal por lista.
- **Kafka lento o conexión colgada**: tanto la obtención de metadatos como el envío tienen un tiempo máximo acotado (2 segundos por defecto, configurable) para evitar los bloqueos prolongados por defecto del cliente cuando el broker no responde.
- **Mensajes mal formados o de esquema desconocido en el tópico**: un mensaje vacío, JSON corrupto o tipo de evento no reconocido se registra como advertencia y se omite sin bloquear la partición ni detener el consumidor.
- **Entrega repetida de un mismo evento (at-least-once)**: si el consumidor recibe un evento duplicado tras un reequilibrio o reinicio, registra la entrada correspondiente con su `eventId` sin fallar ni alterar estado de negocio.
- **Pérdida asumida en modo mejor esfuerzo**: si Kafka falla después del commit en base de datos, el evento no se almacena en una tabla outbox ni se reintenta indefinidamente; la respuesta HTTP no se revierte y la incidencia queda en el log de advertencias.
- **Eventos desactivados por configuración o ejecución local sin Kafka**: si la publicación/consumo de eventos está desactivada por configuración o no hay broker disponible en local, la API arranca y opera igual que antes de HU-11 sin errores ni bloqueo.
- **Configuración inválida cuando los eventos están habilitados**: si los eventos están habilitados y el tiempo máximo de publicación es menor o igual a cero, o el nombre del tópico o grupo de consumo está vacío, la aplicación falla al arrancar indicando el parámetro inválido.
- **Sondas de salud frente a caída de Kafka**: `/actuator/health/liveness` y `/actuator/health/readiness` responden `200` aunque Kafka esté caído o desconectado, para evitar reinicios o retirada de tráfico de la API.
- **Seguridad y ausencia de secretos**: los eventos y registros de auditoría nunca incluyen contraseñas, tokens JWT ni claves; cualquier credencial opcional se inyecta exclusivamente por variable de entorno y nunca reside en Git.

## Requirements *(mandatory)*

### Functional Requirements

**Publicación post-commit y contrato de eventos**

- **FR-001**: `POST /lists` y `DELETE /lists/{listName}` conservan exactamente los contratos HTTP, códigos de respuesta y reglas de autorización de HU-02, HU-03 y HU-04 (`201` + `Location` en creación, `204` en borrado, `ProblemDetail` en errores).
- **FR-002**: Tras confirmar exitosamente la persistencia de una creación de lista (`POST /lists`), la aplicación publica un evento `PlaylistCreated` hacia Kafka usando el nombre de la lista (`playlistName`) como clave de partición.
- **FR-003**: Tras confirmar exitosamente la persistencia del borrado de una lista (`DELETE /lists/{listName}`), la aplicación publica un evento `PlaylistDeleted` hacia Kafka usando el nombre de la lista (`playlistName`) como clave de partición.
- **FR-004**: La publicación de eventos ocurre estrictamente **después** de que la transacción de persistencia haya tenido éxito; ninguna operación rechazada (`400`, `401`, `403`, `404`, `409`), revertida por fallo de base de datos o de solo lectura (`GET`) publica eventos.
- **FR-005**: Todos los eventos se publican en formato JSON en un tópico configurable (`playlist.events` por defecto) e incluyen: identificador único del evento (`eventId`), tipo de evento (`eventType`: `PlaylistCreated` o `PlaylistDeleted`), nombre exacto de la lista (`playlistName`) y marca temporal UTC (`occurredAt`); `PlaylistCreated` incluye además `songCount` (entero ≥ 0). No se exponen identificadores numéricos internos de base de datos ni secretos.

**Consumidor de auditoría**

- **FR-006**: Un adaptador de entrada dedicado consume los eventos del tópico configurado usando un grupo de consumidores configurable (`playlist-audit` por defecto).
- **FR-007**: Por cada evento `PlaylistCreated` y `PlaylistDeleted` válido recibido, el consumidor registra de forma estructurada en nivel `INFO` una entrada de auditoría que contiene al menos `eventType`, `playlistName`, `eventId`, `occurredAt` y, en creaciones, `songCount`.
- **FR-008**: Ante mensajes corruptos, vacíos, JSON ilegibles o tipos de evento desconocidos en el tópico, el consumidor registra una advertencia estructurada con el motivo resumido, descarta el mensaje inválido y continúa consumiendo los mensajes posteriores sin interrupción.

**Resiliencia, mejor esfuerzo, observabilidad y salud**

- **FR-009**: La publicación de eventos opera con semántica de mejor esfuerzo: si Kafka está apagado, inalcanzable, rechazando conexiones o devuelve error, `POST /lists` (`201`) y `DELETE /lists/{listName}` (`204`) completan igualmente sin errores `5xx` ni reversión de la lista.
- **FR-010**: La publicación tiene un tiempo máximo total acotado configurable (**2 segundos** por defecto) que cubre la resolución de metadatos y el envío; superado ese límite, el intento se cancela de forma transparente, se registra como advertencia y la respuesta HTTP continúa sin esperar indefinidamente.
- **FR-011**: Si Kafka está caído o inaccesible al iniciar la aplicación, la API arranca con normalidad sin bloquear ni abortar el inicio.
- **FR-012**: Cuando Kafka se recupera tras una caída o corte temporal, tanto el productor como el consumidor reanudan automáticamente la publicación y el registro de auditoría sin reiniciar la API.
- **FR-013**: La indisponibilidad de Kafka nunca hace fallar las sondas `/actuator/health/liveness` ni `/actuator/health/readiness`, que siguen respondiendo `200`.
- **FR-014**: La aplicación registra de forma estructurada los fallos y tiempos agotados al publicar o consumir en Kafka (nivel `WARN` con motivo resumido, sin credenciales ni payloads sensibles) y los eventos emitidos/consumidos en auditoría.

**Arquitectura hexagonal, no bloqueo y configuración**

- **FR-015**: Siguiendo la arquitectura hexagonal del proyecto (`ADR-0001`), los modelos de evento de dominio (`PlaylistCreated`, `PlaylistDeleted`) residen en `domain` libres de frameworks; el puerto de salida de publicación de eventos reside en `application` y es invocado desde los casos de uso de creación y borrado tras la confirmación de persistencia; los adaptadores productor y consumidor de Kafka residen exclusivamente en `infrastructure`, manteniendo todas las reglas de `ArchUnit` en verde.
- **FR-016**: Tanto la publicación como el consumo de eventos se ejecutan sin bloquear los hilos del event loop de la aplicación, verificado automáticamente con `BlockHound`.
- **FR-017**: La activación de eventos (`enabled`), los servidores bootstrap de Kafka, el nombre del tópico, el identificador del grupo consumidor y el tiempo máximo de publicación son configurables por propiedades/variables de entorno sin modificar código, con valores por defecto seguros para ejecución local.
- **FR-018**: Si los eventos están habilitados y el tiempo máximo de publicación es menor o igual a cero, o el tópico o grupo consumidor quedan vacíos o en blanco, la aplicación falla al arrancar con un mensaje claro.
- **FR-019**: La introducción de eventos en Kafka no introduce regresiones en el comportamiento, contratos ni pruebas de HU-02, HU-03, HU-04, HU-06, HU-09, HU-10 ni HU-12.

**Pruebas automáticas con Testcontainers**

- **FR-020**: Existen pruebas automáticas de integración con un contenedor Kafka real (modo KRaft) mediante Testcontainers (sin servicios externos ni acceso a internet) que cubren obligatoriamente:
  1. Publicación post-commit: `POST /lists` (`201`) publica `PlaylistCreated` y `DELETE /lists/{listName}` (`204`) publica `PlaylistDeleted` con los datos esperados, mientras que peticiones fallidas o con reversión transaccional no publican eventos.
  2. Consumo de auditoría: el consumidor recibe los eventos publicados y escribe las entradas esperadas en el log de auditoría.
  3. Kafka caído o inaccesible: con el broker detenido o inaccesible, `POST /lists` (`201`), `DELETE /lists/{listName}` (`204`) y las sondas de salud (`200`) siguen respondiendo dentro del tiempo acotado sin errores del servidor.
- **FR-021**: Las pruebas con Testcontainers se ejecutan automáticamente dentro de `mvn verify` en local y en el pipeline de CI de GitHub Actions.
- **FR-022**: El proyecto backend mantiene cobertura mínima del 80 % de líneas por módulo en JaCoCo, cero violaciones de Checkstyle, todas las reglas de ArchUnit en verde, cero bloqueos en BlockHound y cero hallazgos críticos o altos en Qodana y Trivy.

**Infraestructura y documentación**

- **FR-023**: En `quipux-playlist-infra`, `compose/compose.yaml` añade el servicio `kafka` en modo KRaft (sin ZooKeeper) con versión explícita fijada (sin `latest`), en la red interna sin exposición pública innecesaria de puertos, y entrega a `api` la configuración de conexión sin impedir que `api` arranque ni responda si Kafka está caído.
- **FR-024**: Con `docker compose up` sobre `compose/compose.yaml`, un evaluador puede crear y borrar listas comprobando los eventos registrados en los logs de auditoría de `api` y verificar que al detener `kafka` la API sigue respondiendo.
- **FR-025**: En `quipux-playlist-infra`, `k8s/` añade los manifiestos de Kafka en modo KRaft (`Deployment` y `Service`) y las variables no secretas en `ConfigMap`, con contenedor endurecido (usuario no root, sin escalada de privilegios, sistema de archivos raíz de solo lectura con volúmenes temporales `emptyDir` para directorios escribibles, capacidades `ALL` eliminadas, perfil seccomp `RuntimeDefault`, imagen explícita fijada y límites de recursos), manteniendo `api` en 1 réplica y sin secretos reales en Git.
- **FR-026**: Los manifiestos actualizados de `k8s/` generan una salida válida con `kustomize build`, pasan Trivy de configuración y Trivy de secretos sin hallazgos `HIGH` o `CRITICAL`, y quedan listos para sincronización automática por Argo CD en estado `Synced` y `Healthy`.
- **FR-027**: El README del backend documenta las nuevas variables de configuración de Kafka/eventos y su verificación con Testcontainers; el README de infra documenta el servicio Kafka (KRaft) en Compose y su inclusión en Kubernetes.
- **FR-028**: Al completar la historia, se actualizan los criterios de HU-11 en `docs/backlog.md` y se anota la concreción técnica correspondiente en `docs/adr/0001-arquitectura-api-listas-reproduccion.md`.
- **FR-029**: Ninguno de los repositorios, imágenes, manifiestos ni registros contiene secretos o credenciales reales.

### Key Entities *(include if feature involves data)*

- **Evento de dominio de lista (`PlaylistCreated` / `PlaylistDeleted`)**: registro inmutable de un cambio confirmado en una lista; contiene identificador único (`eventId`), tipo (`eventType`), nombre de la lista (`playlistName`), marca temporal UTC (`occurredAt`) y, en creación, número de canciones (`songCount`).
- **Tópico de eventos de listas**: canal de mensajería en Kafka (`playlist.events` por defecto) particionado por `playlistName` para preservar el orden causal de los eventos de una misma lista.
- **Consumidor de auditoría**: suscriptor independiente dentro del grupo de consumo (`playlist-audit` por defecto) encargado de leer los eventos del tópico y volcarlos al registro de auditoría.
- **Registro de auditoría**: línea de log estructurada emitida por el consumidor con los atributos trazables del evento recibido.
- **Configuración de mensajería Kafka**: parámetros operativos que controlan activación, dirección de conexión al broker, tópico, grupo consumidor y tiempo máximo de publicación.
- **Servicio Kafka (KRaft) de infraestructura**: instancia única efímera de Kafka sin ZooKeeper desplegada en Docker Compose y Kubernetes/Kustomize como bus de eventos no crítico para la disponibilidad de la API.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El **100 %** de las creaciones (`201`) y borrados (`204`) confirmados de listas con el broker de eventos disponible publican su evento correspondiente y producen una entrada verificable en el registro de auditoría en menos de **5 segundos**.
- **SC-002**: El **0 %** de las peticiones rechazadas (`400`, `401`, `403`, `404`, `409`), operaciones revertidas por fallo en base de datos o consultas de lectura publican eventos.
- **SC-003**: Con el broker de eventos completamente detenido o inaccesible, el **100 %** de las operaciones autorizadas de creación (`201`) y borrado (`204`) responden con éxito funcional en menos de **3 segundos** sin errores del servidor.
- **SC-004**: Con el broker de eventos caído, el **100 %** de las comprobaciones a `/actuator/health/liveness` y `/actuator/health/readiness` responden `200` sin degradar la disponibilidad del servicio.
- **SC-005**: El **100 %** de los criterios de aceptación de HU-11 en `docs/backlog.md` queda cubierto por pruebas automáticas de integración (publicación, consumo y broker caído) y validaciones de infraestructura.
- **SC-006**: La verificación completa del backend finaliza en verde con cobertura ≥ **80 %** de líneas por módulo, **0** violaciones de estilo, **0** violaciones de reglas de arquitectura y **0** llamadas bloqueantes detectadas en el event loop.
- **SC-007**: Los manifiestos de despliegue generan salida válida sin errores y el análisis de configuración y secretos reporta **0** hallazgos `HIGH` o `CRITICAL`.
- **SC-008**: Los repositorios, manifiestos y registros de ejecución contienen **0** secretos o credenciales reales.

## Assumptions

- **Ubicación de la historia y ramas**: siguiendo el criterio usado en HU-04, HU-10 y HU-12, la especificación, el plan y las tareas de HU-11 viven en `quipux-playlist-backend/specs/11-kafka-events/`, y los cambios de código e infraestructura se implementan en ramas `feature/11-kafka-events` de `quipux-playlist-backend` y `quipux-playlist-infra`.
- **Decisiones ya tomadas (`ADR-0001`, secciones 2, 6, 7, 11 y 13)**: eventos `PlaylistCreated` y `PlaylistDeleted` publicados después del commit mediante puerto de salida; consumidor simple de auditoría que registra en el log; publicación de mejor esfuerzo (sin patrón *outbox*); si Kafka no está disponible la petición HTTP no falla; Kafka en modo KRaft (sin ZooKeeper) en Compose y Kubernetes; y pruebas de integración obligatorias con Testcontainers incluyendo el caso de Kafka caído.
- **Valores por defecto y carga útil fijados en esta especificación (revisables antes del plan)**:
  - Un único tópico (`playlist.events` por defecto) con `playlistName` como clave de partición para preservar el orden causal entre creación, borrado y recreación de una misma lista.
  - Grupo consumidor `playlist-audit` por defecto.
  - Atributos de evento (`eventId`, `eventType`, `playlistName`, `occurredAt`, más `songCount` en `PlaylistCreated`) sin incluir el usuario autenticado (`actor`), para no alterar los contratos de entrada existentes de creación y borrado de listas; la inclusión del actor queda abierta como evolución futura compatible.
  - Tiempo máximo de publicación acotado a **2 segundos** por defecto (configurable) para acotar la espera en metadatos y envío cuando Kafka está caído o lento.
- **Sin cambios en el contrato HTTP ni en el frontend**: `POST /lists` y `DELETE /lists/{listName}` mantienen exactamente sus peticiones, cabeceras, códigos y cuerpos; por tanto, no requiere cambios en OpenAPI, colección de Postman ni `quipux-playlist-frontend`.
- **Broker único efímero en KRaft**: al tratarse de un bus de auditoría de mejor esfuerzo para un entorno de evaluación con una sola réplica de `api`, Kafka se despliega como instancia única efímera en modo KRaft sin persistencia durable en volúmenes externos, sin clúster multibroker ni Schema Registry.
- **Dependencias**: HU-02 (gestión de listas), HU-03 (seguridad), HU-04 (géneros), HU-07 (Docker Compose), HU-08 (CI/Trivy/Qodana), HU-09 (Kubernetes/Argo CD) y HU-10 (Redis) implementadas; Docker disponible para Testcontainers en local y en CI.
- **Fuera de alcance**: patrón *Transactional Outbox* o entrega garantizada *exactly-once*, Schema Registry / Avro / Protobuf, tópicos de mensajes fallidos (DLQ) o endpoints de reprocesamiento, persistencia del log de auditoría en base de datos o SIEM externo, clúster Kafka multibroker y emisión de eventos para consultas de lectura, login, géneros o recomendaciones IA.
