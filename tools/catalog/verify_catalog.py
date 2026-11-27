#!/usr/bin/env python3
"""Checks every song of the catalog against MusicBrainz and writes a report.

For each song it looks for a recording with that title and artist on that album, and compares the year the album
was first released. It only reads core data (CC0): never tags or genres.

Usage, from the backend root:
    MUSICBRAINZ_CONTACT=<e-mail or URL> python tools/catalog/verify_catalog.py [--cache FILE]

MusicBrainz requires a contact in every request and allows one request per second.
"""
import argparse
import json
import os
import pathlib
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
CATALOG = ROOT / "infrastructure/src/main/resources/catalog/songs.json"
MANUAL = ROOT / "tools/catalog/manual-checks.json"
REPORT = ROOT / "docs/catalog-verification.md"
API = "https://musicbrainz.org/ws/2"
PAUSE_SECONDS = 1.1
ATTEMPTS = 6


class MusicBrainz:
    def __init__(self, contact, cache_file):
        self.agent = "catalog-check/1.0 ( %s )" % contact
        self.cache_file = cache_file
        self.cache = {}
        if cache_file and cache_file.exists():
            self.cache = json.loads(cache_file.read_text(encoding="utf-8"))
        self.last = 0.0

    def get(self, path, **parameters):
        parameters["fmt"] = "json"
        url = "%s/%s?%s" % (API, path, urllib.parse.urlencode(parameters))
        if url not in self.cache:
            self.cache[url] = self._fetch(url)
            if self.cache_file:
                self.cache_file.write_text(json.dumps(self.cache), encoding="utf-8")
        return self.cache[url]

    def _fetch(self, url):
        for attempt in range(1, ATTEMPTS + 1):
            time.sleep(max(0.0, self.last + PAUSE_SECONDS - time.monotonic()))
            self.last = time.monotonic()
            try:
                request = urllib.request.Request(url, headers={"User-Agent": self.agent})
                with urllib.request.urlopen(request, timeout=30) as response:
                    return json.load(response)
            except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
                # 503 is how the service asks to slow down.
                if attempt == ATTEMPTS:
                    raise SystemExit("MusicBrainz did not answer: %s" % getattr(error, "reason", error))
                time.sleep(2 * attempt)


def normalized(text):
    decomposed = unicodedata.normalize("NFKD", text.casefold())
    plain = "".join(c for c in decomposed if not unicodedata.combining(c))
    plain = plain.replace("&", " and ")
    return " ".join(re.findall(r"[^\W_]+", plain)) or plain.strip()


def same_title(found, expected):
    without_notes = re.sub(r"\s*[(\[][^()\[\]]*[)\]]\s*$", "", found)
    return normalized(expected) in (normalized(found), normalized(without_notes))


def quoted(text):
    return '"%s"' % text.replace("\\", "\\\\").replace('"', '\\"')


def artists_to_try(artist):
    first = re.split(r"\s+(?:&|y|and)\s+|\s*,\s*", artist)[0]
    return [artist] if first == artist else [artist, first]


def candidates(client, song):
    """Pairs of recording and release group that match the title, the artist and the album."""
    for artist in artists_to_try(song["artista"]):
        query = "recording:%s AND artist:%s AND release:%s" % (
            quoted(song["titulo"]), quoted(artist), quoted(song["album"]))
        found = client.get("recording", query=query, limit=100)
        pairs = []
        for recording in found.get("recordings", []):
            if not same_title(recording.get("title", ""), song["titulo"]):
                continue
            for release in recording.get("releases", []):
                group = release.get("release-group", {})
                if same_title(group.get("title", ""), song["album"]) \
                        or same_title(release.get("title", ""), song["album"]):
                    pairs.append((recording["id"], group.get("id")))
        if pairs:
            return list(dict.fromkeys(pairs))
    return []


def check(client, song):
    pairs = candidates(client, song)
    if not pairs:
        return False, "no recording with that title and artist on that album", None
    years = {}
    for recording, group in list(dict((group, (recording, group)) for recording, group in pairs).values())[:4]:
        year = client.get("release-group/%s" % group).get("first-release-date", "")[:4]
        if year == song["anno"]:
            return True, "recording %s, release group %s" % (recording, group), None
        if year:
            years[year] = group
    found = ", ".join(sorted(years)) or "unknown"
    return False, "the album was first released in %s" % found, found


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--cache", type=pathlib.Path, help="file that keeps answers between runs")
    arguments = parser.parse_args()
    contact = os.environ.get("MUSICBRAINZ_CONTACT", "").strip()
    if not contact:
        raise SystemExit("Set MUSICBRAINZ_CONTACT to an e-mail or URL: MusicBrainz requires it. No request was made.")

    songs = json.loads(CATALOG.read_text(encoding="utf-8"))
    manual = {}
    if MANUAL.exists():
        manual = {(entry["titulo"], entry["artista"], entry["album"], entry["anno"]): entry["fuente"]
                  for entry in json.loads(MANUAL.read_text(encoding="utf-8"))}
    client = MusicBrainz(contact, arguments.cache)
    rows, pending = [], []
    for number, song in enumerate(songs, start=1):
        verified, detail, _ = check(client, song)
        source = manual.get((song["titulo"], song["artista"], song["album"], song["anno"]))
        if not verified and source:
            verified, detail = True, "checked by hand: %s" % source
        rows.append((number, song, verified, detail))
        if not verified:
            pending.append(number)
        print("%d/%d %s %s - %s: %s" % (
            number, len(songs), "ok  " if verified else "FAIL", song["titulo"], song["artista"], detail), flush=True)
    write_report(rows, pending)
    print("verified %d of %d; report in %s" % (len(songs) - len(pending), len(songs), REPORT.relative_to(ROOT)))
    return 1 if pending else 0


def write_report(rows, pending):
    by_hand = sum(1 for row in rows if row[3].startswith("checked by hand"))
    lines = [
        "# Comprobación del catálogo",
        "",
        "Generado por `tools/catalog/verify_catalog.py` el %s contra [MusicBrainz](https://musicbrainz.org) "
        "(datos básicos, CC0). Cada canción se busca por título, artista y álbum, y se compara el año de la primera "
        "publicación del álbum. Los géneros se asignaron a mano con la lista local y no se comprueban."
        % time.strftime("%Y-%m-%d"),
        "",
        "- Canciones: %d" % len(rows),
        "- Comprobadas en MusicBrainz: %d" % (len(rows) - len(pending) - by_hand),
        "- Comprobadas a mano: %d" % by_hand,
        "- Sin comprobar: %d" % len(pending),
        "",
        "| # | Canción | Artista | Álbum | Año | Estado | Detalle |",
        "|---|---|---|---|---|---|---|",
    ]
    for number, song, verified, detail in rows:
        cells = [str(number), song["titulo"], song["artista"], song["album"], song["anno"],
                 "Sí" if verified else "**No**", detail]
        lines.append("| %s |" % " | ".join(cell.replace("|", "\\|") for cell in cells))
    REPORT.parent.mkdir(exist_ok=True)
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
