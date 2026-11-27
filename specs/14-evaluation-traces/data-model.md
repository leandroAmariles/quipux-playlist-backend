# Data Model: HU-14

Sin cambios en H2 ni en pgvector.

| Entidad | Campos | Reglas |
|---|---|---|
| Caso de evaluación | `id`, `canciones[]`, `esperadas[]` (título, artista) | 15–20 casos; esperadas en el catálogo y fuera de la lista |
| `ActivePrompt` | `version` (`v2` o `v2-lf3`), plantilla | Plantilla válida |
| `EvaluatedPlaylist` | respuesta (`RecommendationSet`), `candidates[]`, `model` | Candidatas vacías si no hubo recuperación |
| Informe | fecha, versiones, resultados, promedios y su número de casos | Métrica no calculada = `null` |
