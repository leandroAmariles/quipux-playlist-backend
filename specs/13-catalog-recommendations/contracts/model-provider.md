# Contrato con la pasarela de modelos

Base: [HU-12](../../12-ai-recommendations/contracts/model-provider.md). Autenticación, un solo intento y registros no
cambian.

## Embeddings: `POST /v1/embeddings` (nuevo)

```json
{ "model": "recomendador-embeddings", "input": ["Take Five | The Dave Brubeck Quartet | Time Out | 1959 | jazz"],
  "encoding_format": "base64" }
```

| Uso | `input` | Tiempo máximo |
|---|---|---|
| Consulta | Un texto: las canciones de la muestra, una por línea | 5 s |
| Indexación | Hasta 50 textos, uno por canción | 120 s |

- La pasarela descarta `encoding_format` y devuelve vectores de 768 números.
- Una petición por consulta, sin reintentos. Los campos ausentes quedan vacíos en el texto.

## Chat: `POST /v1/chat/completions` (cambia)

`model`, `max_tokens` 600, `stream: true` y:

```json
"response_format": {
  "type": "json_schema",
  "json_schema": { "name": "json_schema", "strict": true, "schema": {
    "type": "object", "additionalProperties": false, "required": ["recomendaciones"],
    "properties": { "recomendaciones": { "type": "array", "minItems": 1, "maxItems": 5, "items": {
      "type": "object", "additionalProperties": false, "required": ["idCatalogo", "motivo"],
      "properties": { "idCatalogo": { "type": "string", "enum": ["c1", "c2"] }, "motivo": { "type": "string" } }
    } } }
  } }
}
```

`enum` lleva los identificadores cortos de las candidatas de esa petición, en orden de cercanía.

### Mensajes

- Sistema: el texto de `prompts/recommendations/v2.txt` anterior a los bloques de datos.
- Usuario: `<lista>[…]</lista>` y, en la línea siguiente, `<candidatas>[…]</candidatas>`.
  - Canción: `titulo`, `artista`, `album`, `anno`, `genero` (muestra y recortes de HU-12).
  - Candidata: `idCatalogo`, `titulo`, `artista`, `genero`, `anno`.
  - `<` y `>` se escriben como `\u003c` y `\u003e` en los dos bloques.

### Interpretación

- Un objeto JSON sin texto sobrante, con el arreglo `recomendaciones`; si no, `SIMILITUD`.
- Un elemento sin `idCatalogo` de texto, o con `motivo` de otro tipo, se descarta solo; `motivo` puede faltar.
- Un identificador corto desconocido se descarta. Los demás campos se ignoran.

## Fallos

| Fallo | Resultado | ¿Cuenta en el cortacircuitos? |
|---|---|---|
| Embeddings: error, conexión, tiempo, otra dimensión | `RESPALDO` | No |
| PostgreSQL: caído, lento, sin esquema | `RESPALDO` | No |
| Sin candidatas | `RESPALDO` | No |
| Chat: error HTTP, conexión, tiempo | `SIMILITUD` | Sí |
| Chat: vacío, JSON inválido, sin identificador válido | `SIMILITUD` | No |
| Chat: cortacircuitos abierto o máximo de llamadas | `SIMILITUD` | No |

Ningún mensaje de error de la pasarela se registra ni se conserva: puede repetir el prompt.
