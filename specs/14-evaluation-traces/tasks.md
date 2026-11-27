# Tasks: Evaluación, web y trazas

**Input**: `specs/14-evaluation-traces/` (spec, plan, research, data-model, contracts, quickstart)

Rutas relativas a `quipux-playlist-backend/`, salvo `infra/` = `quipux-playlist-infra/`. Se piden pruebas.

## Phase 1: Setup

- [x] T001 Crear `evaluation/pyproject.toml` y `evaluation/uv.lock` con Python 3.12 y ragas 0.4.4
- [x] T002 [P] Ignorar `evaluation/.venv` y `evaluation/reports` en `.gitignore`

## Phase 2: Foundational

- [x] T003 Añadir `current()` a `application/.../port/out/RecommendationProviderPort.java` y usarlo en `application/.../usecase/GetRecommendationsUseCase.java` y su prueba

## Phase 3: US1 — Medir la calidad (P1)

**Prueba independiente**: `uv run python evaluate.py` genera el informe.

- [x] T004 [US1] Crear `application/.../port/in/EvaluateRecommendationsPort.java` y `application/.../usecase/EvaluateRecommendationsUseCase.java` (data-model.md `EvaluatedPlaylist`) con su prueba
- [x] T005 [US1] Crear `bootstrap/.../RecommendationEvaluationApplication.java`, enlazarlo en `PlaylistApplication.java` y probarlo con WireMock en `bootstrap/src/test/java/.../RecommendationEvaluationApplicationTest.java`
- [x] T006 [P] [US1] Escribir 18 casos en `evaluation/cases.json` (data-model.md `Caso de evaluación`)
- [x] T007 [US1] Escribir `evaluation/checks.py` y `evaluation/tests/test_checks.py`
- [x] T008 [US1] Escribir `evaluation/evaluate.py`: modo Java, comprobaciones, cuatro métricas e informe
- [x] T009 [P] [US1] Añadir el ecosistema `uv` de `/evaluation` en `.github/dependabot.yml`

## Phase 4: US2 — Trazas (P2)

**Prueba independiente**: una llamada al modelo, una traza.

- [x] T010 [P] [US2] Crear `infra/compose/compose.langfuse.yaml` y `infra/compose/litellm/config.langfuse.yaml`; variables en `infra/compose/.env.example`; embeddings sin traza en `infra/compose/litellm/config.yaml`
- [x] T011 [US2] Enviar los metadatos del contrato en `infrastructure/.../out/recommendation/LlmRecommendationProviderAdapter.java` y probarlo

## Phase 5: US3 — Prompt desde Langfuse (P2)

**Prueba independiente**: publicar, pedir, detener Langfuse, pedir.

- [x] T012 [US3] Crear `infrastructure/.../out/recommendation/ActivePrompt.java` (data-model.md `ActivePrompt`) y `LangfusePromptSource.java` con pruebas WireMock (éxito, caída, lentitud, 401, formato, caché)
- [x] T013 [US3] Conectar en `config/RecommendationConfig.java`, `LlmRecommendationProviderAdapter.java` y `bootstrap/src/main/resources/application.yml`; prueba de API con Langfuse caído
- [x] T014 [P] [US3] Escribir `tools/langfuse/publish_prompt.py` y `tools/langfuse/test_publish_prompt.py`

## Phase 6: US4 — Puntuaciones en Langfuse (P3)

- [x] T015 [US4] Enviar las puntuaciones desde `evaluation/evaluate.py`, con aviso si no se puede

## Phase 7: Polish

- [x] T016 `mvn verify` en verde y `uv run pytest`
- [x] T017 Validar `quickstart.md` con la composición real y anotar memoria consumida (pasos 1 a 5; el 6 es T018)
- [x] T018 Ejecutar la evaluación, revisar una muestra a mano y escribir `evaluation/BASELINE.md` (muestra revisada por el asistente; la de una persona sigue pendiente en el backlog)
- [x] T019 [P] `specs/14-evaluation-traces/requests.http`
- [x] T020 [P] Actualizar `README.md`, `evaluation/README.md`, `AI-LOG.md`, `infra/README.md` y los criterios de HU-14 en `../backlog-ia.md`

## Dependencies

T003 → T004, T011–T013. US1 y US2 son independientes. US3 y US4 necesitan US2 para validarse en real.

## Parallel

T002, T006, T009, T010 y T014 tocan archivos distintos.

## Strategy

MVP: US1. Si hubiera que recortar, se quitan bloques completos desde el último (Langfuse).
