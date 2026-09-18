"""Regression tests on real OSM geometry (tests/fixtures/*_ways.json, ODbL).

CLAUDE.md rule 5: real-world facts are checked against independently known values. These
fixtures are the raw ways the pipeline saw for three streets in the first real build; the
expected chords were verified in the debug GeoJSON."""
import json
import os

import pytest

from pipeline.config import PipelineConfig
from pipeline.geom import true_bearings
from pipeline.models import Way
from pipeline.runs import chord_length, dedupe_runs, extract_runs

FIX = os.path.join(os.path.dirname(__file__), "fixtures")


def load_ways(name: str) -> list[Way]:
    doc = json.load(open(os.path.join(FIX, f"{name}_ways.json"), encoding="utf-8"))
    return [Way(id=w["id"], name=name, kind="street", node_ids=w["node_ids"],
                coords=[(c[0], c[1]) for c in w["coords"]], tags=w["tags"]) for w in doc["ways"]]


def runs_for(name: str, cfg: PipelineConfig):
    runs = dedupe_runs(extract_runs(load_ways(name), cfg), cfg)
    return sorted(((chord_length(r), true_bearings(r.a, r.b)[0], r.max_offset_m) for r in runs), reverse=True)


def test_leopoldstrasse_is_one_long_run(cfg):
    # Odeonsplatz/Siegestor -> Münchner Freiheit: the boulevard is straight for ~1.5 km
    # (true bearing ~177°, i.e. looking south toward the Siegestor).
    runs = runs_for("leopoldstra_e", cfg)
    length, bearing, offset = runs[0]
    assert length == pytest.approx(1524, abs=15)
    assert bearing == pytest.approx(177, abs=1)
    assert offset < cfg.max_offset_m


def test_prinzregentenstrasse_west_part_is_found(cfg):
    # Prinz-Carl-Palais -> Prinzregentenbrücke, ~800 m at ~287° (sunset range!)
    runs = runs_for("prinzregentenstra_e", cfg)
    assert any(abs(L - 796) < 15 and abs(b - 287) < 1 for L, b, _ in runs), runs[:5]
    # east of the Isar the street is a gentle arc; it must still yield >= 400 m pieces
    assert sum(1 for L, b, _ in runs if L >= 400 and 85 < b < 110) >= 2


def test_brienner_strasse_is_found(cfg):
    runs = runs_for("brienner_stra_e", cfg)
    assert runs and runs[0][0] >= 400


def test_plan_default_2deg_would_fragment_leopoldstrasse():
    """Documents why merge_tol_deg is not 2°: with PLAN.md's original value the same ways
    give at best a 765 m fragment (greedy) or a bit more with longest-first selection,
    still far below the real 1.5 km."""
    cfg = PipelineConfig(merge_tol_deg=2.0, min_seg_for_bearing_m=5.0)
    runs = runs_for("leopoldstra_e", cfg)
    assert runs[0][0] < 1000
