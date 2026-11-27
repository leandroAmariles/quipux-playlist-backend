"""Publica en Langfuse el prompt del repositorio con la etiqueta production.

El archivo de Git es la fuente oficial: se niega a publicar cambios sin confirmar y no crea una versión si la de
producción ya tiene ese texto. Solo usa la biblioteca estándar.

    python tools/langfuse/publish_prompt.py
"""

import argparse
import base64
import json
import os
import pathlib
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

BACKEND = pathlib.Path(__file__).resolve().parents[2]
ENV_FILE = BACKEND.parent / "quipux-playlist-infra" / "compose" / ".env"
PROMPTS = pathlib.PurePosixPath("infrastructure/src/main/resources/prompts/recommendations")
LABEL = "production"


def load_env(path):
    """Completa el entorno con el .env de Compose, sin pisar lo ya definido."""
    if path.is_file():
        for line in path.read_text(encoding="utf-8").splitlines():
            name, separator, value = line.strip().partition("=")
            if separator and not name.startswith("#") and value:
                os.environ.setdefault(name, value.strip("\"'"))


def git(*arguments):
    return subprocess.run(
        ["git", "-C", str(BACKEND), *arguments], capture_output=True, text=True, check=True).stdout.strip()


def commit_of(prompt_file):
    """El commit que dejó el archivo como está. Con cambios sin confirmar no hay commit que citar."""
    if git("status", "--porcelain", "--", prompt_file):
        sys.exit(f"{prompt_file} tiene cambios sin confirmar en Git: confírmalos antes de publicar.")
    return git("log", "-1", "--format=%H", "--", prompt_file)


class Langfuse:
    def __init__(self, host, public_key, secret_key):
        self.url = host.rstrip("/") + "/api/public/v2/prompts"
        self.token = base64.b64encode(f"{public_key}:{secret_key}".encode()).decode()

    def _send(self, url, body=None):
        request = urllib.request.Request(
            url, data=None if body is None else json.dumps(body).encode(),
            headers={"Authorization": f"Basic {self.token}", "Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=10) as response:
            return json.load(response)

    def production(self, name):
        try:
            return self._send(f"{self.url}/{urllib.parse.quote(name)}?label={LABEL}")
        except urllib.error.HTTPError as failure:
            if failure.code == 404:
                return None
            raise

    def create(self, name, text, commit):
        return self._send(self.url, {
            "type": "text", "name": name, "prompt": text, "labels": [LABEL],
            "config": {"commit": commit}, "commitMessage": f"git {commit}",
        })


def publish(langfuse, name, text, commit):
    """Devuelve (número de versión, si se creó)."""
    current = langfuse.production(name)
    if current is not None and current["prompt"] == text:
        return current["version"], False
    return langfuse.create(name, text, commit)["version"], True


def text_of(prompt_file):
    """El texto tal como lo lee el backend: saltos de línea normalizados y sin espacios alrededor."""
    return (BACKEND / prompt_file).read_text(encoding="utf-8").replace("\r\n", "\n").strip()


def main():
    parser = argparse.ArgumentParser(description="Publica el prompt del repositorio en Langfuse.")
    parser.add_argument("--version", default="v2", help="archivo de prompts/recommendations, sin .txt")
    parser.add_argument("--env-file", type=pathlib.Path, default=ENV_FILE)
    arguments = parser.parse_args()
    load_env(arguments.env_file)
    host = os.environ.get("LANGFUSE_HOST") or f"http://localhost:{os.environ.get('LANGFUSE_PORT', '3000')}"
    public_key, secret_key = os.environ.get("LANGFUSE_PUBLIC_KEY"), os.environ.get("LANGFUSE_SECRET_KEY")
    if not (public_key and secret_key):
        sys.exit("Faltan LANGFUSE_PUBLIC_KEY y LANGFUSE_SECRET_KEY.")

    prompt_file = str(PROMPTS / f"{arguments.version}.txt")
    name = f"recomendaciones-{arguments.version}"
    commit = commit_of(prompt_file)
    try:
        version, created = publish(
            Langfuse(host, public_key, secret_key), name, text_of(prompt_file), commit)
    except OSError as failure:
        sys.exit(f"No se pudo publicar en Langfuse: {type(failure).__name__} {getattr(failure, 'code', '')}")
    state = "publicada" if created else "sin cambios"
    print(f"{name}: versión {version} {state} (commit {commit[:12]}).")


if __name__ == "__main__":
    main()
