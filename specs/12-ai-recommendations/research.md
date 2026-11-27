# Research: Recomendaciones con IA

Fecha: 2026-11-27. Las decisiones del ADR-0002 no se reabren; aquí se resuelve cómo cumplirlas.

## Evidencia propia

Experimento desechable fuera del repositorio con Spring Boot 4.1.3 y Spring AI 2.0.3 (`openai-java-core` 4.101.0,
OkHttp 4.12.0) contra un servidor HTTP falso en local, sin modelos.

| Comprobación | Resultado |
|---|---|
| `base-url` con y sin `/v1` | Pide `/v1/chat/completions` y `/chat/completions`: el SDK no añade `/v1` |
| `spring.ai.openai.max-retries=0` ante `500` | 1 petición |
| Sin fijar reintentos ni tiempo | 4 peticiones ante `500`; una respuesta lenta de 6 s se completa |
| `spring.ai.openai.timeout=3s` con respuesta en *streaming* | La llamada entera se corta a los 3 s y la conexión se cierra |
| Cancelar en Reactor (`timeout`) sin tiempo en el SDK | La petición HTTP **sigue**: el servidor termina de enviar |
| Solo `spring.ai.model.chat` y `embedding` en `none`, sin clave | **No arranca** (`At least one credential source must be specified`) |
| Los seis `spring.ai.model.*` en `none`, sin clave | Arranca sin `ChatModel` |
| `chat=openai` con `api-key` vacía | Arranca y pide **sin** `Authorization` |
| Variables `OPENAI_API_KEY` y `OPENAI_BASE_URL` en el entorno | Mandan las propiedades; con clave vacía no se usa la del entorno |
| Error del proveedor | `MessageAggregator` registra en `ERROR` el cuerpo devuelto; con ese *logger* apagado, no |
| BlockHound tras un calentamiento | Sin llamadas bloqueantes en `stream()` |
| BlockHound en la primera llamada | Falla por carga de clases en el hilo que suscribe |
| Respuesta `200` vacía o que no es SSE | Texto vacío, sin error |
| Cuerpo enviado | `model`, `messages`, `max_tokens`, `response_format`, `stream: true` |

## R1. Módulo de Spring AI y activación

- **Decisión**: `spring-ai-bom` 2.0.3 y `spring-ai-starter-model-openai` solo en `infrastructure`.
  `spring.ai.model.chat=${AI_PROVIDER:none}` (`none` u `openai`) y `embedding`, `image`, `moderation`,
  `audio.speech` y `audio.transcription` fijos en `none`.
- **Motivo**: es lo acordado en el ADR (sección 2). El ADR pedía comprobar los demás modelos: **exigen la clave**, así
  que hay que apagar los seis.
- **Alternativas**: `spring-ai-openai` sin *starter* y construir el modelo a mano. Evita la autoconfiguración, pero se
  aparta del ADR sin necesidad.
- **Efecto**: llegan Jackson 2 (junto a Jackson 3), Kotlin y OkHttp como dependencias transitivas. La aplicación sigue
  usando Jackson 3.
- **Hallado al implementar**: Spring AI trae `swagger-annotations` 2.2.31 y `swagger-annotations-jakarta` 2.2.38, más
  antiguas que las de `springdoc` 3.2.0; la documentación OpenAPI fallaba con `NoSuchMethodError`. Ambas se fijan en
  2.2.55 en el `pom.xml` padre.

## R2. Petición al modelo

- **Decisión**: `base-url` terminada en `/v1` (por defecto `http://localhost:4000/v1`), modelo
  `recomendador-chat`, `max-tokens` 600, `response-format.type=JSON_OBJECT` y `ChatModel.stream()` agregando el texto.
- **Motivo**: la dirección se fija siempre, para no terminar nunca en el servicio público de OpenAI. El modo JSON reduce
  las respuestas inválidas de un modelo pequeño; la validación sigue siendo obligatoria.
- **Pendiente de comprobar**: que LiteLLM traduzca `max_tokens` y `response_format` a Ollama (quickstart, escenario 6).

## R3. Reintentos

- **Decisión**: `spring.ai.openai.max-retries=0`; en LiteLLM, `num_retries: 0` en `litellm_settings` y
  `router_settings`, y `disable_cooldowns: true`.
- **Motivo**: una petición del usuario, una petición al proveedor (FR-018). El enfriamiento del *router* competiría con
  el cortacircuitos del backend. Los reintentos por defecto se comprobaron: 4 peticiones.
