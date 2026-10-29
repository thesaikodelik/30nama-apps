#!/usr/bin/env python3
"""Builds cards/NN.json: for each 30nama title, what the back of its poster shows on a title's page
(the Android TV app turns the poster over): another of its posters, its name in its own language and
script, the collection it belongs to and its place there, and the logo of the company that made it.
Every viewer gets it, those with no TMDB key of their own and those in Iran too, from GitHub.

Run daily by .github/workflows/logos.yml, after build_logos.py, whose logos.json and state.json say
which titles 30nama has. Titles are split by the last two digits of their IMDb number into a hundred
small files, so an app reads only the one it needs, when the poster is turned. Each run carries on
from the last: a title already looked up is asked again after RECHECK_DAYS, and a run stops after
BUDGET lookups or MAX_MINUTES. Titles TMDB has a logo for (the better known) come first, newest
first.

    TMDB_KEY=... [BUDGET=20000] [MAX_MINUTES=120] build_cards.py <folder holding logos.json and state.json>

Only TMDB's API is used, with the owner's key from the workflow's secrets; its website turns away
bulk reading, as it may.
"""
import concurrent.futures
import datetime
import json
import os
import re
import sys
import time
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_logos import fetch, TMDB_API  # noqa: E402

RECHECK_DAYS = 60
COLLECTION_DAYS = 30
NAMES = ("fa", "ar")


def call(path, query, key):
    if not key.startswith("eyJ"):
        query = {**query, "api_key": key}
    headers = {"Accept": "application/json"}
    if key.startswith("eyJ"):
        headers["Authorization"] = "Bearer " + key
    found = fetch(TMDB_API + path + "?" + urllib.parse.urlencode(query), headers)
    return json.loads(found[1]) if found else {}


def shard(imdb):
    """The file a title is kept in: the last two digits of its IMDb number."""
    return re.sub(r"\D", "", imdb)[-2:].rjust(2, "0")


def other_poster(details):
    """The best voted poster that is not the one 30nama and TMDB lead with: English or wordless."""
    main = details.get("poster_path")
    posters = [p for p in (details.get("images") or {}).get("posters") or []
               if p.get("file_path") and p["file_path"] != main and (p.get("iso_639_1") or "") in ("en", "")]
    if not posters:
        return None
    return max(posters, key=lambda p: (p.get("vote_average") or 0, p.get("vote_count") or 0))["file_path"].lstrip("/")


def own_name(details):
    """Its name in its own language, where that is not English and not the name it goes by."""
    original = (details.get("original_title") or details.get("original_name") or "").strip()
    shown = (details.get("title") or details.get("name") or "").strip()
    if not original or details.get("original_language") in (None, "en") or original.casefold() == shown.casefold():
        return None
    return original


def company(details):
    """The first company that made it with a logo; for a series, its network where none has one."""
    for group in ("production_companies", "networks"):
        for c in details.get(group) or []:
            if c.get("logo_path"):
                return c["logo_path"].lstrip("/").replace(".svg", ".png"), c.get("name") or ""
    return None


def collection_entry(collection):
    """A collection as kept: its names and its parts in the order they came out."""
    parts = sorted((p for p in collection.get("parts") or [] if p.get("id")),
                   key=lambda p: (p.get("release_date") or "9999", p["id"]))
    names = {"en": collection.get("name") or ""}
    for t in (collection.get("translations") or {}).get("translations") or []:
        name = ((t.get("data") or {}).get("name") or (t.get("data") or {}).get("title") or "").strip()
        if t.get("iso_639_1") in NAMES and name:
            names[t["iso_639_1"]] = name
    return {"names": names, "parts": [p["id"] for p in parts]}


def card(details, collection=None):
    """What the back of the poster shows, in short keys; empty where TMDB has none of it.

    p: another poster's file name; o: its own name; c: the collection, as {names, at, of};
    l and n: the company's logo file name and its name."""
    found = {}
    poster = other_poster(details)
    if poster:
        found["p"] = poster
    name = own_name(details)
    if name:
        found["o"] = name
    if collection and details.get("id") in collection["parts"] and len(collection["parts"]) > 1:
        found["c"] = {"names": collection["names"], "at": collection["parts"].index(details["id"]) + 1, "of": len(collection["parts"])}
    made = company(details)
    if made:
        found["l"], found["n"] = made
    return found


