#!/usr/bin/env python3
"""Builds logos.json: for each 30nama title with an IMDb id, the file names of its logos on TMDB
(English, Persian). Where TMDB is filtered (Iran) the app cannot ask TMDB which logo a title has,
but it can read this file from GitHub and the picture through an image proxy.

Run daily by .github/workflows/logos.yml. Each run carries on from the last: titles already
looked up are not asked again, those with no logo are asked again after a month, and a run stops
after BUDGET lookups or MAX_MINUTES, so that a first, long pass is spread over several days. The
most watched titles (by IMDb votes) are looked up first.

    SITE_KEY=... TMDB_KEY=... [BUDGET=500] [MAX_MINUTES=270] [MAX_PAGES=2] build_logos.py <folder holding logos.json and state.json>

TMDB_KEY is a v3 key or a v4 read token from the owner's TMDB account, kept as a secret of the
repository's workflow. Only TMDB's API is used: its website turns away bulk reading, as it may.
"""
import concurrent.futures
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


def catalog(key, known, whole):
    """30nama's titles as (IMDb id, IMDb votes), newest first, and whether the catalogue was read
    to its end. Once it has been (`whole` false), a kind is left at the first page that is all
    known: what is older was read on an earlier run."""
    most = int(os.environ.get("MAX_PAGES") or 0)
    found, complete = [], not most
    for kind in KINDS:
        page, pages = 1, 1
        while page <= pages:
            if most and page > most:
                break
            result = site(f"list/type/category/value/{kind}/page/{page}/orderby/id/order/desc/streamonly/no/genre/all", key)
            if not result:
                complete = False
                break
            pages = int(result.get("pages") or 1)
            titles = [(p["imdb_id"], votes(p)) for p in result.get("posts") or [] if re.fullmatch(r"tt\d+", p.get("imdb_id") or "")]
            found += titles
            if not whole and titles and all(i in known for i, _ in titles):
                break
            page += 1
            time.sleep(0.5)
    return found, complete


def votes(post):
    try:
        return int(str(post.get("imdb_votes") or 0).replace(",", ""))
    except ValueError:
        return 0


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
    budget = int(os.environ.get("BUDGET") or 60000)
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
    # Titles found in the catalogue and not looked up yet, as [IMDb id, votes]: the site is not read
    # through again for them. A queue kept without votes (an older run) is read through again for them.
    queue = state.get("queue", [])
    read_through = state.get("read_through", False) and all(isinstance(q, list) for q in queue)
    waiting = {q[0] if isinstance(q, list) else q for q in queue}
    known = set(logos) | set(none) | waiting

    # Until the catalogue has once been read to its end, every page of it is read.
    titles, complete = catalog(site_key, known, whole=not read_through)
    read_through = read_through or complete
    votes_of = {imdb: count for imdb, count in titles}
    new = [imdb for imdb in dict.fromkeys(imdb for imdb, _ in titles) if imdb not in known]
    queue = [[imdb, votes_of.get(imdb, q[1] if isinstance(q, list) else 0)] for imdb, q in
             ((q[0] if isinstance(q, list) else q, q) for q in queue)] + [[imdb, votes_of.get(imdb, 0)] for imdb in new]
    queue.sort(key=lambda q: -q[1])
    stale = [i for i, day in none.items() if (today - datetime.date.fromisoformat(day)).days >= RECHECK_DAYS]
    print(f"{len(logos)} with a logo, {len(none)} without, {len(new)} new, {len(queue)} waiting, {len(stale)} to look at again; up to {budget} this run", flush=True)

    # Four at a time, each pausing between its own requests: well under TMDB's limit of about forty a second.
    def look(imdb):
        try:
            found = tmdb_api(imdb, tmdb_key)
        except Exception as error:  # one title's failure is not the run's
            print(f"{imdb}: {error}", flush=True)
            return imdb, None
        time.sleep(0.1)
        return imdb, found

    deadline = time.monotonic() + 60 * int(os.environ.get("MAX_MINUTES") or 270)
    todo = ([q[0] for q in queue] + stale)[:budget]
    looked = 0
    done = set()
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for start in range(0, len(todo), 200):
            if time.monotonic() > deadline:
                break
            for imdb, found in pool.map(look, todo[start:start + 200]):
                if found is None:
                    continue
                looked += 1
                done.add(imdb)
                if any(found):
                    logos[imdb] = found
                    none.pop(imdb, None)
                else:
                    none[imdb] = today.isoformat()
            print(f"{looked} looked up, {len(logos)} with a logo", flush=True)

    with open(os.path.join(folder, "logos.json"), "w") as file:
        json.dump({"version": 1, "updated": today.isoformat(), "logos": dict(sorted(logos.items()))}, file, separators=(",", ":"))
    queue = [q for q in queue if q[0] not in done]
    with open(os.path.join(folder, "state.json"), "w") as file:
        json.dump({"none": dict(sorted(none.items())), "queue": queue, "read_through": read_through}, file, separators=(",", ":"))
    print(f"done: {looked} looked up; {len(logos)} titles with a logo, {len(none)} without, {len(queue)} still waiting", flush=True)


if __name__ == "__main__":
    main()
