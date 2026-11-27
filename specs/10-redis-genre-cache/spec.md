# Feature Specification: Caché de géneros en Redis

**Feature Branch**: `feature/10-redis-genre-cache`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "HU-10: como responsable del sistema, quiero cachear los géneros, para reducir llamadas a Spotify." Fuentes: `docs/backlog.md` (HU-10) y `docs/adr/0001-arquitectura-api-listas-reproduccion.md` (secciones 2, 5, 7 y 11). Contratos vigentes: `specs/04-genres` y `specs/02-playlist-management`.

**Repositorios afectados**: backend (puerto y adaptador reactivo de caché, integración en el caso de uso de géneros, configuración, pruebas con Testcontainers y documentación) e infra (servicio Redis en Docker Compose, manifiestos en Kubernetes y documentación).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reutilizar los géneros de Spotify desde Redis durante el TTL (Priority: P1)

Como responsable del sistema, quiero que el catálogo de géneros obtenido desde Spotify se almacene temporalmente en Redis durante un tiempo de vida (TTL) configurable, para que las consultas posteriores y las validaciones de género al crear listas reutilicen el catálogo guardado sin volver a llamar a Spotify.

**Why this priority**: Es el propósito central de la historia: reducir llamadas externas repetitivas a Spotify manteniendo intacto el comportamiento funcional de `GET /genres` y `POST /lists`.

**Independent Test**: Con Redis disponible y Spotify simulado, consultar `GET /genres` dos veces seguidas dentro del TTL, crear una lista con un género válido y verificar que Spotify recibió una sola llamada; después de expirar el TTL, consultar de nuevo y verificar que Spotify recibe una segunda llamada y la caché se renueva.

**Acceptance Scenarios**:

1. **Given** la caché vacía en Redis y Spotify disponible, **When** un usuario autorizado (`USER` o `ADMIN`) consulta `GET /genres`, **Then** la API consulta Spotify, guarda en Redis el catálogo saneado con el TTL configurado (24 horas por defecto) y responde `200` con `generos` y `origen` `SPOTIFY`.
2. **Given** un catálogo válido guardado en Redis dentro de su TTL, **When** un usuario (`USER` o `ADMIN`) consulta nuevamente `GET /genres`, **Then** la API responde `200` con los mismos géneros y `origen` `SPOTIFY` sin realizar ninguna llamada a Spotify.
3. **Given** un catálogo válido guardado en Redis dentro de su TTL, **When** un administrador (`ADMIN`) crea una lista (`POST /lists`) con canciones que declaran género, **Then** la validación del género utiliza el catálogo guardado en Redis sin llamar a Spotify.
4. **Given** un catálogo válido guardado en Redis dentro de su TTL y Spotify temporalmente caído, **When** se consulta `GET /genres` o se valida el género en `POST /lists`, **Then** la API responde utilizando el catálogo vigente en Redis con `origen` `SPOTIFY` sin depender de Spotify.
5. **Given** que el TTL de la entrada en Redis ha expirado y Spotify está disponible, **When** se consulta `GET /genres` (o se valida un género en `POST /lists`), **Then** la API vuelve a consultar Spotify, entrega el catálogo actualizado con `origen` `SPOTIFY` y lo almacena en Redis con un nuevo TTL.
6. **Given** una petición sin token válido (`401`), una creación de lista sin rol `ADMIN` (`403`), una petición rechazada por validaciones previas de estructura (`400`) o una lista sin géneros declarados, **When** llega a la API, **Then** no se consulta ni Redis ni Spotify.

---

### User Story 2 - No cachear la lista local de respaldo (Priority: P1)

Como responsable del sistema, quiero que la lista local de géneros devuelta cuando Spotify falla nunca se guarde en Redis, para que el sistema pueda recuperarse inmediatamente en cuanto Spotify vuelva a estar disponible sin quedar atrapado sirviendo el respaldo durante todo el TTL.

**Why this priority**: Evita degradaciones prolongadas tras fallos transitorios de Spotify y es un criterio explícito de aceptación de HU-10 en el backlog.

**Independent Test**: Con Spotify fallando y Redis operativo, consultar `GET /genres`, confirmar respuesta `200` con `origen` `LOCAL` y que no hay entrada en Redis; restaurar Spotify de inmediato y volver a consultar `GET /genres`, confirmando respuesta `200` con `origen` `SPOTIFY` y guardado posterior en Redis.

