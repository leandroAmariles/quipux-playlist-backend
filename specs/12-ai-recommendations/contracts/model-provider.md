# Contrato con el proveedor de modelos

El backend habla solo con la API compatible con OpenAI de LiteLLM. Es lo que simula WireMock.

## Petición

```text
POST {AI_BASE_URL}/chat/completions          (AI_BASE_URL termina en /v1)
Authorization: Bearer {AI_API_KEY}
Content-Type: application/json
```

```json
{
  "model": "recomendador-chat",
  "messages": [
    { "role": "system", "content": "<instrucciones de v1.txt>" },
    { "role": "user", "content": "<lista>[{\"titulo\":\"Song 1\",\"artista\":\"Artist\"}]</lista>" }
  ],
  "max_tokens": 600,
  "response_format": { "type": "json_object" },
  "stream": true,
  "stream_options": { "include_usage": true }
}
```

Como máximo una petición por recomendación, sin reintentos. Nunca se envía el nombre ni la descripción de la lista, ni
datos del usuario.

## Respuesta esperada

`200`, `text/event-stream`:

```text
data: {"id":"1","object":"chat.completion.chunk","created":1,"model":"m","choices":[{"index":0,"delta":{"content":"{\"recomendaciones\":["}}]}

data: {"id":"1","object":"chat.completion.chunk","created":1,"model":"m","choices":[{"index":0,"delta":{"content":"]}"}}]}

data: [DONE]
```

El texto agregado debe ser exactamente:

```json
{ "recomendaciones": [ { "titulo": "", "artista": "", "genero": "", "motivo": "" } ] }
```

## Fallos

Todos terminan en `200` con `RESPALDO` hacia el usuario.

| Situación | Motivo | ¿Cuenta para el cortacircuitos? |
|---|---|---|
| No termina en `playlist.ai.timeout` | `TIMEOUT` | Sí |
| No se puede conectar o la conexión se corta | `UNREACHABLE` | Sí |
| `4xx` (incluidos `401` y `429`) o `5xx` | `ERROR_RESPONSE` | Sí |
| `200` sin texto (vacío o no es SSE) | `EMPTY` | No |
| Texto que no es el JSON esperado | `UNREADABLE` | No |
| JSON correcto sin sugerencias válidas | `NO_VALID_SUGGESTIONS` | No |
| Cortacircuitos abierto (no se llama) | `CIRCUIT_OPEN` | — |
| 2 llamadas en curso (no se llama) | `TOO_MANY_CALLS` | No |
| IA desactivada o sin clave (no se llama) | `DISABLED`, `NOT_CONFIGURED` | — |
