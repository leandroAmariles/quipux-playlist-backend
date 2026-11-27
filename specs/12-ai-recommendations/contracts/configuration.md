# Contrato de configuración (backend)

Bloques nuevos en `bootstrap/src/main/resources/application.yml`; no se modifica nada de lo existente.

## Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `AI_PROVIDER` | `none` | `openai` activa la IA contra LiteLLM |
| `AI_API_KEY` | vacía | Clave de LiteLLM. Sin ella no se llama |
| `AI_BASE_URL` | `http://localhost:4000/v1` | Dirección de LiteLLM |
| `AI_CHAT_MODEL` | `recomendador-chat` | Alias del modelo |
| `AI_TIMEOUT` | `30s` | Tiempo máximo total de la llamada. Con 60 s o más, revisar nginx |

## Propiedades `playlist.ai`

| Propiedad | Por defecto | Requisito |
|---|---|---|
| `timeout` | `${AI_TIMEOUT:30s}` | FR-017 |
| `chat-model` | `${AI_CHAT_MODEL:recomendador-chat}` | FR-010 |
| `prompt-version` | `v1` | FR-013 |
| `max-songs` | `50` | FR-016 |
| `max-field-length` | `100` | FR-016 |
| `max-output-tokens` | `600` | FR-016 |
| `max-concurrent-calls` | `2` | FR-028 |
| `requests-per-minute` | `10` | FR-027 |
| `cache.ttl` | `24h` | FR-026 |
| `cache.max-entries` | `500` | FR-026 |
| `circuit-breaker.window` | `10` | FR-020 |
| `circuit-breaker.minimum-calls` | `5` | FR-020 |
| `circuit-breaker.failure-rate` | `50` | FR-020 |
| `circuit-breaker.open-duration` | `30s` | FR-020 |
| `default-recommendations-file` | `classpath:default-recommendations.properties` | FR-021 |

## Propiedades fijas de Spring

| Propiedad | Valor |
|---|---|
| `spring.ai.model.chat` | `${AI_PROVIDER:none}` |
| `spring.ai.model.embedding`, `image`, `moderation`, `audio.speech`, `audio.transcription` | `none` |
| `spring.ai.openai.base-url` | `${AI_BASE_URL:http://localhost:4000/v1}` |
| `spring.ai.openai.api-key` | `${AI_API_KEY:}` (vacía, nunca ausente) |
| `spring.ai.openai.max-retries` | `0` |
| `spring.ai.openai.timeout` | `${playlist.ai.timeout}` |
| `spring.ai.openai.chat.model` | `${playlist.ai.chat-model}` |
| `spring.ai.openai.chat.max-tokens` | `${playlist.ai.max-output-tokens}` |
| `spring.ai.openai.chat.response-format.type` | `JSON_OBJECT` |
| `logging.level.org.springframework.ai.chat.model.MessageAggregator` | `"off"` |

Las pruebas fijan `spring.ai.model.chat: none` en su configuración para no depender del entorno de quien las ejecuta.