- **Alternativas**: `spring.ai.retry.*`, que según el reporte citado en el ADR no afecta a esta integración.
- **Hallado al implementar**: `max-retries=0` no basta. El cliente HTTP (OkHttp) **reenvía** la petición ante `503`
  con `Retry-After: 0` y `408` (2 peticiones), `307` y `308` (21) y ante una conexión cortada (hasta 3). El
  experimento inicial solo había probado el `500`. Solución: `SingleAttemptInterceptor`, registrado con
  `OpenAiHttpClientBuilderCustomizer`, marca el cuerpo como de un solo uso; así hay una petición en todos esos casos
  (`RecommendationFailureApiTest`).
- **Límite conocido**: ante `301`, `302` o `303`, OkHttp seguiría la redirección con un `GET` sin cuerpo. No vuelve a
  invocar el modelo y la interfaz de Spring AI no permite desactivarlo.
- **Pendiente de comprobar**: la documentación de LiteLLM es ambigua sobre el valor por defecto; se cuenta lo que llega
  al proveedor (quickstart, escenario 7).

## R4. Tiempo máximo y llamadas simultáneas

- **Decisión**: `playlist.ai.timeout` (30 s) alimenta `spring.ai.openai.timeout` y además un `timeout` de Reactor.
  El permiso de concurrencia (un `Semaphore` con `tryAcquire`) se libera cuando termina **la llamada al proveedor**: la
  llamada se desacopla de la cancelación del suscriptor con `cache()`.
- **Motivo**: solo el tiempo del SDK cierra la conexión. Si un usuario abandona la petición y el permiso se liberase
  entonces, habría más de 2 generaciones a la vez en el modelo (FR-028).
- **Alternativas**: `Bulkhead` de Resilience4j (otra librería para un contador); confiar solo en Reactor (descartado
  por la evidencia).

## R5. Bloqueo

- **Decisión**: la llamada se suscribe en `boundedElastic`. Las pruebas con BlockHound calientan primero el camino,
  como `PlaylistNonBlockingTest`.
- **Motivo**: preparar la petición y cargar clases ocurre en el hilo que suscribe. La respuesta llega por hilos de
  OkHttp, que no son del *event loop*.
- **Alternativas**: llamar desde el *event loop* y confiar en el calentamiento.

## R6. Secretos y registros

- **Decisión**:
  1. Sin `AI_API_KEY` (vacía o en blanco) no se llama: se usa el adaptador simulado.
  2. `logging.level.org.springframework.ai.chat.model.MessageAggregator: "off"`.
  3. La excepción de dominio no conserva la causa ni el mensaje del proveedor: solo el motivo y el estado HTTP.
- **Motivo**: el cuerpo de error del proveedor puede repetir el prompt, y se comprobó que Spring AI lo registra.
- **Pendiente de comprobar**: que la variable `OPENAI_LOG` del SDK no active registros de peticiones. La composición no
  la define.

## R7. Cortacircuitos

- **Decisión**: `resilience4j-circuitbreaker` y `resilience4j-reactor` 2.4.0, configurados a mano: ventana de 10
  llamadas, mínimo 5, umbral 50 %, 30 s abierto, 1 llamada de prueba. Envuelve la llamada **con** su `timeout`; la
  interpretación del contenido queda fuera.
- **Motivo**: ADR (sección 7). Así cuentan los tiempos agotados, la conexión y los errores HTTP, y no el JSON inválido
  (FR-020). Ambas librerías resuelven con Reactor 3.8.9; no usan Spring.
- **Alternativas**: *starter* de Resilience4j (descartado en el ADR); cortacircuitos propio.

## R8. Límite de peticiones

- **Decisión**: puerto `RequestQuotaPort` con un adaptador en memoria de ventana deslizante: hasta 10 marcas de tiempo
  por usuario en los últimos 60 s, actualizadas de forma atómica. `Retry-After` son los segundos, redondeados hacia
  arriba y como mínimo 1, hasta que caduque la marca más antigua. Las rechazadas no se anotan. Reloj inyectado.
- **Motivo**: cumple "la undécima en un minuto" sin el doble cupo en el borde de una ventana fija. El usuario es el
  `sub` del token. El mapa es una caché Caffeine que olvida a quien lleva más de un minuto sin pedir.
- **Alternativas**: `RateLimiter` de Resilience4j (ventana fija y un limitador por nombre); filtro web (no permitiría
  respetar el orden respecto a la lista sin duplicar reglas).

## R9. Caché

- **Decisión**: puerto `RecommendationCachePort` con Caffeine (versión de Spring Boot, 3.2.4): 500 entradas y 24 h
  desde la escritura. Clave: huella SHA-256 de todas las canciones, versión del prompt y alias del modelo.
- **Motivo**: acotada y local (ADR, sección 9). La huella ocupa poco con listas largas; HU-13 añadirá la versión del
  catálogo.
