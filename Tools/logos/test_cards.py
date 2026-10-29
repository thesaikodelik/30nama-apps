#!/usr/bin/env python3
"""Tests for build_cards.py, without TMDB: answers shaped as TMDB's, cut down.

    python3 -I Tools/logos/test_cards.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import datetime  # noqa: E402
import build_cards  # noqa: E402
from build_cards import card, collection_entry, look, restamp, shard  # noqa: E402

MOVIE = {
    "id": 604, "title": "The Matrix Reloaded", "original_title": "The Matrix Reloaded", "original_language": "en",
    "poster_path": "/main.jpg",
    "images": {"posters": [
        {"file_path": "/main.jpg", "iso_639_1": "en", "vote_average": 9},
        {"file_path": "/french.jpg", "iso_639_1": "fr", "vote_average": 8},
        {"file_path": "/second.jpg", "iso_639_1": "en", "vote_average": 6},
        {"file_path": "/wordless.jpg", "iso_639_1": None, "vote_average": 7},
    ]},
    "belongs_to_collection": {"id": 2344},
    "production_companies": [{"name": "Silver", "logo_path": None}, {"name": "Village Roadshow", "logo_path": "/vr.svg"}],
}
COLLECTION = {
    "name": "The Matrix Collection",
    "parts": [{"id": 605, "release_date": "2003-11-05"}, {"id": 603, "release_date": "1999-03-31"}, {"id": 604, "release_date": "2003-05-15"}],
    "translations": {"translations": [{"iso_639_1": "fa", "data": {"name": "مجموعهٔ ماتریکس"}}, {"iso_639_1": "de", "data": {"name": "Matrix"}}]},
}


class CardTests(unittest.TestCase):
    def test_a_movie_in_a_collection(self):
        found = card(MOVIE, collection_entry(COLLECTION))
        self.assertEqual("wordless.jpg", found["p"], "the best voted other poster, English or wordless")
        self.assertNotIn("o", found, "an English title has no name of its own to show")
        self.assertEqual({"names": {"en": "The Matrix Collection", "fa": "مجموعهٔ ماتریکس"}, "at": 2, "of": 3}, found["c"],
                         "its place by release, names only in the app's languages")
        self.assertEqual(("vr.png", "Village Roadshow"), (found["l"], found["n"]), "the first company with a logo, as a picture")

    def test_its_own_name_only_where_it_differs(self):
        spirited = {"id": 129, "name": "Spirited Away", "original_title": "千と千尋の神隠し", "title": "Spirited Away", "original_language": "ja"}
        self.assertEqual("千と千尋の神隠し", card(spirited)["o"])
        same = {"id": 1, "title": "Amélie", "original_title": "amélie", "original_language": "fr"}
        self.assertNotIn("o", card(same))

    def test_a_series_falls_back_to_its_network(self):
        series = {"id": 1399, "name": "Dark", "original_name": "Dark", "original_language": "de",
                  "production_companies": [{"name": "Wiedemann", "logo_path": None}], "networks": [{"name": "Netflix", "logo_path": "/nf.png"}]}
        self.assertEqual(("nf.png", "Netflix"), (card(series)["l"], card(series)["n"]))

    def test_nothing_to_show(self):
        self.assertEqual({}, card({"id": 1, "title": "X", "original_title": "X", "original_language": "en", "poster_path": "/a.jpg",
                                   "images": {"posters": [{"file_path": "/a.jpg"}]}}))

    def test_a_collection_of_one_is_none(self):
        alone = collection_entry({"name": "Solo", "parts": [{"id": 604}]})
        self.assertNotIn("c", card(MOVIE, alone))

    def test_a_new_part_tells_the_others(self):
        three = collection_entry({"name": "Duo", "parts": [{"id": 1, "release_date": "2000"}, {"id": 2, "release_date": "2001"}, {"id": 3, "release_date": "2026"}]})
        shards = {"01": {"tt0000001": {"c": {"names": {"en": "Duo"}, "at": 1, "of": 2}}}, "02": {"tt0000002": {"o": "Zwei"}}}
        restamp(shards, {"tt0000001": 1, "tt0000002": 2}, three)
        self.assertEqual({"names": {"en": "Duo"}, "at": 1, "of": 3}, shards["01"]["tt0000001"]["c"])
        self.assertEqual({"names": {"en": "Duo"}, "at": 2, "of": 3}, shards["02"]["tt0000002"]["c"], "a card without its place gains it")

    def test_a_member_with_no_card_gains_its_place(self):
        two = collection_entry({"name": "Duo", "parts": [{"id": 1, "release_date": "2000"}, {"id": 2, "release_date": "2026"}]})
        shards = {}
        restamp(shards, {"tt0000001": 1}, two)
        self.assertEqual({"names": {"en": "Duo"}, "at": 1, "of": 2}, shards["01"]["tt0000001"]["c"])
        alone = collection_entry({"name": "Duo", "parts": [{"id": 1, "release_date": "2000"}]})
        restamp(shards, {"tt0000001": 1}, alone)
        self.assertNotIn("tt0000001", shards["01"], "a card left with nothing goes")

    def test_details_not_there_is_an_empty_card(self):
        real = build_cards.call
        build_cards.call = lambda path, query, key: {"movie_results": [{"id": 5}]} if path.startswith("find/") else {}
        try:
            self.assertEqual(({}, None, None), look("tt5", "k", {}, datetime.date(2026, 10, 29), set()))
        finally:
            build_cards.call = real

    def test_a_part_the_kept_collection_lacks_asks_again(self):
        today = datetime.date(2026, 10, 29)
        collections = {"9": {**collection_entry({"name": "Duo", "parts": [{"id": 1, "release_date": "2000"}, {"id": 2, "release_date": "2001"}]}), "day": "2026-10-28"}}
        answers = {
            "find/tt3": {"movie_results": [{"id": 3}]},
            "movie/3": {"id": 3, "title": "Three", "original_title": "Three", "original_language": "en", "belongs_to_collection": {"id": 9}},
            "collection/9": {"name": "Duo", "parts": [{"id": 1, "release_date": "2000"}, {"id": 2, "release_date": "2001"}, {"id": 3, "release_date": "2026"}]},
        }
        real = build_cards.call
        build_cards.call = lambda path, query, key: answers.get(path, {})
        try:
            changed = set()
            found, part_of, tmdb_id = look("tt3", "k", collections, today, changed)
        finally:
            build_cards.call = real
        self.assertEqual({"names": {"en": "Duo"}, "at": 3, "of": 3}, found["c"], "asked again though kept a day ago")
        self.assertEqual(("9", 3, {"9"}), (part_of, tmdb_id, changed))

    def test_shard(self):
        self.assertEqual("03", shard("tt0234203"))
        self.assertEqual("07", shard("tt7"))


if __name__ == "__main__":
    unittest.main()
