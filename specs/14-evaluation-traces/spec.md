# Feature Specification: Evaluación, web y trazas

**Feature Branch**: `feature/14-evaluation-traces`

**Created**: 2026-11-27

**Status**: Draft

**Input**: HU-14: "como responsable de calidad y usuario, quiero medir las respuestas, verlas en la web y seguir sus prompts y trazas, para comparar cambios, diagnosticar fallos y consultar las sugerencias fácilmente". Fuentes, en la raíz del espacio de trabajo: `backlog-ia.md` (HU-14) y `0002-recomendaciones-con-ia.md` (ADR-0002, secciones 10 a 13). Comportamiento vigente: `specs/13-catalog-recommendations`.

**Repositorios afectados**: backend (evaluación, prompt desde Langfuse, pruebas y documentación) e infra (composición de Langfuse y documentación). **Frontend: sin cambios**; el bloque web ya se entregó (`quipux-playlist-frontend`, `feature/14-web-recommendations`, integrado en `main`).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Medir la calidad de las recomendaciones (Priority: P1)

Como responsable de calidad, ejecuto un comando y obtengo un informe de calidad de las recomendaciones, para comparar cambios de prompt, modelo o catálogo.

**Why this priority**: Es el bloque `P2` del backlog; los demás son `P3`.

**Independent Test**: Con la composición de la IA en marcha y el catálogo indexado, ejecutar el comando y revisar el informe.

**Acceptance Scenarios**:

1. **Given** los modelos y el catálogo disponibles, **When** se ejecuta el comando de evaluación, **Then** se genera un informe con *faithfulness*, *answer relevancy*, *context precision* y *context recall*, por caso y en promedio.
2. **Given** la misma ejecución, **When** se revisa el informe, **Then** incluye comprobaciones deterministas por caso (salida válida, pertenencia a las candidatas, sin duplicados, sin canciones de la lista, máximo 5 y motivo de 120 caracteres como máximo).
3. **Given** la misma ejecución, **When** se revisa el informe, **Then** registra versión del prompt, modelos de chat, embeddings y juez, y versión del catálogo.
4. **Given** un caso en el que el modelo no responde, **When** se evalúa, **Then** queda anotado con su origen (`SIMILITUD` o `RESPALDO`), no se le asignan puntuaciones del modelo y la ejecución continúa.
5. **Given** una métrica que el juez no puede calcular, **When** se genera el informe, **Then** se muestra como no disponible, no como cero, y el promedio indica sobre cuántos casos se calculó.
6. **Given** la API en marcha, **When** se revisa su contrato público, **Then** no hay forma de obtener los contextos recuperados.

---

### User Story 2 - Ver cada llamada al modelo como una traza (Priority: P2)

Como quien diagnostica fallos, consulto en Langfuse cada llamada al modelo con su modelo, latencia y versión de prompt.

**Why this priority**: Bloque `P3`; da la base de las demás funciones de Langfuse.

**Independent Test**: Levantar Langfuse, pedir una recomendación y contar las trazas nuevas.

**Acceptance Scenarios**:

1. **Given** las variables de entorno definidas, **When** se levanta la composición con `compose.langfuse.yaml`, **Then** Langfuse queda disponible solo en el propio equipo, con proyecto y claves ya creados.
2. **Given** Langfuse activo, **When** el backend llama una vez al modelo de chat, **Then** aparece exactamente una traza con modelo, latencia y versión del prompt.
3. **Given** una respuesta servida desde la caché, de `RESPALDO` sin llamada o un embedding, **When** se revisan las trazas, **Then** no se añade ninguna.
4. **Given** Langfuse detenido, **When** se piden recomendaciones, **Then** la respuesta llega igual que sin Langfuse.
5. **Given** la composición sin `compose.langfuse.yaml`, **When** se levanta, **Then** todo funciona como en HU-13.

---

### User Story 3 - Gestionar el prompt desde Langfuse sin perder Git como fuente (Priority: P2)

Como responsable del servicio, publico en Langfuse el prompt del repositorio y el backend usa la versión marcada `production`, sin dejar de funcionar si Langfuse no está.

**Why this priority**: Bloque `P3`; depende de US2.

**Independent Test**: Publicar el prompt, pedir una recomendación, detener Langfuse y repetir.

**Acceptance Scenarios**:

1. **Given** Langfuse vacío, **When** se ejecuta el script de publicación, **Then** crea el prompt con la etiqueta `production` y la referencia al commit.
2. **Given** el mismo contenido ya publicado, **When** se repite, **Then** no se crea una versión nueva.
3. **Given** un archivo de prompt con cambios sin confirmar en Git, **When** se ejecuta el script, **Then** se niega a publicar.
4. **Given** una versión `production` en Langfuse, **When** el backend pide recomendaciones, **Then** usa su texto, y `versionPrompt`, la traza y la huella de la caché identifican esa versión (por ejemplo `v2-lf3`).
5. **Given** Langfuse detenido, lento, sin configurar, sin ese prompt o con un texto que no respeta el formato del prompt, **When** se piden recomendaciones, **Then** se usa el archivo local y `versionPrompt` es `v2`.
6. **Given** peticiones seguidas, **When** el prompt ya se consultó hace menos del tiempo de caché, **Then** no se vuelve a consultar a Langfuse, haya ido bien o mal la consulta anterior.

---

