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
| `AI_TIMEOUT` | no | `30s` | tiempo máximo total de la llamada al modelo |

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

## Recomendaciones con IA

`GET /lists/{listName}/recommendations` sugiere hasta 5 canciones según las de la lista.

```json
{
  "lista": "Lista 1",
  "origen": "IA",
  "versionPrompt": "v1",
  "recomendaciones": [
    { "idCatalogo": "", "titulo": "...", "artista": "...", "genero": "rock", "motivo": "..." }
  ]
}
```

- `origen` es `IA` cuando responde el modelo y `RESPALDO` cuando se usa la
  [lista por defecto](infrastructure/src/main/resources/default-recommendations.properties).
- **Las sugerencias no están verificadas:** el modelo puede inventar canciones o equivocar el artista. `idCatalogo` va
  vacío hasta que exista un catálogo (HU-13). Los textos los escribe un modelo: trátalos como texto, no como HTML.
- **Desactivada por defecto.** La aplicación arranca y las pruebas pasan sin modelos ni clave; entonces responde
  siempre con `RESPALDO`.

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

Con `AI_PROVIDER=openai` y sin `AI_API_KEY`, la aplicación arranca, lo avisa en el registro y usa el respaldo. No
definas `OPENAI_LOG`: el registro de depuración de la librería puede escribir el prompt.

### Cuándo se usa el respaldo

Lista vacía, IA desactivada, más de `AI_TIMEOUT` sin respuesta, error de conexión o HTTP, respuesta vacía o que no es
el JSON esperado, ninguna sugerencia válida, demasiadas llamadas simultáneas o cortacircuitos abierto. Siempre con
`200`; una lista inexistente sigue siendo `404` y un fallo de la base de datos, `500`. Cada petición al endpoint envía
como máximo una petición al modelo, sin reintentos.

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

El prompt está en [`prompts/recommendations/v1.txt`](infrastructure/src/main/resources/prompts/recommendations/v1.txt).
La lista por defecto necesita al menos 5 canciones completas con géneros de la lista local: si cambias
`playlist.genres.local-file`, revisa también `playlist.ai.default-recommendations-file`, o la aplicación no arrancará.

## Autoría IA

Esta sección describe HU-12 (recomendaciones con IA). El registro completo está en [`AI-LOG.md`](AI-LOG.md).

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
| [quipux-playlist-infra](https://github.com/leandroAmariles/quipux-playlist-infra) | Docker Compose; Kubernetes y Argo CD, aún pendientes |

## Decisiones y especificaciones

- [ADR-0001: arquitectura](docs/adr/0001-arquitectura-api-listas-reproduccion.md)
- [Backlog](docs/backlog.md)
- [Especificaciones por historia](specs/), con sus contratos y casos de prueba HTTP
