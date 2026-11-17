"""External links (Street View, walking directions, geo:) against the vectors shared with
the Kotlin implementation in app/core-engine."""
import json
import locale
import os

import pytest

from pipeline.links import geo_url, streetview_url, walk_url

VECTORS = os.path.join(os.path.dirname(__file__), "..", "..", "testdata", "links_vectors.json")
CASES = json.load(open(VECTORS, encoding="utf-8"))["cases"]


@pytest.mark.parametrize("c", CASES, ids=[c["name"] for c in CASES])
def test_shared_vectors(c):
    assert streetview_url(c["lat"], c["lon"], c["bearing"], c["obstruction"]) == c["streetview"]
    assert walk_url(c["lat"], c["lon"]) == c["walk"]
    assert geo_url(c["lat"], c["lon"], c["label"]) == c["geo"]


def test_unaffected_by_a_german_locale():
    try:
        locale.setlocale(locale.LC_NUMERIC, "de_DE.UTF-8")
    except locale.Error:
        pytest.skip("de_DE locale not installed")
    try:
        assert "48.143050,11.595060" in streetview_url(48.14305, 11.59506, 287.0, 1.13)
    finally:
        locale.setlocale(locale.LC_NUMERIC, "C")
