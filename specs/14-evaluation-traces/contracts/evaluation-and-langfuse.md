# Contratos

La API HTTP no cambia. `versionPrompt` sigue siendo texto: `v2` (archivo) o `v2-lf<n>` (Langfuse), vacío si el origen no es `IA`.

## Modo interno

```text
java -jar playlist-bootstrap-*.jar evaluate-recommendations \
  --playlist.evaluation.cases=<cases.json> --playlist.evaluation.output=<raw.json>
```

Entrada: `[{"id","canciones":[{"titulo","artista","album","anno","genero"}],"esperadas":[{"titulo","artista"}]}]`

Salida:

```json
{"versionCatalogo":"…","modeloChat":"…","modeloEmbeddings":"…",
 "casos":[{"id":"…","origen":"IA","versionPrompt":"v2",
   "candidatas":[{"idCatalogo","titulo","artista","genero","anno"}],
   "recomendaciones":[{"idCatalogo","titulo","artista","genero","motivo"}]}]}
```

Código de salida 0 si hubo candidatas en algún caso; 1 en otro caso.

## Variables de entorno

| Variable | Uso | Por defecto |
|---|---|---|
| `LANGFUSE_HOST` | Backend, script y evaluación | vacío (desactivado) |
| `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY` | Claves del proyecto | vacío |
| `AI_TRACE_SESSION` | Agrupa las trazas de una evaluación | vacío |

## Metadatos de cada llamada de chat

`generation_name=recomendaciones`, `trace_name=recomendaciones`, `version=<versión del prompt>` (versión de la traza en Langfuse), `session_id` si se definió.
