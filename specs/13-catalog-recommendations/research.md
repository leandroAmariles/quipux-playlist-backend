# Research: Recomendaciones sobre un catálogo (RAG)

Fecha: 2026-11-27. Las decisiones del ADR-0002 no se reabren; aquí se resuelve cómo cumplirlas.

## Evidencia propia

Experimentos desechables fuera del repositorio: Spring Boot 4.1.3 y Spring AI 2.0.3 con R2DBC (H2) y JDBC a la vez,
contra `pgvector/pgvector:0.8.7-pg18-trixie`, un servidor de modelos falso y, después, las imágenes de HU-12 (Ollama
0.40.18 y LiteLLM 1.111.0) con modelos ya descargados en volúmenes del equipo, montados en solo lectura. RTX 5070 de
12 GB.

### Java y base de datos

| Comprobación | Resultado |
|---|---|
| `DataSource` con `defaultCandidate = false` junto a R2DBC | Ningún `JdbcTemplate` ni gestor de transacciones JDBC automáticos; solo `connectionFactoryTransactionManager` |
| `schema.sql` | Se ejecuta en H2; en PostgreSQL no aparece nada |
| Arranque con PostgreSQL caído | Arranca. `PgVectorStore` con `initializeSchema=false` no hace SQL ni llama al modelo |
| Búsqueda con PostgreSQL caído | Falla a los 2,4 s (`connectionTimeout` 2 s); antes ya se pidió el embedding |
| Índice HNSW forzado, `hnsw.ef_search` por defecto (40), `topK` 100 | **Devuelve 40**. Con `SET hnsw.ef_search = 500` en la conexión, 100 |
| `add` dos veces de los mismos 300 documentos | 300 filas: inserta o actualiza por `id` |
| Cuerpo de embeddings por defecto | `"encoding_format":"base64"` y cada documento precedido de `\n\n` |
| `encoding-format=float` | Se envía `float` |
| `503` con `Retry-After: 0` en embeddings, sin `SingleAttemptInterceptor` | **2 peticiones**; con él, 1. El personalizador de HU-12 ya alcanza al cliente de embeddings |
| `spring.ai.openai.embedding.timeout=2s` | Corta a los 2 s, una petición |
| Vector de otra dimensión | `DataIntegrityViolationException` |
| Embeddings activados con clave vacía | Arranca y **pide igualmente** |
| Opciones nuevas en la petición de chat | **Sustituyen** a las del modelo: se envía `model=gpt-5-mini` sin `max_tokens`; LiteLLM responde `400` |
| `getOptions().mutate().responseFormat(...)` | Conserva `model`, `max_tokens` y *streaming*, y envía el esquema |
| Aplicación actual con `web-application-type=none` | **No arranca** (`SecurityConfig` necesita componentes web) |
| Contexto mínimo con solo la autoconfiguración de embeddings | Arranca (16 *beans*), sin R2DBC |
| Testcontainers 2.0.5, imagen por digest, Docker 29.6.1 en Windows | Arranca, ejecuta el script y reporta PostgreSQL 18.7 y `vector` 0.8.7 |

### Modelos reales a través de LiteLLM

| Comprobación | Resultado |
|---|---|
| Embeddings con `encoding_format` (`base64` o `float`) y la configuración de HU-12 | **`400 UnsupportedParamsError`** |
| Con `additional_drop_params: ["encoding_format"]` | `200`, 768 dimensiones; indexar y buscar desde Spring AI funciona |
| Contexto por defecto de Ollama con esta GPU | 4096 en chat y 2048 en embeddings |
| Peor caso (50 canciones y 20 candidatas con campos de 100 caracteres) | Prompt de 9426 *tokens*. Con 4096 se **recorta a 2050**, instrucciones incluidas; con 16384 entra |
| Memoria de `llama3.2:3b` en GPU | 2,6 GB con 4096; 4,1 GB con 16384 |
| Embedding de una consulta enorme (25 649 caracteres) | `200`, recortada en silencio: igual que la de sus primeras 20 canciones |

### Prompt `v2` con 20 candidatas (`llama3.2:3b`, GPU)

