import json
import pathlib

import evaluate
from checks import average, hit_rate, run_checks

ROOT = pathlib.Path(__file__).resolve().parents[2]
CASE = {
    "id": "c",
    "canciones": [{"titulo": "Mine", "artista": "Me", "genero": "rock"}],
    "esperadas": [{"titulo": "New", "artista": "Singer"}],
}


def suggestion(identifier="a", title="New", reason="Afín"):
    return {"idCatalogo": identifier, "titulo": title, "artista": "Singer", "genero": "rock", "motivo": reason}


def result(*recommendations, origin="IA"):
    candidates = [dict(suggestion(identifier), anno="2000") for identifier in ("a", "b")]
    return {"id": "c", "origen": origin, "versionPrompt": "v2", "candidatas": candidates,
            "recomendaciones": list(recommendations)}


def failed(outcome):
    return {name for name, passed in run_checks(CASE, outcome).items() if not passed}


def test_a_correct_answer_passes_every_check():
    assert failed(result(suggestion())) == set()


def test_detects_each_problem():
    assert failed(result()) == {"salida_valida"}
    assert failed(result(suggestion("z"))) == {"en_candidatas"}
    assert failed(result(suggestion(""))) == {"en_candidatas"}
    assert failed(result(suggestion("a"), suggestion("b", " new "))) == {"sin_duplicados"}
    assert failed(result(dict(suggestion(), titulo="MINE", artista="me"))) == {"sin_canciones_de_la_lista"}
    assert failed(result(*[suggestion("a", f"T{index}") for index in range(6)])) == {"maximo_5"}
    assert failed(result(suggestion(reason="x" * 121))) == {"motivo_corto"}


def test_the_fallback_does_not_need_catalog_identifiers():
    assert failed(result(suggestion(""), origin="RESPALDO")) == set()


def test_measures_hits_and_averages_only_what_exists():
    assert hit_rate(CASE, result(suggestion(), suggestion("b", "Other"))) == 0.5
    assert hit_rate(CASE, result()) is None
    assert average([1.0, None, 0.0]) == (0.5, 2)
    assert average([None]) == (None, 0)


def test_only_answers_of_the_model_are_scored():
    raw = {"casos": [result(suggestion(), origin="SIMILITUD")]}

    results = evaluate.asyncio.run(evaluate.evaluate_cases({"c": CASE}, raw, metrics={"unused": None}))

    assert results[0]["puntuaciones"] is None


def test_warns_when_langfuse_is_not_configured(monkeypatch, capsys):
    for name in ("LANGFUSE_HOST", "LANGFUSE_PUBLIC_KEY", "LANGFUSE_SECRET_KEY"):
        monkeypatch.delenv(name, raising=False)

    assert evaluate.publish_scores({"promedios": {}}, "session") is False
    assert "no se enviaron" in capsys.readouterr().err


def test_the_cases_come_from_the_catalog_and_expect_new_songs():
    catalog = json.loads(
        (ROOT / "infrastructure/src/main/resources/catalog/songs.json").read_text(encoding="utf-8"))
    known = {(song["titulo"], song["artista"]) for song in catalog}
    cases = json.loads((ROOT / "evaluation/cases.json").read_text(encoding="utf-8"))

    assert 15 <= len(cases) <= 20
    assert len({case["id"] for case in cases}) == len(cases)
    for case in cases:
        own = {(song["titulo"], song["artista"]) for song in case["canciones"]}
        expected = {(song["titulo"], song["artista"]) for song in case["esperadas"]}
        assert own and expected, case["id"]
        assert own <= known and expected <= known, case["id"]
        assert not own & expected, case["id"]
