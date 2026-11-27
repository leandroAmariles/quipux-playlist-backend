# Contrato de composición de la IA (repositorio de infra)

`compose.yaml` no se modifica.

## Archivos nuevos o ampliados en `quipux-playlist-infra`

| Archivo | Contenido |
|---|---|
| `compose/compose.ai.yaml` | Ollama, descarga de modelos, LiteLLM y activación en `api` |
| `compose/compose.ai.gpu.yaml` | Reserva de la GPU NVIDIA para `ollama` |
| `compose/litellm/config.yaml` | Alias, sin reintentos, sin registro de mensajes |
| `compose/.env.example` | Añade `AI_API_KEY=` |
| `README.md` | Uso, clave, modelos, GPU, memoria, requisitos y latencias |

## Servicios

| Servicio | Imagen (fijada por digest) | Puertos | Depende de | Salud |
|---|---|---|---|---|
| `ollama` | `ollama/ollama:0.40.18` | ninguno | — | `ollama list` |
| `ollama-init` | la misma | ninguno | `ollama` sano | termina con éxito |
| `litellm` | `ghcr.io/berriai/litellm:v1.111.0` | `127.0.0.1:${LITELLM_PORT:-4000}:4000` | `ollama-init` completado | `/health/liveliness` |
| `api` (se amplía) | la existente | sin cambios | **nada nuevo** | sin cambios |

Digests en [research.md](../research.md), R14.

## Variables

| Variable | Obligatoria con `compose.ai.yaml` | Por defecto | Destino |
|---|---|---|---|
| `AI_API_KEY` | Sí (falla con un mensaje si falta) | — | `LITELLM_MASTER_KEY` y `api` |
| `OLLAMA_CHAT_MODEL` | No | `llama3.2:3b` | `ollama-init` y LiteLLM |
| `OLLAMA_EMBEDDING_MODEL` | No | `nomic-embed-text` | `ollama-init` y LiteLLM |
| `AI_TIMEOUT`, `AI_CHAT_MODEL` | No | los del backend | `api` |
| `LITELLM_PORT` | No | `4000` | Puerto local |

En `api` se fijan `AI_PROVIDER=openai` y `AI_BASE_URL=http://litellm:4000/v1`.

La clave se genera de forma aleatoria con prefijo `sk-`: LiteLLM rechaza claves vacías o conocidas como inseguras.

## Volumen

`ollama-models`, montado en `/root/.ollama`. `down` lo conserva; `down -v` obliga a descargar de nuevo.

Con `compose.ai.yaml`, `AI_API_KEY` se usa también como clave maestra de LiteLLM: es válido solo en desarrollo local.

## Uso

```bash
cd quipux-playlist-infra/compose
docker compose -p quipux-ia -f compose.yaml -f compose.ai.yaml up -d --build
# con GPU
docker compose -p quipux-ia -f compose.yaml -f compose.ai.yaml -f compose.ai.gpu.yaml up -d --build
```
