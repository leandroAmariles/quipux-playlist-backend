# Contrato REST: recomendaciones

Base: `http://localhost:8080`.

## Operación

| Método y ruta | Rol | Éxito | Errores |
|---|---|---|---|
| `GET /lists/{listName}/recommendations` | `USER`, `ADMIN` | `200` | `400`, `401`, `403`, `404`, `429`, `500` |

- `{listName}` se codifica y se compara igual que en `GET /lists/{listName}`.
- `HEAD` se comporta como `GET` y consume cupo.
- Sin cuerpo ni parámetros.

## Respuesta `200`

`Content-Type: application/json`. Todos los campos están siempre presentes.

Del modelo:

```json
{
  "lista": "Lista 1",
  "origen": "IA",
  "versionPrompt": "v1",
  "recomendaciones": [
    {
      "idCatalogo": "",
      "titulo": "Song 2",
      "artista": "Another artist",
      "genero": "rock",
      "motivo": "Mismo género y época"
    }
  ]
}
```

De respaldo:

```json
{ "lista": "Lista 1", "origen": "RESPALDO", "versionPrompt": "", "recomendaciones": [] }
```

| Campo | Tipo | Máximo | Notas |
|---|---|---|---|
| `lista` | texto | 255 | Nombre pedido |
| `origen` | `IA` \| `RESPALDO` | — | `SIMILITUD` llega con HU-13 |
| `versionPrompt` | texto | — | Vacío si `origen` no es `IA` |
| `recomendaciones` | arreglo | 5 | Con `IA`, de 1 a 5; con `RESPALDO`, de 0 a 5 |
| `idCatalogo` | texto | — | Siempre vacío en esta etapa |
| `titulo`, `artista` | texto | 255 | Nunca vacíos |
| `genero` | texto | 100 | Puede ir vacío; no se comprueba contra el catálogo |
| `motivo` | texto | 120 | Puede ir vacío |

Los textos de una respuesta `IA` los escribe un modelo: quien los muestre debe tratarlos como texto, no como HTML.

## Errores

`Content-Type: application/problem+json`.

| Estado | `code` | Cabecera | Cuándo |
|---|---|---|---|
| `400` | `PLAYLIST_GE_00001` | — | Dirección rechazada por la protección de rutas; antes de comprobar el token |
| `401` | `AUTH_ERROR_002` | `WWW-Authenticate: Bearer` | Token ausente, no válido o vencido |
| `403` | `AUTH_ERROR_003` | — | Token sin rol permitido |
| `429` | `RECOMMENDATION_ERROR_001` | `Retry-After: <segundos>` | Más de 10 peticiones del usuario en un minuto |
| `404` | `PLAYLIST_GE_00002` | — | La lista no existe |
| `500` | `PLAYLIST_GE_99999` | — | Error inesperado, incluido el de la base de datos |

```json
{
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Too many recommendation requests; try again later",
  "instance": "/lists/Lista%201/recommendations",
  "code": "RECOMMENDATION_ERROR_001"
}
```

Orden: `400` → `401` → `403` → `429` → `404` / `500` → `200`.

## Archivos de contrato que cambian

| Archivo (`bootstrap/src/test/resources`) | Añade |
|---|---|
| `openapi-operations.properties` | La operación con sus estados y códigos, y los campos de los dos DTO nuevos |
| `postman-collection.properties` | La operación y sus peticiones: `200` (tras crear la lista) y `404`. No se exige una petición que provoque `429` |

La colección conserva el orden de ejecución y no envía más de 10 recomendaciones por minuto.