**Acceptance Scenarios**:

1. **Given** Redis operativo y Spotify fallando (credenciales ausentes o rechazadas, error HTTP `4xx`/`5xx`, tiempo agotado, fallo de conexión, contenido ilegible o lista sin géneros válidos), **When** se consulta `GET /genres`, **Then** la API responde `200` con la lista local y `origen` `LOCAL`, y no guarda nada en Redis.
2. **Given** una consulta inmediatamente anterior respondida con `origen` `LOCAL` por fallo de Spotify, **When** Spotify vuelve a estar disponible y se realiza una nueva consulta `GET /genres` (sin esperar ningún TTL), **Then** la API consulta Spotify, responde `200` con `origen` `SPOTIFY` y almacena ese catálogo en Redis.
3. **Given** una entrada previa en Redis cuyo TTL expiró y Spotify fallando en ese instante, **When** se consulta `GET /genres`, **Then** la API responde `200` con `origen` `LOCAL` y deja Redis sin catálogo cacheado hasta que Spotify vuelva a responder con éxito.
4. **Given** que Spotify falla durante una creación de lista (`POST /lists`) con géneros sin caché vigente, **When** la validación recurre a la lista local, **Then** la lista local se usa solo para esa petición y no se guarda en Redis.

---

### User Story 3 - Seguir respondiendo con Redis caído o lento (Priority: P1)

Como responsable del sistema, quiero que la API arranque y atienda todas sus operaciones con normalidad aunque Redis esté caído, inalcanzable o responda lento, para que un fallo en la capa de caché nunca deje la aplicación sin servicio.

**Why this priority**: Una caché es una optimización, nunca un punto único de fallo; si Redis cae o se degrada, `GET /genres` y `POST /lists` deben seguir funcionando como en HU-04.

**Independent Test**: Arrancar la API sin Redis o detener Redis en caliente, ejecutar `GET /genres`, `POST /lists` y las sondas de salud, comprobando que todo sigue respondiendo dentro del tiempo esperado y que al reactivar Redis el cacheo se reanuda solo.

**Acceptance Scenarios**:

1. **Given** Redis apagado o inaccesible al iniciar la aplicación, **When** arranca la API, **Then** el arranque completa con normalidad y el servicio queda listo para atender peticiones.
2. **Given** Redis caído o inaccesible durante la ejecución, **When** se consulta `GET /genres` o se crea una lista con género (`POST /lists`), **Then** la API ignora el fallo de caché, consulta Spotify (respondiendo `SPOTIFY` si está disponible o `LOCAL` si también falla) y entrega una respuesta válida sin errores `5xx`.
3. **Given** Redis lento o bloqueado que supera el tiempo máximo de operación de caché (1 segundo por defecto, configurable), **When** se intenta leer o escribir en Redis, **Then** la operación de caché se cancela al vencer su tiempo límite, queda registrada como advertencia y la petición continúa sin demorarse esperando a Redis.
4. **Given** que la lectura en Redis falló o estaba vacía, Spotify devolvió géneros válidos y Redis falla al intentar guardar el resultado, **When** termina la operación, **Then** el usuario recibe igualmente `200` con los géneros de Spotify (`origen` `SPOTIFY`) y el error de escritura queda registrado como advertencia.
5. **Given** que Redis estuvo caído durante peticiones anteriores y vuelve a estar disponible, **When** llegan nuevas consultas dentro de la operación normal, **Then** la API reanuda automáticamente la lectura y escritura en Redis sin necesidad de reiniciar.
6. **Given** Redis caído o inaccesible, **When** Kubernetes o un cliente consulta `/actuator/health/liveness` y `/actuator/health/readiness`, **Then** ambas sondas siguen respondiendo `200` (el estado de Redis no tumba ni saca de servicio a la API).
7. **Given** cualquier operación de lectura o escritura contra Redis, **When** se ejecuta bajo detección de llamadas bloqueantes (`BlockHound`), **Then** toda la interacción se realiza de forma reactiva y no bloqueante sin bloquear hilos del event loop.

---

### User Story 4 - Verificar automáticamente la caché con Redis real mediante Testcontainers (Priority: P1)

