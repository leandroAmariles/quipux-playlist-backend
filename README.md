# quipux-playlist-backend

API REST reactiva para crear, consultar y borrar listas de reproducción de música, con autenticación JWT, roles y
géneros desde Spotify. Tiene una [aplicación web](https://github.com/leandroAmariles/quipux-playlist-frontend) en otro
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

Las tres obligatorias no tienen valor por defecto para que no haya secretos en el repositorio: sin ellas la aplicación
no arranca. Las contraseñas las eliges tú.

## Ejecución

En Linux, macOS o Git Bash:

```bash
./mvnw package
export JWT_SECRET="$(openssl rand -base64 48)"
export DEMO_USER_PASSWORD="<elige una>"
export DEMO_ADMIN_PASSWORD="<elige otra>"
java -jar bootstrap/target/playlist-bootstrap-0.1.0-SNAPSHOT.jar
```

En PowerShell:

```powershell
.\mvnw.cmd package
$env:JWT_SECRET = "<una clave larga, de 32 caracteres o más>"
$env:DEMO_USER_PASSWORD = "<elige una>"
$env:DEMO_ADMIN_PASSWORD = "<elige otra>"
java -jar bootstrap/target/playlist-bootstrap-0.1.0-SNAPSHOT.jar
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

[`postman/quipux-playlist.postman_collection.json`](postman/quipux-playlist.postman_collection.json) recorre las seis
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
| `POST /lists` | `ADMIN` | `201` con `Location`; `400`, `409`, `413`, `415` |
| `DELETE /lists/{listName}` | `ADMIN` | `204`; `400`, `404` |

Salvo el inicio de sesión, todo exige `Authorization: Bearer <token>`: sin un token válido y vigente la respuesta es
`401`, y sin el rol necesario, `403`. Los errores son `ProblemDetail` (RFC 9457).

Los nombres de lista no admiten `/`, `\`, caracteres de control, `.` ni `..`, ni contener `%2F`, `%5C`, `%00`, `%0A`,
`%0D` (en mayúsculas o minúsculas) o los separadores Unicode U+2028 y U+2029.

`GET /genres` devuelve `generos` y su `origen` (`SPOTIFY` o `LOCAL`). Al crear una lista, cada `genero` debe estar en
ese catálogo, sin distinguir mayúsculas.

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
| [quipux-playlist-infra](https://github.com/leandroAmariles/quipux-playlist-infra) | Despliegue: aún pendiente |

## Decisiones y especificaciones

- [ADR-0001: arquitectura](docs/adr/0001-arquitectura-api-listas-reproduccion.md)
- [Backlog](docs/backlog.md)
- [Especificaciones por historia](specs/), con sus contratos y casos de prueba HTTP