- **Alternativas**: `Mono.cache` por clave (no acota); Redis (descartado).

## R10. Prompt, entrada y validación

- **Decisión**:
  - `prompts/recommendations/v1.txt` con el texto exacto del ADR. Lo anterior al bloque `<lista>` final va como mensaje
    de sistema y el bloque, como mensaje de usuario.
  - Canciones: las 50 primeras en orden de creación (`ORDER BY s.id`), cada campo recortado a 100 caracteres, como
    JSON con `titulo`, `artista`, `album`, `anno` y `genero`. Los caracteres `<` y `>` se escriben como `<` y
    `>`, así ningún dato cierra el delimitador.
  - Respuesta: JSON estricto con Jackson 3 (sin texto sobrante), objeto con el arreglo `recomendaciones`. Un elemento
    con tipos equivocados se descarta; los campos extra se ignoran.
- **Motivo**: separar roles hace más difícil que un título actúe como instrucción, sin cambiar el texto aceptado. La
  forma se valida en `infrastructure` y las reglas de contenido (FR-015) en `domain`.
- **Alternativas**: todo en un solo mensaje; `BeanOutputConverter` de Spring AI (todo o nada, sin descartar por
  elemento).

## R11. Lista por defecto

- **Decisión**: `default-recommendations.properties`, con claves `recommendation.NN.titulo|artista|genero|motivo`. Se
  valida al arrancar (mínimo 5, sin repetidos, campos completos y dentro de tamaño, géneros de la lista local).
- **Motivo**: misma convención que `local-genres.properties`; configurable por ruta (FR-021).
- **Alternativas**: JSON (otra convención); constantes en código (no cambiable sin compilar).

## R12. LiteLLM

- **Decisión**: `litellm/config.yaml` en infra: `recomendador-chat` → `ollama_chat/llama3.2` y
  `recomendador-embeddings` → `ollama/nomic-embed-text`, `api_base: http://ollama:11434`, `keep_alive: -1`,
  `timeout` 120 s (por encima del del backend, que es quien manda), sin reintentos, `turn_off_message_logging: true` y
  `master_key: os.environ/LITELLM_MASTER_KEY`, alimentada con `AI_API_KEY`. Salud en `/health/liveliness`.
- **Motivo**: documentación oficial. LiteLLM no arranca con la clave maestra vacía o conocida como insegura: la guía
  pide generar una aleatoria.
- **Comprobado el 2026-11-27 (T077)**: embeddings con el prefijo `ollama/` devuelven 768 dimensiones;
  `max_tokens=5` corta la respuesta (`finish_reason: length`); `response_format: json_object` devuelve JSON; sin clave,
  `401`. Con un doble de Ollama que contaba peticiones, ante `500`, `503` con `Retry-After: 0` y `408` LiteLLM hizo
  **un** intento. La imagen no trae `curl` ni `wget`: la salud usa `python`. `telemetry: false` queda en la
  configuración y los registros no mencionan telemetría (no se inspeccionó el tráfico de red). Los registros de `api` y
  `litellm` no contenían la clave, tokens, contraseñas, el prompt, canciones ni sugerencias.
- **Observado**: con el contenedor de Ollama parado, LiteLLM no falla enseguida; corta el backend a los 30 s. El
  cortacircuitos evita repetir esa espera tras varios fallos.

## R13. Ollama

- **Decisión**: servicio sin puertos con volumen `ollama-models`, `OLLAMA_NO_CLOUD=1` y `OLLAMA_KEEP_ALIVE=-1`, salud
  con `ollama list`. Un servicio `ollama-init` de una sola ejecución descarga cada modelo solo si falta y lo precarga.
  GPU en `compose.ai.gpu.yaml`, comprobada con `ollama ps` (columna `PROCESSOR`).
- **Motivo**: FR-030 a FR-038. `OLLAMA_NO_CLOUD` evita que algo salga del equipo.
- **Comprobado el 2026-11-27 (T077)**: `ollama run <modelo de embeddings> "texto"` precarga el modelo. La salida de
  progreso se oculta salvo si la precarga falla.
- **Modelo de chat**: el volumen de modelos del equipo tenía `llama3.2:3b`, así que la composición fija esa etiqueta
  por defecto (sin etiqueta, Ollama buscaría `latest`). Los modelos se copiaron de un volumen existente, montado en
  solo lectura, para no descargarlos de nuevo.

### Mediciones (T078, 2026-11-27)

Equipo: RTX 5070 de 12 GB y motor de Docker con 15,2 GiB. Latencia de `GET .../recommendations` de listas no
guardadas, con `llama3.2:3b`.

