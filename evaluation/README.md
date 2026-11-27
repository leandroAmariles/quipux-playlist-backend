# Evaluación de las recomendaciones

Se ejecuta fuera de línea, nunca dentro de una petición. Mide con [RAGAS](https://docs.ragas.io) y con
comprobaciones que no usan ningún modelo.

## Requisitos

- La composición de la IA en marcha y el catálogo indexado (`quipux-playlist-infra`).
- El JAR del backend: `mvn package -DskipTests`.
- Java 21 y [uv](https://docs.astral.sh/uv/), que instala Python 3.12 y las versiones de `uv.lock`.

## Ejecutar

Desde esta carpeta:

```bash
uv run python evaluate.py
```

Lee `../../quipux-playlist-infra/compose/.env` (u otro con `--env-file`) sin pisar el entorno. Usa `AI_API_KEY`,
`CATALOG_DB_PASSWORD`, `LITELLM_PORT`, `CATALOG_DB_PORT` y, si están, las claves de Langfuse y `LANGFUSE_PORT`.

| Opción | Para qué |
|---|---|
| `--judge-model` | alias de LiteLLM que puntúa (por defecto `recomendador-chat`) |
| `--no-ragas` | solo las comprobaciones deterministas, en segundos |
| `--cases`, `--out` | otros casos u otra carpeta de informes |

El informe queda en `reports/<fecha>/` (`report.md`, `report.json` y `raw.json`), fuera de Git. La línea base está en
[`BASELINE.md`](BASELINE.md).

## Cómo funciona

1. `java -jar … evaluate-recommendations` recomienda cada caso con el mismo código que el endpoint y guarda las
   candidatas recuperadas. No hay ninguna ruta HTTP que las exponga.
2. Comprobaciones: salida válida, pertenencia a las candidatas, sin duplicados, sin canciones de la lista, máximo 5
   y motivo de 120 caracteres.
3. RAGAS solo sobre las respuestas con origen `IA`. Una métrica que el juez no calcula queda como `n/d`.
4. Con Langfuse configurado, los promedios se envían a la sesión de trazas de la ejecución, con la versión del prompt.

## Cómo leer las métricas

| Métrica | Aquí significa |
|---|---|
| faithfulness | lo que afirman los motivos se apoya en la lista y las candidatas |
| answer_relevancy | la respuesta contesta a "qué me recomiendas" |
| context_precision | las candidatas útiles aparecen primero |
| context_recall | las canciones esperadas estaban entre las candidatas |
| aciertos_sobre_esperadas | fracción de las sugerencias que estaban esperadas (sin juez) |

RAGAS se pensó para preguntas y respuestas, y el juez es un modelo local pequeño: puntúa de forma inestable. Las
notas sirven para comparar versiones, no como valor absoluto. No hay umbral que bloquee.

## Pruebas

```bash
uv run pytest
```