| Formato pedido | Canciones en la lista | Respuestas con algún identificador válido | Notas |
|---|---|---|---|
| `json_object`, identificadores UUID | 5 / 50 | 0 de 12 / 1 de 8 | Suele devolver el ejemplo vacío del prompt |
| `json_object`, identificadores cortos | 5 / 50 | 0 de 12 / 4 de 8 | Igual |
| Esquema JSON, cortos, mínimo 1 | 5 / 50 | 12 de 12 / 12 de 12 | 0 identificadores desconocidos; a veces solo 1 sugerencia |
| Esquema JSON, UUID, mínimo 3 | 50 | 12 de 12 | 4 repetidos; 1,9 s frente a 0,9 s |

### Calidad de la elección

18 listas de 3 canciones de un mismo estilo; entre 20 candidatas, 5 afines. Afinidad medida por género: es una
aproximación, no la evaluación de HU-14.

| Modelo | Sugerencias afines | Al azar |
|---|---|---|
| `llama3.2:3b` | 19 % a 27 % según mínimo y temperatura | 22 % |
| `qwen2.5:7b` | 59 % a 63 % | 22 % |

**`llama3.2:3b` no elige mejor que el azar entre las candidatas.** Como las candidatas ya son las 20 más cercanas, el
resultado sigue siendo razonable, pero la elección del modelo pequeño no le añade afinidad.

### Latencia

| Situación (contexto 8192 en CPU) | `llama3.2:3b` | `qwen2.5:7b` |
|---|---|---|
| Chat en GPU, mediana | 0,9 a 1,7 s | 2,4 a 2,7 s (primera carga: unos 60 s) |
| Chat en CPU, 5 canciones | 5,1 s | 28,9 s |
| Chat en CPU, 50 canciones | 14,6 y 18,3 s | 35,8 y 36,3 s |
| Embeddings en CPU: indexar 300 / consulta de 50 canciones | 8,5 s / 0,2 s (`nomic-embed-text`) | — |

No se midió la latencia en CPU con contexto 16384 (quickstart, escenario 9).

### Composición (T033, 2026-11-27, CPU)

Proyecto temporal con los modelos ya descargados y volumen nuevo del catálogo. Imagen de PostgreSQL revisada el mismo
día (R11).

| Medida | Resultado |
|---|---|
| Indexación de 228 canciones | `added=228`; unos 4 s dentro de un proceso de 7 s |
| Repetición sin cambios | `added=0, kept=228, removed=0` |

Medido con 228 canciones; después se retiraron 14 sin coincidencia en MusicBrainz y quedan 214.
| Puertos | `api`, `catalog-db` y `litellm` solo en `127.0.0.1`; `ollama` sin puertos |
| Memoria en reposo | `ollama` 4,3 GiB (chat con contexto 16384: 4,1 GB), `litellm` 0,98 GiB, `api` 0,37 GiB, `catalog-db` 42 MiB |

## R1. Módulos y conexión a PostgreSQL

- **Decisión**: en `infrastructure`, `spring-ai-pgvector-store`, `spring-boot-starter-jdbc` y `postgresql`
  (*runtime*), con versiones del BOM de Spring AI y de Boot (pgjdbc 42.7.14, HikariCP 7.0.2). `HikariDataSource`
  declarado con `defaultCandidate = false`, `initializationFailTimeout = -1`, `minimumIdle = 0`, 4 conexiones,
  `connectionTimeout` 2 s, `connectTimeout` 2 s, `socketTimeout` 4 s; `JdbcTemplate` propio con tiempo de consulta de
  3 s; `connectionInitSql` con `SET hnsw.ef_search = 500`.
- **Motivo**: ADR (sección 3) y la evidencia: aísla H2, no condiciona el arranque y el índice no recorta resultados
  (el catálogo tiene como máximo 500 canciones).
- **Alternativas**: *starter* de `pgvector` (autoconfigura sobre el `DataSource` principal); esquema creado por la
  aplicación (la ata a PostgreSQL al arrancar).

## R2. Embeddings

- **Decisión**: `spring.ai.model.embedding=${AI_PROVIDER:none}`, modelo `recomendador-embeddings`,
  `metadata-mode=none` y documentos con plantilla de contenido `{content}`. Texto de una canción:
  `título | artista | álbum | año | género`; la consulta une con saltos de línea las canciones de la muestra de HU-12.
  Sin prefijos `search_document:` y `search_query:`. Se mantiene el `encoding_format` por defecto.
