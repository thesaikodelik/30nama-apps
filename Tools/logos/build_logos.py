#!/usr/bin/env python3
"""Builds logos.json: for each 30nama title with an IMDb id, the file names of its logos on TMDB
(English, Persian). Where TMDB is filtered (Iran) the app cannot ask TMDB which logo a title has,
but it can read this file from GitHub and the picture through an image proxy.

Run daily by .github/workflows/logos.yml. Each run carries on from the last: titles already
looked up are not asked again, those with no logo are asked again after a month, and a run stops
after BUDGET lookups so that a first, long pass is spread over several days.

    SITE_KEY=... TMDB_KEY=... [BUDGET=500] [MAX_PAGES=2] build_logos.py <folder holding logos.json and state.json>

TMDB_KEY is a v3 key or a v4 read token from the owner's TMDB account, kept as a secret of the
repository's workflow. Only TMDB's API is used: its website turns away bulk reading, as it may.
"""
import datetime
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

SITE = "https://interface.30nama.com/api/v1/action/"
TMDB_API = "https://api.themoviedb.org/3/"
AGENT = "30nama-logos (+https://github.com/thesaikodelik/30nama-apps)"
KINDS = ["movie", "series", "anime"]
RECHECK_DAYS = 30


def fetch(url, headers=None, data=None, tries=4):
    """The body of url, or None for a page that is not there. Rate limits and outages are waited out."""
    for attempt in range(tries):
        request = urllib.request.Request(url, data=data, headers={"User-Agent": AGENT, **(headers or {})})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.geturl(), response.read()
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            if error.code in (429, 500, 502, 503, 504) and attempt < tries - 1:
                time.sleep(int(error.headers.get("Retry-After") or 0) or 5 * (attempt + 1))
                continue
            raise
        except (urllib.error.URLError, TimeoutError):
            if attempt < tries - 1:
                time.sleep(5 * (attempt + 1))
                continue
            raise
    return None


def site(path, key):
    headers = {"c-api-key": key, "c-platform": "Website", "Content-Type": "application/x-www-form-urlencoded"}
    found = fetch(SITE + path, headers, data=b"")
    if not found:
        return None
    answer = json.loads(found[1])
    return answer.get("result") if answer.get("success") else None


def catalog(key, known):
    """IMDb ids of 30nama's titles, newest first. A kind is left once a whole page of it is known:
    what is older was seen on an earlier run."""
    most = int(os.environ.get("MAX_PAGES") or 100000)
    for kind in KINDS:
        page, pages = 1, 1
        while page <= min(pages, most):
            result = site(f"list/type/category/value/{kind}/page/{page}/orderby/id/order/desc/streamonly/no/genre/all", key)
            if not result:
                break
            pages = int(result.get("pages") or 1)
            ids = [p.get("imdb_id") for p in result.get("posts") or [] if re.fullmatch(r"tt\d+", p.get("imdb_id") or "")]
            yield from ids
            if ids and all(i in known for i in ids):
                break
            page += 1
            time.sleep(0.5)


def best(logos, code):
    """The best voted logo in one language ('' for one with no text), as the app chooses."""
    fitting = [l for l in logos if (l.get("iso_639_1") or "") == code and l.get("file_path")]
    if not fitting:
        return ""
    path = max(fitting, key=lambda l: l.get("vote_average") or 0)["file_path"]
    return path.lstrip("/").replace(".svg", ".png")


def tmdb_api(imdb, key):
    def call(path, query):
        if not key.startswith("eyJ"):
            query = {**query, "api_key": key}
        headers = {"Accept": "application/json"}
        if key.startswith("eyJ"):
            headers["Authorization"] = "Bearer " + key
        found = fetch(TMDB_API + path + "?" + urllib.parse.urlencode(query), headers)
        return json.loads(found[1]) if found else {}

    found = call(f"find/{imdb}", {"external_source": "imdb_id"})
    for kind, results in (("movie", found.get("movie_results")), ("tv", found.get("tv_results"))):
        if results:
            logos = call(f"{kind}/{results[0]['id']}/images", {"include_image_language": "fa,en,null"}).get("logos") or []
            return [best(logos, "en") or best(logos, ""), best(logos, "fa")]
    return ["", ""]


def main():
    folder = sys.argv[1]
    site_key = os.environ["SITE_KEY"]
    tmdb_key = os.environ["TMDB_KEY"]
    budget = int(os.environ.get("BUDGET") or 20000)
    today = datetime.date.today()

    def load(name):
        try:
            with open(os.path.join(folder, name)) as file:
                return json.load(file)
        except FileNotFoundError:
            return {}

    logos = load("logos.json").get("logos", {})
    state = load("state.json")
    # Titles TMDB had no logo for, with the day they were last looked up.
    none = state.get("none", {})
    # Titles found in the catalogue and not looked up yet: the site is not read through again for them.
    queue = state.get("queue", [])
    known = set(logos) | set(none) | set(queue)

    new = []
    for imdb in catalog(site_key, known):
        if imdb not in known and imdb not in new:
            new.append(imdb)
    queue = new + queue
    stale = [i for i, day in none.items() if (today - datetime.date.fromisoformat(day)).days >= RECHECK_DAYS]
    print(f"{len(logos)} with a logo, {len(none)} without, {len(new)} new, {len(queue)} waiting, {len(stale)} to look at again; up to {budget} this run", flush=True)

    looked = 0
    done = set()
    for imdb in queue + stale:
        if looked >= budget:
            break
        try:
            found = tmdb_api(imdb, tmdb_key)
        except Exception as error:  # one title's failure is not the run's
            print(f"{imdb}: {error}", flush=True)
            continue
        looked += 1
        done.add(imdb)
        if any(found):
            logos[imdb] = found
            none.pop(imdb, None)
        else:
            none[imdb] = today.isoformat()
        # Well under TMDB's limit of about forty requests a second.
        time.sleep(0.1)
        if looked % 200 == 0:
            print(f"{looked} looked up, {len(logos)} with a logo", flush=True)

    with open(os.path.join(folder, "logos.json"), "w") as file:
        json.dump({"version": 1, "updated": today.isoformat(), "logos": dict(sorted(logos.items()))}, file, separators=(",", ":"))
    queue = [i for i in queue if i not in done]
    with open(os.path.join(folder, "state.json"), "w") as file:
        json.dump({"none": dict(sorted(none.items())), "queue": queue}, file, separators=(",", ":"))
    print(f"done: {looked} looked up; {len(logos)} titles with a logo, {len(none)} without, {len(queue)} still waiting", flush=True)


if __name__ == "__main__":
    main()