Como desarrollador y responsable de calidad, quiero pruebas de integración automáticas con un contenedor Redis real y Spotify simulado, para garantizar en cada compilación y en CI que el acierto de caché, la expiración del TTL, la exclusión del respaldo y la tolerancia a la caída de Redis funcionan realmente.

**Why this priority**: La historia exige explícitamente pruebas con Testcontainers para los escenarios críticos de caché; sin ellas la historia no puede darse por terminada según el backlog y el ADR-0001.

**Independent Test**: Ejecutar la verificación completa del backend (`mvn verify`) en un entorno con Docker y comprobar que las pruebas de integración levantan Redis con Testcontainers y pasan sin acceso a internet ni credenciales reales de Spotify.

**Acceptance Scenarios**:

1. **Given** un entorno con Docker disponible y sin acceso a Spotify real, **When** se ejecutan las pruebas de integración con Testcontainers y WireMock para el escenario de acierto, **Then** se verifica que la primera consulta llama a Spotify y la segunda obtiene los géneros desde Redis sin llamar a Spotify.
2. **Given** un TTL corto configurado para la prueba de expiración y un catálogo ya cacheado en el contenedor Redis, **When** transcurre el TTL y se ejecuta una nueva consulta, **Then** se verifica que la consulta vuelve a llamar a Spotify y actualiza el contenido en Redis.
3. **Given** el contenedor Redis en marcha y Spotify simulado devolviendo error, **When** se consulta `GET /genres` y luego se restaura Spotify simulado, **Then** se verifica que la respuesta `LOCAL` no quedó cacheada y que la siguiente petición vuelve a llamar a Spotify y sí cachea la respuesta `SPOTIFY`.
4. **Given** el contenedor Redis detenido o inaccesible, **When** se ejecutan peticiones contra `GET /genres` (tanto con Spotify simulado operativo como caído), **Then** se verifica que la API sigue respondiendo `200` con el catálogo esperado y sin errores del servidor.
5. **Given** el conjunto completo de pruebas del backend (`mvn verify`), **When** se ejecuta en local y en el pipeline de CI, **Then** todas las pruebas (unitarias, ArchUnit, BlockHound y Testcontainers) pasan con al menos 80 % de cobertura de líneas por módulo y sin violaciones de Checkstyle.

---

### User Story 5 - Desplegar Redis en Docker Compose y Kubernetes (Priority: P2)

Como evaluador y responsable de operaciones, quiero que Redis esté integrado en Docker Compose y en los manifiestos de Kubernetes del repositorio de infraestructura, para poder evaluar y desplegar la caché completa sin configuraciones manuales y cumpliendo las reglas de seguridad de GitOps.

**Why this priority**: Completa la integración operativa en local (Compose) y en clúster (Kubernetes/Argo CD), aunque la lógica y verificación principal de caché en el backend pueden desarrollarse y probarse de forma independiente primero.

**Independent Test**: Levantar el entorno con `docker compose` junto con WireMock para comprobar la caché de extremo a extremo y su tolerancia al detener Redis; en Kubernetes, ejecutar `kustomize build` y los análisis de Trivy sobre `k8s/` y verificar el despliegue saludable en el clúster local.

**Acceptance Scenarios**:

1. **Given** la composición de `quipux-playlist-infra` (`compose/compose.yaml`), **When** se levanta junto con `compose.wiremock.yaml` y se consulta `GET /genres` varias veces seguidas, **Then** la primera llamada llega a WireMock, las siguientes se sirven desde Redis y al detener el servicio de Redis la API sigue respondiendo.
2. **Given** el servicio Redis definido en `compose/compose.yaml`, **When** se inspecciona su definición, **Then** usa una versión explícita fijada (sin `latest`), opera en la red interna sin exponer puertos públicos innecesarios y no impide que `api` arranque si Redis tarda o falla.
3. **Given** los manifiestos Kustomize en `k8s/` de `quipux-playlist-infra`, **When** se incorpora el despliegue y servicio de Redis y se ejecuta `kustomize build`, **Then** se generan manifiestos válidos con contenedor Redis endurecido (usuario sin privilegios, sin escalada de privilegios, sistema de archivos raíz de solo lectura, imagen explícita fijada y recursos acotados) y configuración no secreta en `ConfigMap`.
4. **Given** el pipeline de CI de `quipux-playlist-infra`, **When** valida los cambios de `k8s/`, **Then** `kustomize build`, Trivy de configuración y Trivy de secretos terminan en verde sin hallazgos `HIGH` o `CRITICAL`, y Argo CD sincroniza la aplicación en estado `Synced` y `Healthy`.