- **Motivo**: documentos y consulta con la misma forma. En una muestra de 46 canciones los prefijos de
  `nomic-embed-text` no mejoraron el orden. Los metadatos y los saltos iniciales son ruido.
- **Alternativas**: prefijos (sin mejora medida); `encoding-format=float` (LiteLLM también lo rechaza).
- **Límite conocido**: con campos muy largos solo cuentan las primeras canciones (contexto de 2048). Con datos
  normales, 50 canciones caben. No se corrige en esta historia.

## R3. LiteLLM

- **Decisión**: `additional_drop_params: ["encoding_format"]` solo en `recomendador-embeddings`.
- **Motivo**: sin ello todo embedding del backend falla y toda respuesta sería `RESPALDO`. HU-12 comprobó el alias con
  una petición sin ese parámetro.
- **Alternativas**: `drop_params: true` global (ocultaría otros parámetros no admitidos).

## R4. Búsqueda y candidatas

- **Decisión**: se piden `20 + canciones del catálogo presentes en la lista`; el dominio descarta las presentes y las
  que no estén en el catálogo vigente y se queda con 20, en orden de cercanía. Sin filtro de metadatos.
- **Motivo**: garantiza 20 candidatas aunque la lista contenga canciones del catálogo, y los datos salen siempre del
  fichero vigente (FR-018, FR-021).
- **Nota**: `PgVectorStore` descarta distancias coseno de 1 o más; las medidas reales estuvieron entre 0,36 y 0,58.
- **Alternativas**: filtro `NOT IN` en la consulta (expresión larga y no resuelve el índice desactualizado).

## R5. Indexación

- **Decisión**: se lee qué hay indexado (`id` y modelo, con una consulta propia sobre el `JdbcTemplate`); se generan
  en lotes de 50 las canciones ausentes o de otro modelo y, al terminar, se borran las sobrantes. `id` = UUID derivado
  de los cinco campos; el modelo va en los metadatos.
- **Motivo**: repetir no genera nada; interrumpir conserva los lotes hechos; guardar antes de borrar evita dejar el
  índice vacío. `PgVectorStore` no permite listar.
- **Alternativas**: borrar todo y reindexar (no es idempotente en coste).

## R6. Cómo se lanza la indexación

- **Decisión**: la misma imagen con el argumento `index-catalog`, que arranca un contexto mínimo sin web, indexa y
  termina con código 0 o 1. En la composición, servicio `catalog-index` de una sola ejecución que espera a PostgreSQL y
  a LiteLLM. Repetir: `docker compose ... run --rm catalog-index`.
- **Motivo**: FR-008 y FR-009 sin endpoint ni reintentos en la API; el trabajo no recibe los secretos JWT; el mismo
  código calcula identificadores y versión. La aplicación completa no arranca sin servidor web.
- **Alternativas**: tarea al arrancar la API (necesita reintentos y estado); script aparte (duplicaría la derivación
  de identificadores); endpoint de administración (fuera de la especificación).

## R7. Respuesta del modelo de chat

- **Decisión**: cada petición envía un esquema JSON (`response_format: json_schema`, estricto) con `idCatalogo`
  limitado a los identificadores ofrecidos, de 1 a 5 elementos. A las candidatas se les asignan identificadores cortos
  (`c1`…`c20`) que el adaptador traduce a UUID. El texto de `v2` no cambia. Las opciones se construyen mutando las del
  modelo.
- **Motivo**: de casi ninguna respuesta útil a 12 de 12; los identificadores cortos ahorran unos 400 *tokens*, la
  mitad de tiempo y repeticiones. La validación del dominio sigue siendo obligatoria: otro proveedor puede ignorar el
  esquema.
- **Alternativas**: reescribir el prompt (texto aceptado en el ADR); mínimo 3 (no mejora la afinidad y fuerza
  relleno); temperatura 0,2 (sin mejora consistente).

## R8. Contexto de Ollama

- **Decisión**: `OLLAMA_CONTEXT_LENGTH=16384` en `compose.ai.yaml`.
- **Motivo**: el peor caso necesita unos 10 000 *tokens* con la respuesta; por defecto se recortan las instrucciones
  sin error visible para el backend. También afecta a HU-12, cuya lista puede superar 4096.
