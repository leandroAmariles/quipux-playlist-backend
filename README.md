# quipux-playlist-backend

API REST reactiva para gestionar listas de reproducción de música.

**En construcción:** están las operaciones sobre listas, la autenticación, los géneros y la
[aplicación web](https://github.com/leandroAmariles/quipux-playlist-frontend).

## API

| Método y ruta | Quién | Respuestas |
|---|---|---|
| `POST /auth/login` | cualquiera | `200` con el token; `400`, `401`, `415` |
| `GET /lists` | `USER`, `ADMIN` | `200` |
| `GET /lists/{listName}` | `USER`, `ADMIN` | `200`; `404` |
| `GET /genres` | `USER`, `ADMIN` | `200` |
| `POST /lists` | `ADMIN` | `201` con `Location`; `400`, `409`, `413`, `415` |
| `DELETE /lists/{listName}` | `ADMIN` | `204`; `404` |

Salvo el inicio de sesión, todo exige `Authorization: Bearer <token>`: sin un token válido y vigente la respuesta es
`401`, y sin el rol necesario, `403`. El token dura 30 minutos.

Hay dos usuarios de demostración, `user` (rol `USER`) y `admin` (rol `ADMIN`). Sus contraseñas se eligen al arrancar.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$DEMO_ADMIN_PASSWORD\"}" | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

curl -i -X POST http://localhost:8080/lists -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"nombre":"Mis favoritas","descripcion":"Demo","canciones":[{"titulo":"Uno","anno":"1999"}]}'
curl -i http://localhost:8080/lists/Mis%20favoritas -H "Authorization: Bearer $TOKEN"
```

Los nombres de lista no admiten `/`, `\`, caracteres de control, `.` ni `..`, ni contener `%2F`, `%5C`, `%00`, `%0A`,
`%0D` (en mayúsculas o minúsculas) o los separadores Unicode U+2028 y U+2029.

`GET /genres` devuelve `generos` y su `origen` (`SPOTIFY` o `LOCAL`). Si Spotify falla, tarda más de 3 segundos o no
hay credenciales, responde con una lista local. Al crear una lista, cada `genero` debe estar en ese catálogo, sin
distinguir mayúsculas.

**Limitación de Spotify:** a la aplicación registrada para este proyecto, Spotify real no le entrega géneros (`404`),
así que el origen es `LOCAL`. El origen `SPOTIFY` se obtiene apuntando a un servicio simulado
([guía](specs/04-genres/quickstart.md)).

Detalle en los contratos de [géneros](specs/04-genres/contracts/rest-api.md), de [autenticación](specs/03-authentication-authorization/contracts/rest-api.md) y
de [listas](specs/02-playlist-management/contracts/rest-api.md), y casos de prueba en los `requests.http` de
[autenticación](specs/03-authentication-authorization/requests.http),
[listas](specs/02-playlist-management/requests.http) y [géneros](specs/04-genres/requests.http).

**Sobre el modelo no bloqueante:** la API es reactiva de extremo a extremo, pero el controlador `r2dbc-h2` ejecuta
las consultas en el mismo hilo que las pide. Con la base en memoria son operaciones cortas. BCrypt, que es lento a
propósito, se ejecuta en un hilo aparte.

## Repositorios

| Repositorio | Contenido |
|---|---|
| quipux-playlist-backend (este) | API, documentación y punto de entrada |
| [quipux-playlist-frontend](https://github.com/leandroAmariles/quipux-playlist-frontend) | Aplicación web Angular |
| [quipux-playlist-infra](https://github.com/leandroAmariles/quipux-playlist-infra) | Docker Compose, Kubernetes y Argo CD |

## Documentación

- [ADR-0001: arquitectura](docs/adr/0001-arquitectura-api-listas-reproduccion.md)
- [Backlog](docs/backlog.md)
- [Especificaciones por historia](specs/)

## Requisitos

- Java 21
- Acceso a internet la primera vez, para descargar Maven y las dependencias

No hace falta instalar Maven.

## Variables de entorno

La aplicación no arranca sin las tres primeras. No tienen valor por defecto, para que no haya secretos en el
repositorio.

| Variable | Contenido |
|---|---|
| `JWT_SECRET` | clave que firma los tokens, de 32 bytes como mínimo |
| `DEMO_USER_PASSWORD` | contraseña del usuario `user`, de 1 a 72 bytes |
| `DEMO_ADMIN_PASSWORD` | contraseña del usuario `admin`, de 1 a 72 bytes |
| `JWT_TTL` | opcional: vigencia del token (por defecto `30m`) |
| `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET` | opcionales: sin las dos se usa la lista local |
| `SPOTIFY_ACCOUNTS_BASE_URL`, `SPOTIFY_API_BASE_URL` | opcionales: para apuntar a un servicio simulado |
| `SPOTIFY_TIMEOUT` | opcional: tiempo máximo para obtener los géneros (por defecto `3s`) |

## Comandos

En Windows se usa `mvnw.cmd` en lugar de `./mvnw`.

```bash
# Compila, prueba y aplica los controles de calidad. No necesita variables de entorno ni conexión con Spotify
./mvnw verify

# Empaqueta y arranca en el puerto 8080
./mvnw package
export JWT_SECRET="$(openssl rand -base64 48)"
export DEMO_USER_PASSWORD="<elige una>"
export DEMO_ADMIN_PASSWORD="<elige otra>"
java -jar bootstrap/target/playlist-bootstrap-0.1.0-SNAPSHOT.jar
```

En PowerShell las variables se definen con `$env:JWT_SECRET = "..."`.

Si el puerto 8080 está ocupado, se cambia con `--server.port=18080` o con la variable `SERVER_PORT`.

La base de datos es H2 en memoria: no hay que preparar nada y los datos se pierden al detener la aplicación.

`verify` falla ante una violación de Checkstyle, una dependencia prohibida entre capas o menos del 80 % de líneas
cubiertas en un módulo.

No hay límite de intentos de inicio de sesión: la aplicación no debe exponerse en una red pública.

## Estructura

| Módulo | Contenido | Puede depender de |
|---|---|---|
| `domain` | Entidades y reglas | Nada |
| `application` | Casos de uso y puertos | `domain`, Reactor |
| `infrastructure` | Adaptadores | `application`, `domain` |
| `bootstrap` | Arranque y ensamblaje | Todos |