| Situación | CPU | GPU |
|---|---|---|
| Modelo sin cargar (Ollama recién reiniciado) | 10,5 s | 3,8 s |
| En caliente | 7,6 a 9,5 s | 0,65 a 1,0 s |
| Primera petición tras crear el contenedor con GPU | — | **26,9 s**, una vez |
| Respuesta guardada | 5 ms en el backend | igual |

| Memoria | CPU | GPU |
|---|---|---|
| `ollama` | 2,9 GiB | 0,73 GiB y VRAM (5,1 GB usados en total en la GPU) |
| `litellm` | 0,98 GiB | 0,98 GiB |
| `api` | 0,36 GiB | 0,37 GiB |

Se mantienen 30 s de tiempo máximo y el modelo pequeño. La medida de 26,9 s queda cerca del límite: en un equipo más
lento esa primera petición puede acabar en respaldo. No se comparó con un modelo de 8B ni se evaluó la calidad: en las
muestras aparecieron canciones dudosas o mal atribuidas, esperable sin catálogo (HU-13).

## R14. Cadena de suministro

| Imagen | Digest del índice (comprobado el 2026-11-27) | Avisos publicados |
|---|---|---|
| `ollama/ollama:0.40.18` | `sha256:1cabd20e1762a7a8444bf4a7110b346d923cd77ab6c97c984434140638c913ec` | La API de avisos de GitHub del repositorio no devuelve ninguno. Coincide con el ADR |
| `ghcr.io/berriai/litellm:v1.111.0` | `sha256:ce00096d012719d58c52cecfa373f7c6256dd1b0ff08f6fa607e10e9628f1f46` | De los 30 más recientes, ninguno incluye la 1.111.0; el último (JWT/SSO) se corrige en ella |

- Hay además avisos de terceros sobre LiteLLM: CVE-2026-40217, corregido en la serie 1.83; ZDI-26-1082, corregido en
  la 1.104.0; y ZDI-26-1031, que el proveedor discute, requiere ser administrador y no tiene parche anunciado. Este
  último se mitiga con el puerto solo en `127.0.0.1` y la clave maestra fuera del repositorio. FR-033 pide una imagen
  «sin avisos pendientes»: ver la decisión más abajo.
- Sobre Ollama hay avisos de terceros: los revisados con versión corregida son anteriores a la 0.40, uno afecta al
  actualizador de Windows (no a la imagen) y ZDI-26-403 (denegación de servicio al descargar) no publicaba versiones
  afectadas en lo consultado. Se mitiga al no publicar puertos.
- Fuentes: [avisos de LiteLLM](https://github.com/BerriAI/litellm/security/advisories),
  [ZDI-26-1031](https://www.zerodayinitiative.com/advisories/ZDI-26-1031/),
  [ZDI-26-1082](https://www.zerodayinitiative.com/advisories/ZDI-26-1082/),
  [ZDI-26-403](https://zerodayinitiative.com/advisories/ZDI-26-403),
  [Ollama en LiteLLM](https://docs.litellm.ai/docs/providers/ollama),
  [reintentos de LiteLLM](https://docs.litellm.ai/docs/proxy/reliability),
  [FAQ de Ollama](https://raw.githubusercontent.com/ollama/ollama/main/docs/faq.mdx).
- **Firma verificada el 2026-11-27 (T072)** con `ghcr.io/sigstore/cosign/cosign:v3.1.6`
  (`sha256:7d08ec40d6f6fd9ced81a498c367ee6d4dd90d73218f58a791f78661bdbef769`), usando la orden del ADR sobre el digest
  de LiteLLM de la tabla: firma, registro de transparencia y certificado correctos, con identidad
  `BerriAI/project-releaser/.github/workflows/release.yml@refs/heads/main`.
- **Avisos revisados de nuevo el 2026-11-27**: ninguno posterior al 2026-11-24 en LiteLLM y ninguno publicado en el
  repositorio de Ollama.
- **Decisión de la persona usuaria (2026-11-27)**: se acepta ZDI-26-1031 y se documenta; LiteLLM solo escucha en
  `127.0.0.1` y la clave maestra no está en el repositorio.

## R15. Generación del cliente por un asistente

- **Decisión**: una sesión nueva del asistente, **sin** este documento ni el plan, recibe un prompt con lo que pediría
  un desarrollador (contrato, puerto y comportamiento esperado). El prompt y el código se copian a `AI-LOG.md` sin
  tocar antes de cualquier cambio. La revisión contrasta ese código con R1 a R10.
- **Motivo**: FR-042. Con estas conclusiones en el contexto, la revisión no mediría nada.
- **Alternativas**: generarlo en la sesión que investigó (sesgado).