- **Alternativas**: reducir canciones o candidatas (contradice FR-017 y FR-018). Un valor intermedio no se midió.

## R9. Modelo de chat

- **Decisión**: se mantiene `llama3.2:3b` por defecto; el README ofrece `qwen2.5:7b` para equipos con GPU mediante
  `OLLAMA_CHAT_MODEL`. **Pendiente de la persona usuaria** (ADR-0002: «modelo de chat, después de medir»).
- **Motivo**: el pequeño cumple el tiempo en CPU y GPU; el de 7B elige mucho mejor, pero en CPU supera el presupuesto
  y casi siempre daría `SIMILITUD`, además de unos 4,7 GB más de descarga.

## R10. Tiempos

- **Decisión**: embedding y búsqueda, 5 s en conjunto (`timeout` de Reactor sobre `boundedElastic`), con el cliente de
  embeddings también en 5 s. El caso de uso limita la llamada al modelo a lo que reste de los 30 s; el adaptador
  conserva su propio tiempo de 30 s. La indexación usa 120 s por lote.
- **Motivo**: FR-025 sin quitar tiempo al modelo cuando la búsqueda es rápida.
- **Alternativas**: dar al modelo 25 s fijos (HU-12 midió 26,9 s en frío).
- **Límite conocido**: cancelar no interrumpe una llamada JDBC; la acotan `socketTimeout` y el tiempo de consulta.

## R11. Imagen de PostgreSQL

- **Decisión**: `pgvector/pgvector:0.8.7-pg18-trixie`, digest
  `sha256:ab49524d18df7200b74ac9a475e1db6297fc1eb57da4c214c63d10ecc4b754fa` (PostgreSQL 18.7). El volumen se monta en
  `/var/lib/postgresql`.
- **Motivo**: 0.8.7 corrige CVE-2026-103484 y las anteriores del año; 18.7 es la versión menor del 12 de noviembre,
  con 52 correcciones. PostgreSQL 19 salió el 19 de noviembre y se descarta por reciente.
- **Pendiente**: revisar avisos y analizar la imagen con Trivy al implementar.
- Fuentes: [pgvector 0.8.7](https://www.postgresql.org/about/news/pgvector-087-released-3392/),
  [versiones de PostgreSQL de noviembre](https://www.helpnetsecurity.com/2026/11/13/postgresql-security-update-november-2026/).

## R12. Origen y comprobación del catálogo

- **Decisión**: `catalog/songs.json` en `infrastructure`, empezando por unas 200 a 250 canciones. Título, artista,
  álbum y año se contrastan con MusicBrainz mediante un script de un solo uso; **el género se asigna a mano** con la
  lista local. El informe de la comprobación se guarda en el repositorio.
- **Motivo**: los datos básicos son CC0, pero las etiquetas y su relación con géneros son
  [CC BY-NC-SA](https://musicbrainz.org/doc/About/Data_License). La API admite 1 petición por segundo y exige un
  [contacto en el `User-Agent`](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting).
- **Contacto**: el script lo toma de `MUSICBRAINZ_CONTACT`, sin valor por defecto: **lo decide la persona usuaria**.
- **Alternativas**: Spotify (prohibido); descargar el volcado de MusicBrainz (desproporcionado).

## R13. Pruebas sin Docker

- **Decisión**: la integración con pgvector usa `testcontainers-postgresql` y `testcontainers-junit-jupiter` con
  `disabledWithoutDocker`; una prueba aparte falla si `CI=true` y no hay Docker. Las pruebas de la API con IA usan un
  catálogo en memoria, salvo una de extremo a extremo con pgvector.
- **Motivo**: la historia exige Testcontainers y la verificación local no debe depender de Docker; en CI no puede
  omitirse en silencio.

## R14. Esquema duplicado

- **Decisión**: el script vive en infra (`compose/catalog-db/init.sql`) y una copia idéntica en las pruebas del
  backend; ambos siguen [contracts/catalog.md](contracts/catalog.md).
- **Motivo**: son dos repositorios; FR-015 exige que lo cree la composición.
