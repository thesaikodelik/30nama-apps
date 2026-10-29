#!/usr/bin/env python3
"""Tests for build_cards.py, without TMDB: answers shaped as TMDB's, cut down.

    python3 -I Tools/logos/test_cards.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_cards import card, collection_entry, shard  # noqa: E402

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

    def test_shard(self):
        self.assertEqual("03", shard("tt0234203"))
        self.assertEqual("07", shard("tt7"))


if __name__ == "__main__":
    unittest.main()
