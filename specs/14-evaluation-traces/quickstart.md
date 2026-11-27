# Quickstart: validar HU-14

Requisitos: `compose/.env` completo, Docker, Java 21 y uv. Comandos desde `quipux-playlist-infra/compose` salvo indicación.

1. **Pruebas** (backend): `mvn verify`; en `evaluation/`: `uv run pytest`.
2. **Composición**: `docker compose -p quipux-ia -f compose.yaml -f compose.ai.yaml -f compose.ai.gpu.yaml -f compose.langfuse.yaml up -d --build`.
3. **Publicar el prompt** (backend): `python tools/langfuse/publish_prompt.py` dos veces → una sola versión.
4. **Traza**: pedir recomendaciones de una lista con canciones → `versionPrompt` `v2-lf1` y una traza nueva; repetir (caché) → ninguna más.
5. **Caída**: `docker compose -p quipux-ia … stop langfuse-web`, esperar 60 s y pedir con otra lista → `200`, `versionPrompt` `v2`.
6. **Evaluación** (backend, `evaluation/`): `uv run python evaluate.py` → informe en `evaluation/reports/`.
