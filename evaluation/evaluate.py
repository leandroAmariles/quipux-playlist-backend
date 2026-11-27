"""Evalúa las recomendaciones fuera de línea.

1. Ejecuta el modo interno del backend, que recomienda para cada caso y vuelca las candidatas.
2. Aplica las comprobaciones deterministas.
3. Calcula las cuatro métricas de RAGAS con un juez local a través de LiteLLM.
4. Escribe el informe y, si Langfuse está configurado, envía las puntuaciones.

    uv run python evaluate.py
"""

import argparse
import asyncio
import base64
import datetime
import json
import math
import os
import pathlib
import subprocess
import sys
import urllib.request

from checks import average, hit_rate, run_checks

HERE = pathlib.Path(__file__).resolve().parent
BACKEND = HERE.parent
ENV_FILE = BACKEND.parent / "quipux-playlist-infra" / "compose" / ".env"
METRICS = ("faithfulness", "answer_relevancy", "context_precision", "context_recall")


def load_env(path):
    """Completa el entorno con el .env de Compose, sin pisar lo ya definido."""
    if path.is_file():
        for line in path.read_text(encoding="utf-8").splitlines():
            name, separator, value = line.strip().partition("=")
            if separator and not name.startswith("#") and value:
                os.environ.setdefault(name, value.strip("\"'"))


def local_langfuse():
    """Con las claves definidas, Langfuse es el de la composición, en este equipo."""
    if os.environ.get("LANGFUSE_PUBLIC_KEY") and os.environ.get("LANGFUSE_SECRET_KEY"):
        os.environ.setdefault("LANGFUSE_HOST", f"http://localhost:{os.environ.get('LANGFUSE_PORT', '3000')}")


def jar():
    found = sorted((BACKEND / "bootstrap" / "target").glob("playlist-bootstrap-*.jar"), key=os.path.getmtime)
    if not found:
        sys.exit("No hay JAR: ejecuta antes 'mvn package -DskipTests' en el backend.")
    return found[-1]


def recommend(cases_file, raw_file, session):
    """El modo interno del backend: la misma recuperación y elección que el endpoint."""
    environment = dict(os.environ)
    environment.setdefault("AI_BASE_URL", f"http://localhost:{os.environ.get('LITELLM_PORT', '4000')}/v1")
    environment.setdefault(
        "CATALOG_DB_URL", f"jdbc:postgresql://localhost:{os.environ.get('CATALOG_DB_PORT', '5433')}/catalog")
    environment.setdefault("AI_TIMEOUT", "120s")
    environment.setdefault("CATALOG_RETRIEVAL_TIMEOUT", "30s")
    environment.update(AI_PROVIDER="openai", AI_TRACE_SESSION=session)
    command = [
        "java", "-jar", str(jar()), "evaluate-recommendations",
        f"--playlist.evaluation.cases={cases_file}", f"--playlist.evaluation.output={raw_file}",
        # Un proceso recién arrancado tarda más que el segundo que espera la API; y toda la ejecución, un solo prompt.
        "--playlist.ai.langfuse.timeout=10s", "--playlist.ai.langfuse.cache-ttl=1h",
    ]
    if subprocess.run(command, env=environment, check=False).returncode != 0:
        sys.exit("El backend no obtuvo candidatas: revisa la composición y la indexación del catálogo.")
    return json.loads(raw_file.read_text(encoding="utf-8"))


def describe(song):
    return f"{song['titulo']} de {song['artista']} (género {song['genero']})"


def sample_of(case, result):
    """Adapta una recomendación al par pregunta-respuesta que miden las métricas."""
    songs = [describe(song) for song in case["canciones"]]
    candidates = [describe(song) for song in result["candidatas"]]
    return {
        "user_input": "¿Qué canciones me recomiendas si tengo en mi lista " + "; ".join(songs) + "?",
        "response": " ".join(
            f"Te recomiendo {item['titulo']} de {item['artista']}: {item['motivo']}"
            for item in result["recomendaciones"]),
        # La respuesta habla de la lista y de las candidatas; la recuperación se mide solo sobre las candidatas.
        "grounding": ["En la lista del usuario está " + song for song in songs] + candidates,
        "retrieved": candidates,
        "reference": " ".join(
            f"Una recomendación adecuada es {song['titulo']} de {song['artista']}." for song in case["esperadas"]),
    }


