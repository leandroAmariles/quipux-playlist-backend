# Contrato REST: listas de reproducción

Base: `http://localhost:8080`. Sin autenticación hasta HU-03.

## Representación

```json
{
  "nombre": "Mis favoritas",
  "descripcion": "Para trabajar",
  "canciones": [
    { "titulo": "Song 1", "artista": "Artist", "album": "Album", "anno": "1999", "genero": "rock" }
  ]
}
```

En las respuestas están siempre todos los campos: los opcionales sin valor salen como `null` y `canciones` como `[]`.
En las peticiones los campos desconocidos se ignoran.

## Operaciones

| Método y ruta | Éxito | Errores |
|---|---|---|
| `POST /lists` | `201`, cabecera `Location`, lista creada | `400`, `409`, `413`, `415` |
| `GET /lists` | `200`, arreglo de listas (puede ser `[]`) | — |
| `GET /lists/{listName}` | `200`, lista | `404` |
| `DELETE /lists/{listName}` | `204`, sin cuerpo | `404` |

- `POST` exige `Content-Type: application/json`.
- `Location` es relativa y codifica todo carácter reservado: `/lists/Mis%20favoritas`.
- `{listName}` se envía codificado y se compara exactamente (distingue mayúsculas y espacios).

## Errores

`Content-Type: application/problem+json` (RFC 9457).

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "A playlist with that name already exists",
  "instance": "/lists",
  "code": "PLAYLIST_GE_00003"
}
```

| `code` | Estado | Cuándo |
|---|---|---|
| `PLAYLIST_GE_00001` | `400` | Datos o cuerpo no válidos |
| `PLAYLIST_GE_00002` | `404` | La lista no existe |
| `PLAYLIST_GE_00003` | `409` | Nombre duplicado |
| `PLAYLIST_GE_99999` | `500` | Error inesperado; `detail` genérico, sin datos internos |

`413` y `415` los genera el framework, también como `ProblemDetail`, sin `code`.
