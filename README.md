# quipux-playlist-backend

API REST reactiva para gestionar listas de reproducción de música.

**En construcción:** están las operaciones sobre listas. Faltan autenticación, géneros y la aplicación web.

> **Sin autenticación todavía:** hasta HU-03 cualquiera puede crear y borrar listas. No exponer en una red pública.

## API

| Método y ruta | Respuestas |
|---|---|
| `POST /lists` | `201` con `Location`; `400`, `409`, `413`, `415` |
| `GET /lists` | `200` |
| `GET /lists/{listName}` | `200`; `404` |
| `DELETE /lists/{listName}` | `204`; `404` |

```bash
curl -i -X POST http://localhost:8080/lists -H 'Content-Type: application/json' \
  -d '{"nombre":"Mis favoritas","descripcion":"Demo","canciones":[{"titulo":"Uno","anno":"1999"}]}'
curl -i http://localhost:8080/lists/Mis%20favoritas
```

Detalle en el [contrato REST](specs/02-playlist-management/contracts/rest-api.md) y casos de prueba en
[requests.http](specs/02-playlist-management/requests.http).

**Sobre el modelo no bloqueante:** la API es reactiva de extremo a extremo, pero el controlador `r2dbc-h2` ejecuta
las consultas en el mismo hilo que las pide. Con la base en memoria son operaciones cortas.

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

## Comandos

En Windows se usa `mvnw.cmd` en lugar de `./mvnw`.

```bash
# Compila, prueba y aplica los controles de calidad
./mvnw verify

# Empaqueta y arranca en el puerto 8080
./mvnw package
java -jar bootstrap/target/playlist-bootstrap-0.1.0-SNAPSHOT.jar
```

Si el puerto 8080 está ocupado, se cambia con `--server.port=18080` o con la variable `SERVER_PORT`.

La base de datos es H2 en memoria: no hay que preparar nada y los datos se pierden al detener la aplicación.

`verify` falla ante una violación de Checkstyle, una dependencia prohibida entre capas o menos del 80 % de líneas
cubiertas en un módulo.

## Estructura

| Módulo | Contenido | Puede depender de |
|---|---|---|
| `domain` | Entidades y reglas | Nada |
| `application` | Casos de uso y puertos | `domain`, Reactor |
| `infrastructure` | Adaptadores | `application`, `domain` |
| `bootstrap` | Arranque y ensamblaje | Todos |