def ragas_metrics(judge_model, embedding_model, base_url, api_key):
    """Cada métrica con lo que recibe de la muestra."""
    import instructor
    from openai import AsyncOpenAI
    from ragas.embeddings import OpenAIEmbeddings
    from ragas.llms import llm_factory
    from ragas.metrics.collections import AnswerRelevancy, ContextPrecisionWithReference, ContextRecall, Faithfulness

    client = AsyncOpenAI(base_url=base_url, api_key=api_key, timeout=180, max_retries=1)
    # Un modelo pequeño solo cumple el formato si se le impone el esquema, como hace el backend.
    # "no-log": las llamadas del juez no son llamadas de la aplicación y no dejan traza.
    judge = llm_factory(
        judge_model, client=client, mode=instructor.Mode.JSON_SCHEMA, temperature=0, max_tokens=2048,
        extra_body={"no-log": True})
    embeddings = OpenAIEmbeddings(client=client, model=embedding_model)
    return {
        "faithfulness": (
            Faithfulness(llm=judge),
            {"user_input": "user_input", "response": "response", "retrieved_contexts": "grounding"}),
        "answer_relevancy": (
            AnswerRelevancy(llm=judge, embeddings=embeddings, strictness=1),
            {"user_input": "user_input", "response": "response"}),
        "context_precision": (
            ContextPrecisionWithReference(llm=judge),
            {"user_input": "user_input", "reference": "reference", "retrieved_contexts": "retrieved"}),
        "context_recall": (
            ContextRecall(llm=judge),
            {"user_input": "user_input", "retrieved_contexts": "retrieved", "reference": "reference"}),
    }


async def score(metrics, sample):
    scores = {}
    for name, (metric, arguments) in metrics.items():
        try:
            outcome = await asyncio.wait_for(
                metric.ascore(**{argument: sample[field] for argument, field in arguments.items()}), timeout=300)
            value = float(outcome.value)
            scores[name] = None if math.isnan(value) else round(value, 4)
        except Exception as failure:  # un juez pequeño falla a veces: la métrica queda sin dato
            print(f"  {name}: no disponible ({type(failure).__name__})", file=sys.stderr)
            scores[name] = None
    return scores


async def evaluate_cases(cases, raw, metrics):
    results = []
    for result in raw["casos"]:
        case = cases[result["id"]]
        entry = dict(result, comprobaciones=run_checks(case, result), aciertos=hit_rate(case, result))
        # Solo se puntúa lo que respondió el modelo.
        entry["puntuaciones"] = (
            await score(metrics, sample_of(case, result)) if metrics and result["origen"] == "IA" else None)
        print(f"{result['id']}: {result['origen']} {entry['puntuaciones'] or ''}", flush=True)
        results.append(entry)
    return results


def summarize(raw, results, judge_model):
    versions = sorted({item["versionPrompt"] for item in results if item["versionPrompt"]})
    averages = {}
    for name in METRICS:
        value, count = average([(item["puntuaciones"] or {}).get(name) for item in results])
        averages[name] = {"promedio": value, "casos": count}
    hits, answered = average([item["aciertos"] for item in results if item["origen"] == "IA"])
    averages["aciertos_sobre_esperadas"] = {"promedio": hits, "casos": answered}
    origins = {}
    for item in results:
        origins[item["origen"]] = origins.get(item["origen"], 0) + 1
    checks = {
        name: sum(item["comprobaciones"][name] for item in results)
        for name in (results[0]["comprobaciones"] if results else {})
    }
    return {
        "fecha": datetime.datetime.now().astimezone().isoformat(timespec="seconds"),
        "versionPrompt": ",".join(versions),
        "modeloChat": raw["modeloChat"],
        "modeloEmbeddings": raw["modeloEmbeddings"],
        "modeloJuez": judge_model,
        "modelosOllama": {
            "chat": os.environ.get("OLLAMA_CHAT_MODEL", "llama3.2:3b"),
            "embeddings": os.environ.get("OLLAMA_EMBEDDING_MODEL", "nomic-embed-text"),
        },
        "versionCatalogo": raw["versionCatalogo"],
        "casos": len(results),
        "origenes": origins,
        "comprobaciones_superadas": checks,
        "promedios": averages,
    }


