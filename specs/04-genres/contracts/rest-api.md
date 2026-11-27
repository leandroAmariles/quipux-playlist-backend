# Contrato REST: Géneros

## `GET /genres`

Exige `Authorization: Bearer <token>` con rol `USER` o `ADMIN`.

`200 OK`:

```json
{ "generos": ["acoustic", "rock", "salsa"], "origen": "LOCAL" }
```

| Campo | Contenido |
|---|---|
| `generos` | textos de 1 a 100 caracteres, sin repetidos; nunca vacío |
| `origen` | `SPOTIFY` o `LOCAL` |

| Estado | Cuándo |
|---|---|
| `200` | siempre que el token sea válido, funcione Spotify o no |
| `401` | sin token, o inválido o vencido |
| `403` | token sin un rol reconocido |

## `POST /lists` (cambia respecto de HU-02)

Además de las reglas de HU-02, cada `genero` no vacío debe estar en el catálogo que devolvería `GET /genres`, sin
distinguir mayúsculas.

`400 Bad Request`:

```json
{
  "status": 400,
  "title": "Bad Request",
  "detail": "A song genre is not in the genre catalog",
  "code": "PLAYLIST_GE_00001",
  "instance": "/lists"
}
```

Orden de las comprobaciones: `401`/`403` → reglas de HU-02 (`400`) → género (`400`) → nombre repetido (`409`).

## Contrato con Spotify (lo que simula WireMock)

1. `POST {accounts}/api/token`, `Authorization: Basic base64(id:secret)`, cuerpo de formulario
   `grant_type=client_credentials`. Respuesta: `{"access_token": "...", "token_type": "bearer", "expires_in": 3600}`.
2. `GET {api}/v1/recommendations/available-genre-seeds`, `Authorization: Bearer <access_token>`. Respuesta:
   `{"genres": ["acoustic", "afrobeat"]}`.