### User Story 4 - Comparar versiones de prompt por sus puntuaciones (Priority: P3)

Como responsable de calidad, veo en Langfuse las puntuaciones de RAGAS asociadas a la versión del prompt evaluada.

**Independent Test**: Ejecutar la evaluación con Langfuse activo y localizar las puntuaciones.

**Acceptance Scenarios**:

1. **Given** Langfuse configurado, **When** termina la evaluación, **Then** sus puntuaciones quedan en Langfuse identificadas con la versión del prompt.
2. **Given** Langfuse detenido o sin configurar, **When** se evalúa, **Then** el informe se genera igual y se avisa de que no se enviaron las puntuaciones.

### Edge Cases

- Langfuse responde con error de autenticación: se usa el archivo local, sin registrar las claves.
- Cambia la versión `production`: se adopta al caducar la caché del prompt, y las respuestas guardadas con la versión anterior dejan de reutilizarse.
- La IA está desactivada: no se consulta a Langfuse.
- El comando de evaluación se ejecuta sin catálogo indexado: los casos quedan como `RESPALDO` y el comando termina con error.

## Requirements *(mandatory)*

### Functional Requirements

**Evaluación**

- **FR-001**: La evaluación MUST vivir en un entorno aislado en `evaluation/`, con la versión de Python y las dependencias fijadas, y ejecutarse fuera de línea, nunca dentro de una petición.
- **FR-002**: MUST haber entre 15 y 20 casos, cada uno con una lista de canciones y las canciones relevantes esperadas, todas del catálogo y ninguna de la propia lista.
- **FR-003**: Un solo comando MUST generar el informe con las cuatro métricas del ADR.
- **FR-004**: El informe MUST incluir las comprobaciones deterministas del escenario 1.2.
- **FR-005**: Los contextos recuperados MUST obtenerse con un modo interno que use la misma recuperación y elección que el endpoint, sin exponer ninguna ruta HTTP.
- **FR-006**: El informe MUST registrar versión del prompt, modelos y versión del catálogo.
- **FR-007**: MUST revisarse a mano una muestra y anotarse la línea base en el repositorio. No hay umbral que bloquee.
- **FR-008**: Dependabot MUST cubrir las dependencias de `evaluation/`.

**Trazas**

- **FR-009**: `compose.langfuse.yaml` MUST levantar Langfuse autoalojado, publicando puertos solo en `127.0.0.1`, con imágenes fijadas por digest; se MUST documentar la memoria necesaria y anotar el consumo medido.
- **FR-010**: Cada llamada del backend al modelo de chat MUST producir una sola traza con modelo, latencia y versión del prompt, enviada por una sola vía.

**Prompt**

- **FR-011**: Un script MUST publicar el prompt del repositorio con la referencia al commit, sin duplicar versiones iguales.
- **FR-012**: El backend MUST usar la versión con etiqueta `production`, consultada con un tiempo máximo corto y guardada en caché.
- **FR-013**: Si Langfuse falla o no está configurado, el backend MUST usar el archivo local; hay una prueba automatizada.
- **FR-014**: La versión usada MUST reflejarse en `versionPrompt` (solo con `origen: IA`), en la traza y en la huella de la caché.

**Seguridad y entrega**

- **FR-015**: Las claves MUST llegar por variables de entorno y no aparecer en logs, informes ni repositorio.
- **FR-016**: La aplicación MUST arrancar y las pruebas pasar sin Langfuse, sin modelos y sin `AI_API_KEY`.
- **FR-017**: `AI-LOG.md` y los README MUST actualizarse.

### Key Entities

- **Caso de evaluación**: identificador, canciones de la lista y canciones relevantes esperadas.
- **Resultado de un caso**: origen, candidatas recuperadas, sugerencias, comprobaciones y puntuaciones.
- **Informe**: fecha, versiones (prompt, modelos, catálogo), resultados y promedios.
- **Versión de prompt**: nombre, texto, número, etiqueta `production` y commit de origen.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un comando produce un informe completo de los 15 a 20 casos sin pasos intermedios.
- **SC-002**: El número de trazas nuevas es igual al número de llamadas al modelo de chat.
- **SC-003**: Con Langfuse detenido, el 100 % de las peticiones de recomendaciones responden `200`, y la consulta del prompt añade como máximo 1 segundo a una petición por periodo de caché.
- **SC-004**: Publicar dos veces el mismo prompt deja una sola versión.
- **SC-005**: El contrato público de la API no cambia.

## Assumptions

- El bloque web ya está entregado y probado; esta especificación no lo reabre.
- El juez de RAGAS es un modelo local a través de LiteLLM; un modelo pequeño puede puntuar de forma inestable, así que las notas sirven para comparar versiones, no como valor absoluto.
- Las métricas se adaptan así: pregunta = pedido de recomendación para la lista; respuesta = sugerencias y motivos; contextos = lista y candidatas; referencia = canciones esperadas.
- Langfuse es solo para desarrollo local; sus credenciales iniciales las define quien lo instala. Ampliar la memoria de Docker (`.wslconfig`) es un paso manual del usuario.
- Las trazas contienen datos de las listas: se quedan en local.
- **Fuera de alcance**: cambios en la web, umbral bloqueante de calidad, evaluación en CI, Langfuse en Kubernetes, edición del prompt desde la interfaz como fuente oficial y trazas de embeddings.