def look(imdb, key, collections, today):
    found = call(f"find/{imdb}", {"external_source": "imdb_id"}, key)
    for kind, results in (("movie", found.get("movie_results")), ("tv", found.get("tv_results"))):
        if not results:
            continue
        details = call(f"{kind}/{results[0]['id']}", {"append_to_response": "images", "include_image_language": "en,null"}, key)
        if not details:
            return None
        collection = None
        part_of = (details.get("belongs_to_collection") or {}).get("id")
        if part_of:
            kept = collections.get(str(part_of))
            if not kept or (today - datetime.date.fromisoformat(kept["day"])).days >= COLLECTION_DAYS:
                answer = call(f"collection/{part_of}", {"append_to_response": "translations"}, key)
                if answer:
                    kept = {**collection_entry(answer), "day": today.isoformat()}
                    collections[str(part_of)] = kept
            collection = kept
        return card(details, collection)
    return {}


def main():
    folder = sys.argv[1]
    key = os.environ["TMDB_KEY"]
    budget = int(os.environ.get("BUDGET") or 20000)
    today = datetime.date.today()

    def load(name):
        try:
            with open(os.path.join(folder, name), encoding="utf-8") as file:
                return json.load(file)
        except FileNotFoundError:
            return {}

    logos = load("logos.json").get("logos", {})
    titles = set(logos) | set(load("state.json").get("none", {}))
    state = load("cards_state.json")
    # The day each title was last looked up, and the collections met, kept a month.
    done = state.get("done", {})
    collections = state.get("collections", {})
    shards = {}
    for name in os.listdir(os.path.join(folder, "cards")) if os.path.isdir(os.path.join(folder, "cards")) else []:
        if name.endswith(".json"):
            with open(os.path.join(folder, "cards", name), encoding="utf-8") as file:
                shards[name[:-5]] = json.load(file).get("cards", {})

    def number(imdb):
        return int(re.sub(r"\D", "", imdb) or 0)

    new = sorted((i for i in titles if i not in done), key=lambda i: (i not in logos, -number(i)))
    stale = sorted((i for i, day in done.items() if (today - datetime.date.fromisoformat(day)).days >= RECHECK_DAYS), key=lambda i: done[i])
    todo = (new + stale)[:budget]
    print(f"{len(titles)} titles, {len(done)} looked up before, {len(new)} new, {len(stale)} to look at again; up to {budget} this run", flush=True)

    def one(imdb):
        try:
            found = look(imdb, key, collections, today)
        except Exception as error:  # one title's failure is not the run's
            print(f"{imdb}: {error}", flush=True)
            return imdb, None
        time.sleep(0.1)
        return imdb, found

    deadline = time.monotonic() + 60 * int(os.environ.get("MAX_MINUTES") or 120)
    looked = 0
    # Four at a time, each pausing between its own requests: well under TMDB's limit of about forty a second.
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for start in range(0, len(todo), 200):
            if time.monotonic() > deadline:
                break
            for imdb, found in pool.map(one, todo[start:start + 200]):
                if found is None:
                    continue
                looked += 1
                done[imdb] = today.isoformat()
                cards = shards.setdefault(shard(imdb), {})
                if found:
                    cards[imdb] = found
                else:
                    cards.pop(imdb, None)
            print(f"{looked} looked up", flush=True)

    os.makedirs(os.path.join(folder, "cards"), exist_ok=True)
    for name in (f"{n:02d}" for n in range(100)):
        with open(os.path.join(folder, "cards", name + ".json"), "w", encoding="utf-8") as file:
            json.dump({"version": 1, "updated": today.isoformat(), "cards": dict(sorted(shards.get(name, {}).items()))},
                      file, ensure_ascii=False, separators=(",", ":"))
    with open(os.path.join(folder, "cards_state.json"), "w", encoding="utf-8") as file:
        json.dump({"done": dict(sorted(done.items())), "collections": collections}, file, ensure_ascii=False, separators=(",", ":"))
    print(f"done: {looked} looked up; {sum(len(c) for c in shards.values())} titles with something to show", flush=True)


if __name__ == "__main__":
    main()
