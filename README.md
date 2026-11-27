# quipux-playlist-backend

API REST reactiva para crear, consultar y borrar listas de reproducción de música, con autenticación JWT, roles,
géneros desde Spotify y recomendaciones con IA. Tiene una [aplicación web](https://github.com/leandroAmariles/quipux-playlist-frontend) en otro
repositorio, y este README es el punto de entrada del proyecto.

Está hecho con Java 21, Spring Boot 4, WebFlux, R2DBC y una base H2 en memoria.

## R2DBC en lugar de JPA

**El enunciado pide JPA y este proyecto no lo usa.** Es una desviación deliberada, pendiente de confirmación por quien
envió la prueba.

**Motivo:** JPA trabaja sobre JDBC, que es bloqueante y contradice el modelo reactivo. R2DBC permite una cadena no
bloqueante desde la petición hasta el controlador de la base de datos.

**Consecuencias:**

- No hay relaciones ni cascadas automáticas: listas y canciones son dos tablas que el adaptador guarda, compone y borra
  de forma explícita, dentro de una transacción reactiva.
- El esquema se define en SQL ([`schema.sql`](infrastructure/src/main/resources/schema.sql)).
- La persistencia está detrás de un puerto: un adaptador JPA podría sustituir al de R2DBC sin tocar el dominio ni los
  casos de uso.
- El controlador `r2dbc-h2` ejecuta las consultas en el mismo hilo que las pide. Con la base en memoria son operaciones
  cortas. BCrypt, que es lento a propósito, se ejecuta en un hilo aparte.

Detalle en el [ADR-0001](docs/adr/0001-arquitectura-api-listas-reproduccion.md), sección 1.

## Con Docker

Para levantar la API y la aplicación web con un solo comando, sin instalar Java, Maven ni Node, sigue el
[README de quipux-playlist-infra](https://github.com/leandroAmariles/quipux-playlist-infra#readme). El
[`Dockerfile`](Dockerfile) de este repositorio construye la imagen de la API.

El resto de este documento explica cómo hacerlo sin Docker.

## Requisitos

- Java 21
- Acceso a internet la primera vez, para descargar Maven y las dependencias
- Opcionales: [Postman](https://www.postman.com/downloads/) para la colección y Node para la aplicación web

No hace falta instalar Maven. Todos los comandos se ejecutan desde la raíz de este repositorio.

## Variables de entorno

| Variable | ¿Obligatoria? | Por defecto | Para qué |
|---|---|---|---|
| `JWT_SECRET` | sí | — | clave que firma los tokens, de 32 bytes como mínimo |
| `DEMO_USER_PASSWORD` | sí | — | contraseña del usuario `user`, de 1 a 72 bytes |
| `DEMO_ADMIN_PASSWORD` | sí | — | contraseña del usuario `admin`, de 1 a 72 bytes |
| `JWT_TTL` | no | `30m` | vigencia del token |
| `SERVER_PORT` | no | `8080` | puerto de la API |
| `SPOTIFY_CLIENT_ID` | no | — | credencial de Spotify; sin las dos se usa la lista local |
| `SPOTIFY_CLIENT_SECRET` | no | — | credencial de Spotify |
| `SPOTIFY_ACCOUNTS_BASE_URL` | no | `https://accounts.spotify.com` | dirección donde se pide el token de Spotify |
| `SPOTIFY_API_BASE_URL` | no | `https://api.spotify.com` | dirección donde se consultan los géneros |
| `SPOTIFY_TIMEOUT` | no | `3s` | tiempo máximo para obtener los géneros |
| `AI_PROVIDER` | no | `none` | `openai` activa las recomendaciones con IA a través de LiteLLM |
| `AI_API_KEY` | no | — | clave de LiteLLM; sin ella no se llama al modelo |
| `AI_BASE_URL` | no | `http://localhost:4000/v1` | dirección de LiteLLM, terminada en `/v1` |
| `AI_CHAT_MODEL` | no | `recomendador-chat` | alias del modelo de chat |
| `AI_TIMEOUT` | no | `30s` | tiempo máximo total de la búsqueda en el catálogo y la llamada al modelo |
| `AI_EMBEDDING_MODEL` | no | `recomendador-embeddings` | alias del modelo de embeddings |
| `AI_EMBEDDING_TIMEOUT` | no | `5s` | tiempo máximo de una petición de embeddings |
| `CATALOG_DB_URL` | no | — | JDBC de PostgreSQL con pgvector; sin ella y sin contraseña no hay catálogo |
| `CATALOG_DB_USER` | no | `catalog` | usuario de PostgreSQL |
| `CATALOG_DB_PASSWORD` | no | — | contraseña de PostgreSQL; solo por variable de entorno |
| `CATALOG_RETRIEVAL_TIMEOUT` | no | `5s` | tiempo máximo del embedding y la búsqueda juntos |

Las tres obligatorias no tienen valor por defecto para que no haya secretos en el repositorio: sin ellas la aplicación
no arranca. Las contraseñas las eliges tú.

## Ejecución

En Linux, macOS o Git Bash:

```bash
./mvnw clean package
export JWT_SECRET="$(openssl rand -base64 48)"
export DEMO_USER_PASSWORD="<elige una>"
export DEMO_ADMIN_PASSWORD="<elige otra>"
java -jar bootstrap/target/playlist-bootstrap-*.jar
```

En PowerShell:

```powershell
.\mvnw.cmd clean package
$env:JWT_SECRET = "<una clave larga, de 32 caracteres o más>"
$env:DEMO_USER_PASSWORD = "<elige una>"
$env:DEMO_ADMIN_PASSWORD = "<elige otra>"
java -jar (Get-Item bootstrap\target\playlist-bootstrap-*.jar).FullName
```

La API queda en `http://localhost:8080`. Si ese puerto está ocupado, define `SERVER_PORT` o añade
`--server.port=18080`.

La base de datos es H2 en memoria: no hay que preparar nada y los datos se pierden al detener la aplicación.

No hay límite de intentos de inicio de sesión y la documentación de la API es pública: la aplicación no debe exponerse
en una red pública.

### Primera lista

Hay dos usuarios de demostración, `user` (rol `USER`, solo consulta) y `admin` (rol `ADMIN`, además crea y borra).
Con la API en marcha, desde otra terminal en la que `DEMO_ADMIN_PASSWORD` tenga el mismo valor:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$DEMO_ADMIN_PASSWORD\"}" | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

curl -i -X POST http://localhost:8080/lists -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"nombre":"Mis favoritas","descripcion":"Demo","canciones":[{"titulo":"Uno","anno":"1999","genero":"rock"}]}'

curl -i http://localhost:8080/lists/Mis%20favoritas -H "Authorization: Bearer $TOKEN"
```

Sin `curl`, se puede hacer lo mismo desde la documentación interactiva o con la colección de Postman.

## Pruebas

```bash
./mvnw verify
```

En PowerShell, `.\mvnw.cmd verify`. Compila, ejecuta las pruebas y aplica los controles de calidad. No necesita
variables de entorno ni conexión con Spotify.

Falla ante una violación de Checkstyle, una dependencia prohibida entre capas o menos del 80 % de líneas cubiertas en
un módulo.

## Integración continua

Cada PR y cada push a `main` ejecutan tres comprobaciones ([`ci.yml`](.github/workflows/ci.yml)), y `main` solo admite
PR con las tres en verde.

| Comprobación | Falla ante | En local |
|---|---|---|
| `verify` | una prueba, la cobertura o Checkstyle | `./mvnw verify` |
| `qodana` | un problema de severidad crítica o alta | Qodana, abajo |
| `trivy` | un secreto, o una dependencia entregada con una vulnerabilidad `HIGH` o `CRITICAL` corregible | Trivy, abajo |

```bash
docker run --rm -v "$PWD:/data/project" jetbrains/qodana-jvm-community:2026.3

./mvnw clean package -Dmaven.test.skip=true
trivy rootfs --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 bootstrap/target
trivy fs --scanners secret --severity HIGH,CRITICAL --exit-code 1 --skip-dirs "**/target" .
```

En Git Bash, antepón `MSYS_NO_PATHCONV=1` al comando de Docker.

Un hallazgo que no se pueda corregir se excluye de uno en uno y con su motivo: en Trivy, con un `.trivyignore.yaml`
(campo `statement`) y `trivyignores: .trivyignore.yaml` en el paso del workflow; en Qodana, con `exclude` en
[`qodana.yaml`](qodana.yaml).

Si una comprobación falla porque no pudo descargar Maven, una dependencia o la base de vulnerabilidades, usa
**Re-run failed jobs**.

## Versiones e imágenes

[release-please](https://github.com/googleapis/release-please) calcula la versión a partir de los
[Conventional Commits](https://www.conventionalcommits.org/es/) que llegan a `main`: `fix` sube el parche y `feat` o un
cambio incompatible suben el menor mientras la versión sea menor que 1.0.0. `docs`, `chore`, `test`, `refactor`,
`build` y `ci` no generan versión.

1. Con cambios publicables, abre o actualiza un PR `chore(main): release X.Y.Z` con la versión en los `pom.xml` y el
   `CHANGELOG.md`. Pasa por el mismo CI que los demás.
2. Al fusionarlo se crean el tag `vX.Y.Z` y la release, y el mismo workflow
   ([`release.yml`](.github/workflows/release.yml)) construye la imagen, la analiza con Trivy y la publica.
3. Si la publicación en GHCR termina en verde, el trabajo `update-infra` abre o actualiza automáticamente un PR en
   `quipux-playlist-infra` (`chore(deploy): bump backend image to X.Y.Z`) actualizando el tag en `k8s/kustomization.yaml`
   para que Argo CD lo despliegue tras aprobar el CI de infra. Si falla `publish`, no se abre ningún PR en infra.

La imagen es `ghcr.io/leandroamariles/quipux-playlist-backend:X.Y.Z`, para `linux/amd64` y sin etiqueta `latest`.

- Si la publicación falla por una causa pasajera, **Re-run failed jobs** publica esa misma versión.
- Si Trivy rechaza la imagen, esa versión se queda sin imagen: se corrige y sale en la siguiente.

## Ajustes de GitHub

No viven en el código, así que hay que repetirlos si se recrea el repositorio.

1. **Protección de `main`:**

   ```bash
   gh api -X PUT repos/leandroAmariles/quipux-playlist-backend/branches/main/protection \
     --input .github/branch-protection.json
   gh api repos/leandroAmariles/quipux-playlist-backend/branches/main/protection
   ```

2. **Token de release-please:** en GitHub, **Settings → Developer settings → Fine-grained tokens**, crea uno limitado a
   este repositorio con **Contents**, **Pull requests** e **Issues** en lectura y escritura. Guárdalo como secreto con
   `gh secret set RELEASE_PLEASE_TOKEN -R leandroAmariles/quipux-playlist-backend`, que lo pide sin mostrarlo. Cuando
   venza, el workflow `Release` falla: genera otro y sustituye el secreto.
3. **Imagen pública:** tras la primera publicación, abre el paquete y usa **Package settings → Change visibility →
   Public**. En GitHub no se puede volver a privado.
4. **Token para abrir PR en infra (`INFRA_PR_TOKEN`):** crea un PAT de grano fino limitado **exclusivamente** a
   `leandroAmariles/quipux-playlist-infra` con **Contents** y **Pull requests** en lectura y escritura, sin permisos sobre
   este repositorio, y guárdalo con `gh secret set INFRA_PR_TOKEN -R leandroAmariles/quipux-playlist-backend`.

## Documentación interactiva

Con la API en marcha:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Contrato OpenAPI: <http://localhost:8080/v3/api-docs>

Se abren sin token. Para probar una operación protegida:

1. Ejecuta `POST /auth/login` con **Try it out**, usando `admin` o `user` y la contraseña que elegiste.
2. Copia el `accessToken` de la respuesta.
3. Pulsa **Authorize**, pégalo y confirma.
4. Ejecuta cualquier operación.

## Colección de Postman

[`postman/quipux-playlist.postman_collection.json`](postman/quipux-playlist.postman_collection.json) recorre las siete
operaciones y los errores principales, y comprueba cada código de respuesta.

1. En Postman, **Import** y elige el archivo.
2. Abre la colección y, en **Variables**, escribe `userPassword` y `adminPassword` (las de `DEMO_USER_PASSWORD` y
   `DEMO_ADMIN_PASSWORD`) y guarda. Si la API no usa el puerto 8080, cambia `baseUrl`.
3. Pulsa **Run** para ejecutarla completa.

Se puede repetir sin reiniciar la API. El token dura 30 minutos: si vence, vuelve a ejecutar la carpeta de sesión.

Desde la terminal, con Node:

```bash
npx --yes newman@6.2.3 run postman/quipux-playlist.postman_collection.json \
  --env-var userPassword="$DEMO_USER_PASSWORD" --env-var adminPassword="$DEMO_ADMIN_PASSWORD"
```

## Aplicación web

Está en [quipux-playlist-frontend](https://github.com/leandroAmariles/quipux-playlist-frontend), y su
[README](https://github.com/leandroAmariles/quipux-playlist-frontend#readme) explica cómo arrancarla. Necesita esta
API en `http://localhost:8080`.

## API

| Método y ruta | Quién | Respuestas |
|---|---|---|
| `POST /auth/login` | cualquiera | `200` con el token; `400`, `401`, `413`, `415` |
| `GET /lists` | `USER`, `ADMIN` | `200` |
| `GET /lists/{listName}` | `USER`, `ADMIN` | `200`; `400`, `404` |
| `GET /genres` | `USER`, `ADMIN` | `200` |
| `GET /lists/{listName}/recommendations` | `USER`, `ADMIN` | `200`; `400`, `404`, `429` |
| `POST /lists` | `ADMIN` | `201` con `Location`; `400`, `409`, `413`, `415` |
| `DELETE /lists/{listName}` | `ADMIN` | `204`; `400`, `404` |

Salvo el inicio de sesión, todo exige `Authorization: Bearer <token>`: sin un token válido y vigente la respuesta es
`401`, y sin el rol necesario, `403`. Los errores son `ProblemDetail` (RFC 9457).

Los nombres de lista no admiten `/`, `\`, caracteres de control, `.` ni `..`, ni contener `%2F`, `%5C`, `%00`, `%0A`,
`%0D` (en mayúsculas o minúsculas) o los separadores Unicode U+2028 y U+2029.

`GET /genres` devuelve `generos` y su `origen` (`SPOTIFY` o `LOCAL`). Al crear una lista, cada `genero` debe estar en
ese catálogo, sin distinguir mayúsculas.

### Sondas de salud (Kubernetes)

Spring Boot Actuator expone sin token únicamente las sondas no bloqueantes de liveness y readiness (`{"status":"UP"}`,
sin detalles internos); `/actuator/health` y el resto de `/actuator/**` siguen exigiendo autenticación (`401`):

| Método y ruta | Quién | Respuestas |
|---|---|---|
| `GET /actuator/health/liveness` | cualquiera (sin token) | `200` (`{"status":"UP"}`) |
| `GET /actuator/health/readiness` | cualquiera (sin token) | `200` (`{"status":"UP"}`) |
| `GET /actuator/health`, `/actuator/**` | protegido | `401` sin token |

## Recomendaciones con IA

`GET /lists/{listName}/recommendations` sugiere hasta 5 canciones de un catálogo propio, afines a las de la lista. Se
buscan en PostgreSQL las 20 canciones del catálogo más cercanas que no estén en ella y el modelo elige entre esas.

```json
{
  "lista": "Lista 1",
  "origen": "IA",
  "versionPrompt": "v2",
  "recomendaciones": [
    { "idCatalogo": "<uuid>", "titulo": "...", "artista": "...", "genero": "jazz", "motivo": "..." }
  ]
}
```

| `origen` | Cuándo | `idCatalogo` | `versionPrompt` |
|---|---|---|---|
| `IA` | el modelo eligió al menos una candidata | del catálogo | `v2` |
| `SIMILITUD` | hay candidatas, pero el modelo falla o no da nada válido: las 5 más cercanas | del catálogo | vacío |
| `RESPALDO` | no hay candidatas o la IA no se usa: [lista por defecto](infrastructure/src/main/resources/default-recommendations.properties) | vacío | vacío |

- Con `IA` y `SIMILITUD`, título, artista y género salen del catálogo; del modelo solo se toman la elección y el motivo.
  El motivo lo escribe un modelo: trátalo como texto, no como HTML.
- **Desactivada por defecto.** La aplicación arranca y las pruebas pasan sin modelos, clave ni PostgreSQL; entonces
  responde siempre con `RESPALDO`.

### Catálogo

[`catalog/songs.json`](infrastructure/src/main/resources/catalog/songs.json) tiene 214 canciones reunidas con ayuda de
IA, sin datos de Spotify y con géneros de la lista local asignados a mano. Título, artista, álbum y año se contrastaron
con [MusicBrainz](https://musicbrainz.org) (datos básicos, CC0): **las 214 coinciden**; las 14 que no coincidían se retiraron, como
detalla [`docs/catalog-verification.md`](docs/catalog-verification.md). Para repetir la comprobación:

```bash
MUSICBRAINZ_CONTACT="<tu contacto>" python tools/catalog/verify_catalog.py
```

La aplicación valida el fichero al arrancar: de 200 a 500 canciones completas, sin repetidas y con géneros conocidos.

### Indexación

La indexación calcula los embeddings y los guarda en pgvector. Es un proceso aparte de la API
(`java -jar playlist-bootstrap-*.jar index-catalog`, con las mismas variables de IA y catálogo) que termina con código
0 o 1 e informa de cuántas canciones añadió, conservó y quitó.

| Para | Con la composición de infra (`DC` = `docker compose -p quipux-ia -f compose.yaml -f compose.ai.yaml`) |
|---|---|
| Automática | se ejecuta sola al levantarla (servicio `catalog-index`) |
| Repetir | `$DC run --rm catalog-index`; sin cambios no pide ningún embedding |
| Tras cambiar `songs.json` | `$DC up -d --build` y reiniciar `api` |
| Desde cero (por ejemplo, otro modelo tras el mismo alias) | detener, borrar el volumen `catalog-data`, levantar y reiniciar `api` |

Cada canción se identifica por su contenido y por el modelo de embeddings. La aplicación no detecta que cambie el modelo
detrás del mismo alias. Límite conocido: con campos muy largos, el embedding de la consulta solo tiene en cuenta las
primeras canciones de la lista.

El modelo de chat por defecto, `llama3.2:3b`, apenas elige mejor que al azar entre las candidatas (que ya son las más
cercanas). Con GPU, `OLLAMA_CHAT_MODEL=qwen2.5:7b` elige bastante mejor; en CPU supera el tiempo máximo.

### Activarla

Lo más sencillo es la composición de
[quipux-playlist-infra](https://github.com/leandroAmariles/quipux-playlist-infra#recomendaciones-con-ia-modelos-locales),
que levanta Ollama y LiteLLM, descarga los modelos y explica `AI_API_KEY`, la GPU, la memoria de Docker, los
requisitos de hardware y las latencias medidas.

Sin esa composición hace falta un LiteLLM accesible con el alias `recomendador-chat`:

```bash
export AI_PROVIDER=openai
export AI_API_KEY="<clave de LiteLLM>"
export AI_BASE_URL=http://localhost:4000/v1
```

Para recomendar hacen falta además el alias `recomendador-embeddings` y el catálogo indexado en PostgreSQL
(`CATALOG_DB_URL` y `CATALOG_DB_PASSWORD`).

Con `AI_PROVIDER=openai` y sin `AI_API_KEY`, la aplicación arranca, lo avisa en el registro y usa el respaldo. No
definas `OPENAI_LOG`: el registro de depuración de la librería puede escribir el prompt.

### Cuándo se degrada

| Fallo | Resultado |
|---|---|
| Lista vacía, IA desactivada o sin clave, catálogo sin configurar, detenido, lento o sin indexar, fallo de los embeddings, sin candidatas | `RESPALDO` |
| Modelo de chat: error, tiempo agotado, respuesta vacía o no válida, demasiadas llamadas simultáneas o cortacircuitos abierto | `SIMILITUD` |

Siempre con `200`; una lista inexistente sigue siendo `404` y un fallo de la base de datos de listas, `500`.
`AI_TIMEOUT` cubre la búsqueda y el modelo; la búsqueda tiene además su propio máximo
(`CATALOG_RETRIEVAL_TIMEOUT`). Cada petición envía como máximo una petición de embeddings y una de chat, sin
reintentos.

### Límites

| Límite | Valor | Propiedad `playlist.ai.` |
|---|---|---|
| Peticiones por usuario y minuto; después, `429` con `Retry-After` | 10 | `requests-per-minute` |
| Llamadas simultáneas al modelo; no se encolan | 2 | `max-concurrent-calls` |
| Canciones enviadas, las primeras | 50 | `max-songs` |
| Caracteres por campo enviado | 100 | `max-field-length` |
| Tokens de la respuesta | 600 | `max-output-tokens` |
| Respuestas de la IA guardadas | 500, durante 24 h | `cache.max-entries`, `cache.ttl` |
| Cortacircuitos: fallos que lo abren y espera | 50 % de 10 (mínimo 5), 30 s | `circuit-breaker.*` |

La caché y el contador de peticiones viven en memoria: se pierden al reiniciar y no se comparten entre réplicas.

Del catálogo se ajustan con `playlist.catalog.`: `candidates` (20), `retrieval-timeout` (5 s), `index-batch-size`
(50) y `pool.*` (4 conexiones; 2 s para conectar, 4 s de socket y 3 s por consulta). La respuesta guardada depende
también de la versión del catálogo.

El prompt está en [`prompts/recommendations/v2.txt`](infrastructure/src/main/resources/prompts/recommendations/v2.txt);
`v1.txt`, el de HU-12, se conserva como referencia.
La lista por defecto necesita al menos 5 canciones completas con géneros de la lista local: si cambias
`playlist.genres.local-file`, revisa también `playlist.ai.default-recommendations-file`, o la aplicación no arrancará.

### Prompt y trazas en Langfuse

Es opcional. Sin `LANGFUSE_HOST` y las dos claves, el prompt es el del repositorio y no se consulta nada.

| Variable | Para qué |
|---|---|
| `LANGFUSE_HOST` | dirección de Langfuse |
| `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY` | claves del proyecto; solo por entorno |
| `AI_TRACE_SESSION` | agrupa las trazas, por ejemplo las de una evaluación |

- El archivo de Git sigue siendo la fuente oficial. `python tools/langfuse/publish_prompt.py` lo publica como
  `recomendaciones-v2` con la etiqueta `production` y el commit; no publica cambios sin confirmar ni repite versiones.
- El backend usa la versión `production` y `versionPrompt` pasa a ser `v2-lf<n>`. Si Langfuse falla, tarda más de
  1 s, no tiene el prompt o su texto no respeta el formato, usa el archivo (`v2`). El resultado, bueno o malo, se
  guarda 60 s (`playlist.ai.langfuse.*`).
- Las trazas las envía LiteLLM, una por llamada de chat, con la versión del prompt como versión de la traza. No
  incluyen el texto de los mensajes.

### Evaluación

[`evaluation/`](evaluation/README.md) mide las recomendaciones con RAGAS y comprobaciones deterministas:
`uv run python evaluate.py`. La línea base está en [`evaluation/BASELINE.md`](evaluation/BASELINE.md).

## Autoría IA

Esta sección describe HU-12 (recomendaciones con IA), HU-13 (catálogo) y HU-14 (evaluación y trazas). El registro está en [`AI-LOG.md`](AI-LOG.md).

En HU-14 el asistente de IA escribió la especificación, el plan, las tareas, el código, las pruebas, los casos de
evaluación, la composición de Langfuse y esta documentación. La persona usuaria pidió la historia completa y
rápida. **Ninguna persona ha revisado todavía el código, los casos ni las puntuaciones.**

En HU-13 el asistente de IA escribió la especificación, el plan, las tareas, el código, las pruebas, la composición, la
lista de canciones y esta documentación. **Ninguna persona ha revisado todavía el código ni la lista de canciones.**

| Parte | Quién |
|---|---|
| Historia, ADR-0002 y criterios de aceptación | documentos de partida |
| Especificación, plan, tareas, código, pruebas, composición de infra y esta documentación | asistente de IA (Claude Code, modelo Claude Sonnet 5.5) |
| Decisiones durante la implementación: proceder con el checklist incompleto, generar el cliente con un subagente, aceptar el aviso ZDI-26-1031, plantilla de `AI-LOG.md` y reutilizar los modelos ya descargados | la persona usuaria |

- El cliente se generó primero con un asistente aislado de la investigación (un subagente nuevo del mismo modelo). Su
  respuesta se conserva sin cambios en `AI-LOG.md`. **No compilaba** y, una vez corregido lo mínimo para compilar,
  incumplía varios requisitos; la revisión y los cambios también están allí.
- La revisión crítica la hizo el asistente, apoyada en ejecuciones. **A la fecha de esta entrega ninguna persona ha
  revisado el código ni ha añadido pruebas a mano.**

## Limitación de Spotify

**Contra el Spotify real, los géneros salen siempre de una lista local (`origen: LOCAL`).**

Con las credenciales de la aplicación registrada para este proyecto, comprobado el 2026-11-26:

| Consulta a Spotify | Respuesta |
|---|---|
| Token con *client credentials* | se obtiene |
| `GET /v1/recommendations/available-genre-seeds` | `404` |
| `GET /v1/browse/categories` | `403` |

La integración sigue el formato documentado de `available-genre-seeds`. También se usa la
[lista local](infrastructure/src/main/resources/local-genres.properties) si Spotify falla, tarda más de 3 segundos o no
hay credenciales.

### Ver el origen `SPOTIFY` con un servicio simulado

Levanta en otro puerto, por ejemplo con [WireMock](https://wiremock.org/docs/standalone/java-jar/), un servicio que
responda:

| Petición | Respuesta JSON |
|---|---|
| `POST /api/token` | `{"access_token":"simulado","token_type":"bearer","expires_in":3600}` |
| `GET /v1/recommendations/available-genre-seeds` | `{"genres":["acoustic","rock"]}` |

Antes de arrancar la API, apunta las dos direcciones a ese puerto y define unas credenciales cualesquiera: sin ellas
no se llama a ningún servicio.

```bash
export SPOTIFY_ACCOUNTS_BASE_URL=http://localhost:9090
export SPOTIFY_API_BASE_URL=http://localhost:9090
export SPOTIFY_CLIENT_ID=simulado
export SPOTIFY_CLIENT_SECRET=simulado
```

Las pruebas automáticas ya cubren este caso sin pasos manuales.

## Estructura

| Módulo | Contenido | Puede depender de |
|---|---|---|
| `domain` | Entidades y reglas | Nada |
| `application` | Casos de uso y puertos | `domain`, Reactor |
| `infrastructure` | Adaptadores | `application`, `domain` |
| `bootstrap` | Arranque y ensamblaje | Todos |

## Repositorios

| Repositorio | Contenido |
|---|---|
| [quipux-playlist-backend](https://github.com/leandroAmariles/quipux-playlist-backend) (este) | API y documentación |
| [quipux-playlist-frontend](https://github.com/leandroAmariles/quipux-playlist-frontend) | Aplicación web Angular |
| [quipux-playlist-infra](https://github.com/leandroAmariles/quipux-playlist-infra) | Docker Compose, Kubernetes (Kustomize) y Argo CD |

## Decisiones y especificaciones

- [ADR-0001: arquitectura](docs/adr/0001-arquitectura-api-listas-reproduccion.md)
- [Backlog](docs/backlog.md)
- [Especificaciones por historia](specs/), con sus contratos y casos de prueba HTTP
