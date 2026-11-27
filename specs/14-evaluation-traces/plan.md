# Implementation Plan: Evaluación, web y trazas

**Branch**: `feature/14-evaluation-traces` | **Date**: 2026-11-27 | **Spec**: [spec.md](spec.md)

## Summary

1. **Evaluación**: modo de línea de comandos `evaluate-recommendations` en el JAR, que reutiliza el caso de uso real y vuelca candidatas y respuestas; `evaluation/` (Python, uv) calcula comprobaciones deterministas y las cuatro métricas de RAGAS.
2. **Trazas**: LiteLLM → Langfuse con `langfuse_otel`, activado solo por `compose.langfuse.yaml`. El backend añade `prompt_version` a los metadatos de cada llamada de chat.
3. **Prompt**: fuente en infraestructura que consulta la versión `production` con `WebClient`, con caché y vuelta al archivo local; script de publicación.
4. **Web**: ya entregada.

## Technical Context

**Language/Version**: Java 21; Python 3.12 (solo `evaluation/`)

**Primary Dependencies**: Spring Boot 4.1.3, Spring AI 2.0.3 (sin dependencias Java nuevas); ragas 0.4.4; LiteLLM 1.111.0; Langfuse 4.77.0

**Storage**: la actual; Langfuse trae sus propias bases (PostgreSQL, ClickHouse, Redis, MinIO)

**Testing**: JUnit 5, Mockito, WireMock, BlockHound, ArchUnit, JaCoCo ≥ 80 %; pytest

**Target Platform**: Docker Compose local

**Project Type**: API web hexagonal + herramientas fuera de línea

**Constraints**: sin Langfuse ni modelos las pruebas pasan; consulta del prompt ≤ 1 s por periodo de caché; Docker con 15,2 GiB

**Scale/Scope**: 18 casos de evaluación

## Constitution Check

`.specify/memory/constitution.md` es la plantilla sin completar: no hay principios que evaluar. Rigen las reglas de `.ai/rules/` y el ADR-0002. Sin violaciones, antes y después del diseño.

## Project Structure

```text
quipux-playlist-backend/
├── application/.../port/out/RecommendationProviderPort.java   # + current()
├── application/.../port/in/EvaluateRecommendationsPort.java   # nuevo
├── application/.../usecase/EvaluateRecommendationsUseCase.java# nuevo
├── infrastructure/.../out/recommendation/
│   ├── ActivePrompt.java, LangfusePromptSource.java           # nuevos
│   ├── LlmRecommendationProviderAdapter.java                  # prompt activo y metadatos
│   └── config/RecommendationConfig.java
├── bootstrap/.../RecommendationEvaluationApplication.java     # nuevo
├── evaluation/        # pyproject.toml, uv.lock, cases.json, evaluate.py, checks.py, tests/
└── tools/langfuse/publish_prompt.py
quipux-playlist-infra/compose/
├── compose.langfuse.yaml            # nuevo
├── litellm/{models,config.langfuse}.yaml # modelos comunes; ajustes + callback
└── .env.example                     # + variables
```

**Structure Decision**: se mantiene la hexagonal; Langfuse solo se conoce en `infrastructure`.

## Complexity Tracking

| Desviación del ADR | Motivo |
|---|---|
| Langfuse sin su AI Gateway (6 de 7 servicios) | No se usa: el gateway de modelos ya es LiteLLM. Ahorra memoria |
