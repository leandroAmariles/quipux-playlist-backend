# Contrato: documentación OpenAPI

Base: `http://localhost:8080`. El comportamiento de las operaciones no cambia: lo definen los contratos de
[listas](../../02-playlist-management/contracts/rest-api.md),
[autenticación](../../03-authentication-authorization/contracts/rest-api.md) y
[géneros](../../04-genres/contracts/rest-api.md).

## Rutas nuevas

Todas públicas y solo `GET`.

| Ruta | Respuesta |
|---|---|
| `/v3/api-docs` | `200`, contrato OpenAPI 3.1 en JSON |
| `/v3/api-docs/swagger-config` | `200`, configuración de la interfaz |
| `/swagger-ui.html` | `302` a `/swagger-ui/index.html` |
| `/swagger-ui/**` | `200`, archivos de la interfaz |

Siguen exigiendo token (`401` sin él): `/v3/api-docs.yaml`, `/webjars/**`, cualquier otro método sobre las rutas de
arriba y el resto de la API.

## Contenido del contrato

- `info`: título, descripción en español y la versión del proyecto.
- `components.securitySchemes.bearerAuth`: `type: http`, `scheme: bearer`, `bearerFormat: JWT`.
- Todas las operaciones salvo el inicio de sesión llevan `security: [bearerAuth]` e indican el rol en su descripción.
- Respuestas correctas en `application/json`; errores en `application/problem+json` con el esquema `Problem`.
- `201` de crear: cabecera `Location`. `204` de borrar: sin cuerpo.

## Operaciones

| Operación | Rol | Éxito | Errores (`code`) |
|---|---|---|---|
| `POST /auth/login` | público | `200` | `400` (`PLAYLIST_GE_00001`), `401` (`AUTH_ERROR_001`), `413`, `415`, `500` |
| `GET /lists` | `USER`, `ADMIN` | `200` | `401`, `403`, `500` |
| `GET /lists/{listName}` | `USER`, `ADMIN` | `200` | `400`, `401`, `403`, `404` (`PLAYLIST_GE_00002`), `500` |
| `GET /genres` | `USER`, `ADMIN` | `200` | `401`, `403`, `500` |
| `POST /lists` | `ADMIN` | `201` | `400`, `401`, `403`, `409` (`PLAYLIST_GE_00003`), `413`, `415`, `500` |
| `DELETE /lists/{listName}` | `ADMIN` | `204` | `400`, `401`, `403`, `404` (`PLAYLIST_GE_00002`), `500` |

En las rutas protegidas: `401` es `AUTH_ERROR_002`, `403` es `AUTH_ERROR_003`, `400` es `PLAYLIST_GE_00001` y `500` es
`PLAYLIST_GE_99999`. `413` y `415` no llevan `code`.

El contrato no publica ninguna otra operación.
