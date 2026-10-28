#!/usr/bin/env python3
"""Builds trending.json: TMDB's lists of the week, for the apps to show "Trending this week" to a
viewer who has no TMDB key of their own. TMDB's public page that the apps read without a key now
sits behind Cloudflare's bot check, which turns an app away (a Mac passes as Safari; Android TV
does not), so the list is taken here from TMDB's API with the project's key, once a day.

Run daily by .github/workflows/logos.yml, beside logos.json on the `data` branch.

    TMDB_KEY=... build_trending.py <folder to write trending.json in>

The file: {"updated": "<UTC time>", "lists": {"<path>?<query>": [entries]}}. Each list is keyed as
the apps ask TMDB for it (core's TrendingRow sources: the path and its query, keys in order), and
each entry keeps only the fields the apps keep (TMDBItem.keptKeys). TMDB_KEY is a v3 key or a v4
read token, as for logos.json. If TMDB does not answer, the last file is left as it was.
"""
import datetime
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

TMDB_API = "https://api.themoviedb.org/3"
AGENT = "30nama-trending (+https://github.com/thesaikodelik/30nama-apps)"

# As core's TrendingRow.trend asks for it (kotlin-core Trending.kt, 30nama/Core/Trending.swift).
LISTS = [("/trending/all/week", {"language": "en-US"})]

KEPT = {"id", "media_type", "title", "name", "original_title", "original_name",
        "release_date", "first_air_date", "poster_path", "popularity", "vote_count"}


def fetch(url, headers, tries=4):
    """The JSON at url. Rate limits and outages are waited out."""
    for attempt in range(tries):
        request = urllib.request.Request(url, headers={"User-Agent": AGENT, "Accept": "application/json", **headers})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.loads(response.read())
        except urllib.error.HTTPError as error:
            if error.code in (429, 500, 502, 503, 504) and attempt < tries - 1:
                time.sleep(int(error.headers.get("Retry-After") or 0) or 5 * (attempt + 1))
                continue
            raise
        except (urllib.error.URLError, TimeoutError):
            if attempt < tries - 1:
                time.sleep(5 * (attempt + 1))
                continue
            raise


def tmdb(path, query, key):
    headers = {}
    if key.startswith("eyJ"):
        headers["Authorization"] = "Bearer " + key
    else:
        query = {**query, "api_key": key}
    return fetch(TMDB_API + path + "?" + urllib.parse.urlencode(query), headers)


def main():
    out = sys.argv[1]
    key = os.environ["TMDB_KEY"].strip()
    lists = {}
    for path, query in LISTS:
        results = tmdb(path, query, key).get("results") or []
        entries = [{k: v for k, v in entry.items() if k in KEPT} for entry in results if entry.get("media_type") != "person"]
        if entries:
            # Keyed as the apps key their own cache: the query's keys in order.
            lists[path + "?" + "&".join(f"{k}={v}" for k, v in sorted(query.items()))] = entries
    if not lists:
        sys.exit("TMDB gave no list; trending.json left as it was")
    updated = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    with open(os.path.join(out, "trending.json"), "w", encoding="utf-8") as f:
        json.dump({"updated": updated, "lists": lists}, f, ensure_ascii=False, separators=(",", ":"))
    print("trending.json:", {k: len(v) for k, v in lists.items()})


if __name__ == "__main__":
    main()
