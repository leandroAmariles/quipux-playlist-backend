# Contrato REST: Autenticación y autorización

Base: `http://localhost:8080`. Amplía el [contrato de HU-02](../../02-playlist-management/contracts/rest-api.md).

## `POST /auth/login`

Público. Ignora la cabecera `Authorization`.

```http
POST /auth/login
Content-Type: application/json

{ "username": "admin", "password": "<contraseña>" }
```

`200`:

```json
{ "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 1800 }
```

| Estado | Cuándo | `code` |
|---|---|---|
| `400` | cuerpo ausente o mal formado, tipos incorrectos, campo ausente, nulo o en blanco | `PLAYLIST_GE_00001` |
| `401` | usuario inexistente, contraseña incorrecta o de más de 72 bytes (respuesta idéntica) | `AUTH_ERROR_001` |
| `413` | cuerpo mayor que el límite | — |
| `415` | `Content-Type` distinto de JSON | — |

## Rutas protegidas

Todas las demás exigen `Authorization: Bearer <accessToken>`.

| Método y ruta | `USER` | `ADMIN` |
|---|---|---|
| `GET`, `HEAD` `/lists` y `/lists/{listName}` | permitido | permitido |
| `POST /lists` | `403` | permitido |
| `DELETE /lists/{listName}` | `403` | permitido |

"Permitido" significa las respuestas del contrato de HU-02.

## Errores de seguridad

`Content-Type: application/problem+json`.

| Estado | Cuándo | Cabecera | `code` |
|---|---|---|---|
| `401` | sin token, ilegible, alterado, sin firma, sin `exp` o vencido; otro esquema | `WWW-Authenticate: Bearer` | `AUTH_ERROR_002` |
| `403` | token válido sin el rol necesario | — | `AUTH_ERROR_003` |
| `400` | dirección rechazada por la protección de rutas; se responde antes de comprobar el token | — | `PLAYLIST_GE_00001` |

Ejemplo:

```json
{ "status": 401, "title": "Unauthorized", "detail": "Authentication is required", "code": "AUTH_ERROR_002" }
```

El detalle nunca indica por qué se rechazó el token. La comprobación de token y rol va antes que la del recurso y la
del cuerpo.

## Cambio respecto a HU-02

`POST /lists` responde además `400` (`PLAYLIST_GE_00001`) si el nombre contiene, sin distinguir mayúsculas, `%2F`,
`%5C`, `%00`, `%0A` o `%0D`, o los separadores U+2028 o U+2029.