---

### Edge Cases

- **Entradas inválidas devueltas por Spotify**: los géneros vacíos, en blanco, repetidos o de más de 100 caracteres siguen descartándose antes de construir el catálogo; solo el catálogo válido y saneado se guarda en Redis. Si tras el saneado no queda ningún género, se responde `LOCAL` y no se guarda nada en Redis.
- **Entrada corrupta, ilegible o vacía en Redis**: si el valor almacenado en Redis no puede leerse, está vacío o no produce un catálogo válido, se trata como ausencia de caché, se registra una advertencia y se consulta Spotify; si Spotify responde con éxito, la entrada válida sobrescribe el valor defectuoso.
- **Fallo de escritura tras lectura fallida**: si Redis estaba accesible (o expirado) en la lectura pero falla o está en modo solo lectura al escribir el resultado de Spotify, la API devuelve igualmente `200` con `origen` `SPOTIFY` y registra la incidencia como advertencia.
- **Peticiones concurrentes con caché fría**: si varias peticiones llegan simultáneamente mientras la caché está vacía, todas pueden consultar a Spotify y escribir el mismo catálogo; todas responden `200` `SPOTIFY` sin errores ni esperas bloqueantes y las peticiones posteriores aprovechan la entrada cacheada.
- **Validación de género en `POST /lists` frente a caché**: solo consulta el catálogo (y por tanto Redis) si las validaciones previas (autenticación `401`, rol `403`, nombre y estructura `400`) pasan y al menos una canción declara un `genero` no vacío; si la caché tiene el catálogo de Spotify vigente, la validación reutiliza esa entrada sin llamar a Spotify.
- **Redis lento o conexión colgada**: tanto la lectura como la escritura tienen un tiempo máximo total acotado (1 segundo por defecto, configurable) para no sumar latencia inaceptable ni afectar el tiempo máximo de Spotify ni el límite de respuesta de la API.
- **Configuración inválida de TTL o timeout de caché**: si el TTL o el tiempo máximo de caché se configuran con valor cero o negativo cuando la caché está habilitada, la aplicación falla al arrancar explicando el parámetro inválido.
- **Caché desactivada por configuración o ejecución local sin Redis**: si la caché se desactiva explícitamente o no hay instancia Redis local al ejecutar el backend sin Docker, la API funciona exactamente igual que en HU-04 sin errores de arranque ni fallos en las peticiones.
- **Reinicio o vaciado de Redis**: como la caché es temporal, si Redis se reinicia o se vacía, las entradas se pierden sin impacto funcional y la siguiente consulta vuelve a poblar la caché desde Spotify.
- **Sondas de salud frente a caída de Redis**: las comprobaciones automáticas de salud de Kubernetes (`/actuator/health/liveness` y `/actuator/health/readiness`) no marcan el pod como no saludable si Redis está caído, para evitar reinicios o retirada de tráfico innecesarios.
- **Credenciales opcionales de Redis**: si en algún entorno se configura contraseña para Redis, se inyecta únicamente por variable de entorno, nunca con valores reales en Git ni expuesta en los registros de la aplicación.

## Requirements *(mandatory)*

### Functional Requirements

**Caché de géneros y TTL**

- **FR-001**: `GET /genres` conserva exactamente el contrato de HU-04 (`200` con `generos` y `origen` `SPOTIFY` o `LOCAL`, accesible para `USER` y `ADMIN`, `401` sin token válido).
- **FR-002**: Cuando Spotify devuelve un catálogo válido (no vacío tras el saneado de HU-04), la API lo almacena en Redis asociado a un tiempo de vida (TTL) configurable, fijado en **24 horas** por defecto.
- **FR-003**: Mientras exista una entrada válida en Redis dentro de su TTL, tanto `GET /genres` como la validación de géneros en `POST /lists` utilizan el catálogo cacheado con `origen` `SPOTIFY` sin realizar llamadas a Spotify.
- **FR-004**: Una vez expirado el TTL en Redis, la siguiente consulta que necesite el catálogo vuelve a consultar Spotify y, si tiene éxito, guarda el nuevo catálogo en Redis con un nuevo TTL.
- **FR-005**: Mientras la entrada en Redis siga vigente dentro de su TTL, una caída temporal de Spotify no impide responder con el catálogo cacheado (`origen` `SPOTIFY`).