def markdown(summary, results):
    def shown(value):
        return "n/d" if value is None else f"{value:.2f}"

    lines = ["# Informe de evaluación", ""]
    for key in ("fecha", "versionPrompt", "modeloChat", "modeloEmbeddings", "modeloJuez", "versionCatalogo"):
        lines.append(f"- {key}: {summary[key]}")
    lines.append(f"- modelos de Ollama: {summary['modelosOllama']}")
    lines.append(f"- orígenes: {summary['origenes']}")
    lines += ["", "## Promedios", "", "| Métrica | Promedio | Casos |", "|---|---|---|"]
    for name, data in summary["promedios"].items():
        lines.append(f"| {name} | {shown(data['promedio'])} | {data['casos']} |")
    lines += ["", "## Comprobaciones deterministas", "", "| Comprobación | Superada |", "|---|---|"]
    for name, passed in summary["comprobaciones_superadas"].items():
        lines.append(f"| {name} | {passed}/{summary['casos']} |")
    lines += ["", "## Casos", "", "| Caso | Origen | " + " | ".join(METRICS) + " | Aciertos | Fallos |",
              "|---|---|---|---|---|---|---|---|"]
    for item in results:
        scores = item["puntuaciones"] or {}
        failed = ", ".join(name for name, passed in item["comprobaciones"].items() if not passed) or "-"
        lines.append("| {} | {} | {} | {} | {} |".format(
            item["id"], item["origen"], " | ".join(shown(scores.get(name)) for name in METRICS),
            shown(item["aciertos"]), failed))
    return "\n".join(lines) + "\n"


def publish_scores(summary, session):
    """Puntuaciones de la ejecución en su sesión de trazas, con la versión del prompt."""
    host = os.environ.get("LANGFUSE_HOST")
    public, secret = os.environ.get("LANGFUSE_PUBLIC_KEY"), os.environ.get("LANGFUSE_SECRET_KEY")
    if not (host and public and secret):
        print("Aviso: Langfuse sin configurar; las puntuaciones no se enviaron.", file=sys.stderr)
        return False
    token = base64.b64encode(f"{public}:{secret}".encode()).decode()
    try:
        for name, data in summary["promedios"].items():
            if data["promedio"] is None:
                continue
            body = {
                "sessionId": session, "name": f"ragas_{name}", "value": data["promedio"], "dataType": "NUMERIC",
                "comment": f"prompt {summary['versionPrompt']}, {data['casos']} casos",
                "metadata": {"versionPrompt": summary["versionPrompt"], "versionCatalogo": summary["versionCatalogo"]},
            }
            request = urllib.request.Request(
                f"{host.rstrip('/')}/api/public/scores", data=json.dumps(body).encode(), method="POST",
                headers={"Authorization": f"Basic {token}", "Content-Type": "application/json"})
            with urllib.request.urlopen(request, timeout=10):
                pass
    except OSError as failure:
        print(f"Aviso: las puntuaciones no se enviaron a Langfuse ({type(failure).__name__}).", file=sys.stderr)
        return False
    print(f"Puntuaciones enviadas a la sesión {session} de Langfuse.")
    return True


def main():
    parser = argparse.ArgumentParser(description="Evalúa las recomendaciones con RAGAS.")
    parser.add_argument("--cases", type=pathlib.Path, default=HERE / "cases.json")
    parser.add_argument("--out", type=pathlib.Path, default=HERE / "reports")
    parser.add_argument("--env-file", type=pathlib.Path, default=ENV_FILE)
    parser.add_argument("--judge-model", default="recomendador-chat", help="alias de LiteLLM que puntúa")
    parser.add_argument("--no-ragas", action="store_true", help="solo comprobaciones deterministas")
    arguments = parser.parse_args()

    load_env(arguments.env_file)
    local_langfuse()
    api_key = os.environ.get("AI_API_KEY")
    if not api_key:
        sys.exit("Falta AI_API_KEY (variable de entorno o .env de Compose).")
    started = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    session = f"evaluacion-{started}"
    folder = arguments.out / started
    folder.mkdir(parents=True, exist_ok=True)

    cases = {case["id"]: case for case in json.loads(arguments.cases.read_text(encoding="utf-8"))}
    raw = recommend(arguments.cases.resolve(), (folder / "raw.json").resolve(), session)
    base_url = os.environ.get("AI_BASE_URL") or f"http://localhost:{os.environ.get('LITELLM_PORT', '4000')}/v1"
    metrics = None if arguments.no_ragas else ragas_metrics(
        arguments.judge_model, raw["modeloEmbeddings"], base_url, api_key)
    results = asyncio.run(evaluate_cases(cases, raw, metrics))

    summary = summarize(raw, results, arguments.judge_model)
    (folder / "report.json").write_text(
        json.dumps({"resumen": summary, "casos": results}, ensure_ascii=False, indent=2), encoding="utf-8")
    (folder / "report.md").write_text(markdown(summary, results), encoding="utf-8")
    print(f"Informe: {folder / 'report.md'}")
    publish_scores(summary, session)


if __name__ == "__main__":
    main()
