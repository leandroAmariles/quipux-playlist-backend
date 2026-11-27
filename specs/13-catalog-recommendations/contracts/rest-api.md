# Contrato REST: cambios sobre HU-12

Base: [contrato de HU-12](../../12-ai-recommendations/contracts/rest-api.md). Ruta, roles, forma, errores, límite y
`HEAD` no cambian.

## `GET /lists/{listName}/recommendations` → `200`

```json
{
  "lista": "Lista 1",
  "origen": "IA",
  "versionPrompt": "v2",
  "recomendaciones": [
    {
      "idCatalogo": "2f1c9f0a-6d1e-3c2b-9a4f-0b7c5d8e1a23",
      "titulo": "Take Five",
      "artista": "The Dave Brubeck Quartet",
      "genero": "jazz",
      "motivo": "Jazz de la misma época"
    }
  ]
}
```

| `origen` | `idCatalogo` | `versionPrompt` | `motivo` | Número |
|---|---|---|---|---|
| `IA` | UUID | `v2` | Del modelo, hasta 120; puede ir vacío | 1 a 5 |
| `SIMILITUD` | UUID | `""` | `Similar a las canciones de la lista` | 1 a 5 |
| `RESPALDO` | `""` | `""` | De la lista por defecto | 0 a 5 |

`titulo`, `artista` y `genero` de `IA` y `SIMILITUD` son los del catálogo.

## Documentación que cambia

- `ApiOperationDocEnum` y los esquemas de la respuesta: origen `SIMILITUD` y significado de `idCatalogo`.
- Las listas acordadas de las pruebas de OpenAPI y Postman, y la descripción de la petición en la colección.