**Lista de respaldo y recuperación**

- **FR-006**: Cuando la obtención desde Spotify falla o no hay credenciales configuradas y no existe entrada vigente en Redis, la API responde con la lista local (`origen` `LOCAL`) y **nunca** guarda dicha lista local en Redis.
- **FR-007**: Si Spotify devuelve un contenido vacío o sin ningún género válido tras el saneado, tampoco se almacena nada en Redis y se responde con `origen` `LOCAL`.
- **FR-008**: Tras una respuesta con `origen` `LOCAL`, la siguiente petición que requiera el catálogo vuelve a intentar la consulta contra Spotify de inmediato, sin esperar a ningún TTL.

**Resiliencia, observabilidad y salud**

- **FR-009**: Si Redis está caído, apagado, inalcanzable o devuelve error al conectar, leer o escribir, la aplicación arranca con normalidad y sigue atendiendo `GET /genres` y `POST /lists` consultando directamente el origen (Spotify o respaldo local).
- **FR-010**: Tanto la lectura como la escritura en Redis tienen un tiempo máximo total acotado configurable (**1 segundo** por defecto). Superado ese límite, la operación de caché se descarta de forma transparente y el flujo continúa sin error hacia el usuario.
- **FR-011**: Cuando Redis vuelve a estar disponible tras una caída o corte temporal, el almacenamiento y lectura en caché se reanudan automáticamente en las siguientes peticiones sin reiniciar la aplicación.
- **FR-012**: La indisponibilidad de Redis nunca provoca que las sondas `/actuator/health/liveness` ni `/actuator/health/readiness` fallen ni devuelvan un estado distinto de `200`.
- **FR-013**: La aplicación registra de forma estructurada los fallos o tiempos agotados de Redis (como advertencia con el motivo resumido, sin credenciales ni secretos) y distingue en depuración los aciertos de caché frente a las consultas al proveedor.

**Configuración, arquitectura y no bloqueo**

- **FR-014**: Siguiendo la arquitectura hexagonal del proyecto (`ADR-0001`), la capacidad de caché de géneros se define mediante un puerto de salida en `application` y su implementación con Redis reside exclusivamente en `infrastructure`, manteniendo `domain` libre de frameworks y todas las reglas de `ArchUnit` en verde.
- **FR-015**: Toda lectura y escritura en Redis se ejecuta mediante un cliente reactivo y no bloqueante, sin llamadas bloqueantes en el event loop, verificado automáticamente con `BlockHound`.
- **FR-016**: La activación de la caché, los parámetros de conexión a Redis (host, puerto, contraseña opcional), el TTL de géneros y el tiempo máximo de operación de caché son configurables sin modificar código, con valores por defecto seguros para ejecución local.
- **FR-017**: Si la caché está habilitada y el TTL o el tiempo máximo de operación de caché se configuran con valor cero o negativo, la aplicación falla al arrancar con un mensaje claro.
- **FR-018**: Cualquier contraseña o credencial opcional de Redis se toma exclusivamente de variables de entorno, sin secretos ni contraseñas reales en el repositorio.
- **FR-019**: La introducción de la caché en Redis no introduce regresiones en el comportamiento, contratos ni pruebas de HU-02, HU-03, HU-04, HU-06, HU-09 ni HU-12.

**Pruebas automáticas con Testcontainers**

- **FR-020**: Existen pruebas automáticas de integración con un contenedor Redis real mediante Testcontainers y Spotify simulado con WireMock (sin credenciales reales ni acceso a internet) que cubren obligatoriamente:
  1. Acierto de caché: la segunda consulta dentro del TTL responde `SPOTIFY` sin llamar a Spotify.
  2. Expiración del TTL: tras expirar un TTL corto configurado para la prueba, la siguiente consulta vuelve a llamar a Spotify y refresca la caché.
  3. Exclusión de la lista de respaldo: un fallo de Spotify responde `LOCAL` sin cachearlo, y la siguiente petición con Spotify disponible vuelve a llamar a Spotify y cachea `SPOTIFY`.
  4. Redis caído: con el contenedor Redis detenido, `GET /genres` y `POST /lists` siguen respondiendo correctamente (tanto con Spotify operativo como con lista local).
