# Research: HU-14

## R1. Contextos para RAGAS sin API pública

- **Decisión**: modo `evaluate-recommendations` del JAR. El caso de uso de evaluación construye `GetRecommendationsUseCase` con una lista en memoria, sin caché ni cupo, y registra las candidatas que recibe el proveedor.
- **Motivo**: misma recuperación, prompt y validación que el endpoint, sin ruta HTTP.
- **Alternativas**: endpoint interno protegido (expone superficie); repetir el flujo en Python (diverge).

## R2. Vía de las trazas

- **Decisión**: LiteLLM con `langfuse_otel`. Verificado en la imagen fijada: admite `include` en la configuración y los metadatos `generation_name`, `trace_name`, `session_id` y `trace_metadata`.
- **Motivo**: solo configuración; ninguna dependencia Java.
- **Hallazgo**: al incluir un archivo, sus secciones sustituyen a las del principal; por eso los modelos están en `models.yaml`.
- **Alternativa**: OpenTelemetry desde Spring AI (dependencias y configuración nuevas).

## R3. Versión del prompt coherente en una petición

- **Decisión**: `RecommendationProviderPort.current()` devuelve una vista del proveedor ligada a un prompt; clave de caché, llamada y respuesta usan la misma.
- **Alternativa**: leer una referencia mutable dos veces (puede mezclar versiones).

## R4. Consulta a Langfuse

- **Decisión**: `GET /api/public/v2/prompts/recomendaciones-v2?label=production`, autenticación básica, 1 s, resultado (o fallo) en caché 60 s con `Mono.cache`. El texto debe cumplir el formato de `PromptTemplate`; si no, archivo local.
- **Etiqueta de versión**: `v2-lf<n>`; local: `v2`.

## R5. RAGAS

- **Decisión**: ragas 0.4.4, Python 3.12 con uv, `langchain-community<0.4` (verificado: importa). Juez por LiteLLM, `recomendador-chat` por defecto y configurable; sin trazas (`no-log`).
- **Juez**: métricas de `ragas.metrics.collections` con salida impuesta por esquema (`instructor`, `JSON_SCHEMA`). Con texto libre o solo JSON, `llama3.2:3b` fallaba el formato.
- **Puntuaciones en Langfuse**: sobre la sesión de la ejecución, con la versión del prompt en el comentario y en los metadatos.

## R6. Composición de Langfuse

- Base: `docker-compose.yml` de la versión 4.77.0. Imágenes ya usadas en este equipo y fijadas por digest: langfuse y worker 4.77.0, ClickHouse 26.10.4.207, MinIO (chainguard), Redis 8.10.3, PostgreSQL 18.6.
- Solo la web publica puerto, en `127.0.0.1`. Secretos obligatorios por entorno, sin valores por defecto.
