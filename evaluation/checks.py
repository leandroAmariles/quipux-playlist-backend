"""Comprobaciones deterministas de las recomendaciones: no usan ningún modelo."""

MAX_RECOMMENDATIONS = 5
MAX_REASON_LENGTH = 120
REQUIRED_FIELDS = ("idCatalogo", "titulo", "artista", "genero", "motivo")


def identity(song):
    """Dos canciones son la misma si coinciden título y artista, sin mayúsculas ni espacios de los extremos."""
    return (song["titulo"].strip().casefold(), song["artista"].strip().casefold())


def run_checks(case, result):
    """Devuelve {comprobación: bool} para la respuesta de un caso."""
    recommendations = result["recomendaciones"]
    candidates = {candidate["idCatalogo"] for candidate in result["candidatas"]}
    verified = [item for item in recommendations if item["idCatalogo"]]
    own = {identity(song) for song in case["canciones"]}
    return {
        "salida_valida": bool(recommendations)
        and all(isinstance(item.get(field), str) for item in recommendations for field in REQUIRED_FIELDS),
        # El respaldo no sale del catálogo: sin identificador no hay nada que pertenezca.
        "en_candidatas": result["origen"] == "RESPALDO"
        or (len(verified) == len(recommendations) and all(item["idCatalogo"] in candidates for item in verified)),
        "sin_duplicados": len({identity(item) for item in recommendations}) == len(recommendations),
        "sin_canciones_de_la_lista": not own & {identity(item) for item in recommendations},
        "maximo_5": len(recommendations) <= MAX_RECOMMENDATIONS,
        # Java cuenta unidades UTF-16.
        "motivo_corto": all(
            len(item["motivo"].encode("utf-16-le")) // 2 <= MAX_REASON_LENGTH for item in recommendations),
    }


def hit_rate(case, result):
    """Fracción de las sugerencias que están entre las esperadas, o None sin sugerencias."""
    recommendations = result["recomendaciones"]
    if not recommendations:
        return None
    expected = {identity(song) for song in case["esperadas"]}
    return sum(identity(item) in expected for item in recommendations) / len(recommendations)


def average(values):
    """Promedio de los valores disponibles y cuántos son. Lo no calculado no cuenta como cero."""
    available = [value for value in values if value is not None]
    return (sum(available) / len(available) if available else None, len(available))