- **FR-021**: Las pruebas con Testcontainers se ejecutan automáticamente dentro de `mvn verify` en local y en el pipeline de CI de GitHub Actions.
- **FR-022**: El proyecto backend mantiene cobertura mínima del 80 % de líneas por módulo en JaCoCo, cero violaciones de Checkstyle, todas las reglas de ArchUnit en verde y cero hallazgos críticos o altos en Qodana y Trivy.

**Infraestructura y documentación**

- **FR-023**: En `quipux-playlist-infra`, `compose/compose.yaml` añade el servicio `redis` con versión explícita fijada (sin etiqueta `latest`), en red interna sin exposición pública innecesaria de puertos, y entrega a `api` la configuración de conexión a Redis sin bloquear el arranque ni la operación de `api` si Redis no está disponible.
- **FR-024**: Combinando `compose/compose.yaml` y `compose/compose.wiremock.yaml`, un evaluador puede comprobar que `GET /genres` cachea los géneros sin repetir llamadas a WireMock y que la API sigue respondiendo tras detener el contenedor de Redis.
- **FR-025**: En `quipux-playlist-infra`, `k8s/` añade los manifiestos de Redis (`Deployment` y `Service`) y las variables no secretas en `ConfigMap`, con contenedor endurecido (usuario no root, sin escalada de privilegios, sistema de archivos raíz de solo lectura, imagen explícita fijada y límites de recursos) y sin introducir secretos en Git.
- **FR-026**: Los manifiestos actualizados de `k8s/` generan una salida válida con `kustomize build`, pasan Trivy de configuración y Trivy de secretos sin hallazgos `HIGH` o `CRITICAL`, y quedan listos para sincronización automática por Argo CD.
- **FR-027**: El README del backend documenta las nuevas variables de configuración de Redis/caché y el requisito de Docker para ejecutar las pruebas con Testcontainers; el README de infra documenta el servicio Redis en Compose y su inclusión en Kubernetes.
- **FR-028**: Al completar la historia, se actualizan los criterios de HU-10 en `docs/backlog.md` y se anota la concreción técnica correspondiente en `docs/adr/0001-arquitectura-api-listas-reproduccion.md`.
- **FR-029**: Ninguno de los repositorios, imágenes, manifiestos ni registros contiene secretos o credenciales reales.

### Key Entities *(include if feature involves data)*

- **Entrada cacheada de géneros**: conjunto ordenado y saneado de nombres de género obtenido de Spotify y almacenado temporalmente en Redis bajo una clave única de catálogo.
- **Tiempo de vida (TTL)**: duración máxima de validez de la entrada cacheada en Redis (24 horas por defecto) tras la cual expira automáticamente.
- **Catálogo de géneros y origen**: respuesta de dominio entregada por la consulta de géneros con la lista de géneros y su origen (`SPOTIFY` cuando proviene de Spotify o de su caché vigente; `LOCAL` cuando proviene de la lista de respaldo).
- **Configuración de caché**: parámetros operativos que controlan activación, destino de Redis, TTL de la caché de géneros y tiempo máximo por operación de caché.
- **Servicio Redis de infraestructura**: instancia efímera de Redis gestionada en Docker Compose y en Kubernetes/Kustomize como almacenamiento de caché no crítico para la disponibilidad de la API.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con la caché vacía y Spotify disponible, 10 consultas autorizadas consecutivas a `GET /genres` dentro del TTL producen exactamente **1** llamada a Spotify y **10** respuestas `200` con `origen` `SPOTIFY`.
- **SC-002**: Tras una consulta fallida a Spotify respondida con `LOCAL`, el **100 %** de las consultas inmediatas posteriores con Spotify recuperado vuelven a obtener los géneros de Spotify sin esperar expiración de TTL.
- **SC-003**: Con Redis completamente detenido o inalcanzable, el **100 %** de las peticiones autorizadas a `GET /genres` y `POST /lists` responden con éxito funcional (`SPOTIFY` o `LOCAL` según la disponibilidad de Spotify) en menos de 2 segundos cuando Spotify responde rápido, y en menos de 5 segundos con Redis y Spotify simultáneamente caídos o agotados.
- **SC-004**: Con Redis caído, el **100 %** de las peticiones a `/actuator/health/liveness` y `/actuator/health/readiness` responden `200` sin afectar la disponibilidad del servicio.
- **SC-005**: El **100 %** de los criterios de aceptación de HU-10 en `docs/backlog.md` quedan cubiertos por pruebas automáticas con Testcontainers (acierto, expiración de TTL y Redis caído, además de no cacheo de respaldo) y validaciones de infraestructura.
- **SC-006**: `mvn verify` finaliza en verde con cobertura ≥ **80 %** de líneas por módulo en JaCoCo, **0** violaciones de Checkstyle, **0** violaciones de ArchUnit y **0** bloqueos detectados por BlockHound.
- **SC-007**: En `quipux-playlist-infra`, `kustomize build` sobre `k8s/` termina sin errores y Trivy (configuración y secretos) reporta **0** hallazgos `HIGH` o `CRITICAL`.
- **SC-008**: Los repositorios, manifiestos y registros de ejecución contienen **0** secretos reales.

## Assumptions

- **Ubicación de la historia y ramas**: siguiendo el criterio usado en HU-04 y HU-12, la especificación, el plan y las tareas de HU-10 viven en `quipux-playlist-backend/specs/10-redis-genre-cache/`, y los cambios de código e infraestructura se implementan en ramas `feature/10-redis-genre-cache` de `quipux-playlist-backend` y `quipux-playlist-infra`.
- **Decisiones ya tomadas (`ADR-0001`, secciones 2, 5, 7 y 11)**: Redis como caché de géneros con cliente reactivo, TTL configurable inicial de 24 horas, tolerancia total a fallos de Redis (la caché no puede tumbar la API), pruebas de integración obligatorias con Testcontainers incluyendo el caso de Redis caído, e incorporación de Redis en `compose/` al implementar HU-10 (y en `k8s/`, tal como difirió HU-09).
- **Tiempo máximo de operación en Redis**: el backlog y el ADR no fijan un timeout específico para Redis; se adopta **1 segundo** por defecto (configurable) para que un Redis lento o colgado no degrade `GET /genres` ni sume más de 1 segundo sobre el límite de 3 segundos de Spotify.
- **Clave única de caché y concurrencia en frío**: la lista de géneros de Spotify es global (no depende del usuario), por lo que basta una única entrada cacheada compartida. Ante peticiones simultáneas con caché fría no se introduce bloqueo distribuido: el último resultado válido de Spotify queda almacenado de forma idempotente.
- **Sin cambios en el contrato HTTP ni en el frontend**: `GET /genres` mantiene exactamente los campos `generos` y `origen` (`SPOTIFY` o `LOCAL`) sin añadir un nuevo valor de origen para caché; por tanto, no requiere cambios en OpenAPI, Postman ni `quipux-playlist-frontend`.
- **Separación frente a la caché de recomendaciones IA (HU-12)**: HU-12 utiliza una caché local en memoria para respuestas de recomendaciones de IA y explicitó no depender de HU-10; esta historia cachea únicamente el catálogo de géneros en Redis sin alterar el comportamiento de HU-12.
- **Redis efímero y sin alta disponibilidad en clúster**: al ser una caché regenerable desde Spotify o respaldo local, Redis se despliega como instancia única efímera sin persistencia en disco (`RDB`/`AOF`), ni Redis Sentinel/Cluster, ni invalidación manual por endpoint.
- **Dependencias**: HU-04 (géneros), HU-07 (Docker Compose), HU-08 (CI con verificación y Trivy) y HU-09 (Kubernetes y Argo CD) ya integradas; entorno con Docker disponible para ejecutar Testcontainers tanto en local como en GitHub Actions.
- **Fuera de alcance**: eventos en Kafka (HU-11), invalidación manual de caché por API, migración a Redis de la caché o cuotas de recomendaciones IA (HU-12), persistencia en disco o clúster de Redis, y autenticación/TLS externa hacia Redis más allá de contraseña opcional por entorno.
