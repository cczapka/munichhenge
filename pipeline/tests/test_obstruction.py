import math

import pytest

from pipeline.models import Run
from pipeline.obstruction import (
    BuildingIndex,
    building_height_m,
    default_obstruction_deg,
    fill_obstructions,
    obstruction_toward,
)
from tests.conftest import ll, make_building, xy


def test_default_formula_matches_plan_numbers(cfg):
    # PLAN.md: 400 m street ~ 2.6°, 2 km canal ~ 0.5° with H=20 m, eye 1.7 m
    assert default_obstruction_deg(400, cfg) == pytest.approx(math.degrees(math.atan(18.3 / 400)))
    assert default_obstruction_deg(400, cfg) == pytest.approx(2.62, abs=0.01)
    assert default_obstruction_deg(2000, cfg) == pytest.approx(0.52, abs=0.01)
    assert default_obstruction_deg(0, cfg) == 90.0


def test_building_height_from_levels_or_height(cfg):
    assert building_height_m(make_building(0, 0, levels=5), cfg) == pytest.approx(5 * 3.2 + 2)
    assert building_height_m(make_building(0, 0, height_m=31.5, levels=2), cfg) == 31.5
    assert building_height_m(make_building(0, 0), cfg) is None


def test_obstruction_uses_median_of_nearby_buildings(cfg):
    idx = BuildingIndex([
        make_building(10, 10, levels=4),      # 14.8 m
        make_building(-30, 10, levels=6),     # 21.2 m
        make_building(10, -30, levels=8),     # 27.6 m
        make_building(500, 500, levels=30),   # far away, ignored
        make_building(-20, -20),              # no height info, ignored
    ])
    end, far = xy(0, 0), xy(-1000, 0)
    ob = obstruction_toward(end, far, idx, cfg)
    assert ob == pytest.approx(math.degrees(math.atan((21.2 - 1.7) / 1000)), abs=1e-6)


def test_obstruction_falls_back_to_default_without_buildings(cfg):
    end, far = xy(0, 0), xy(-1000, 0)
    assert obstruction_toward(end, far, None, cfg) == pytest.approx(default_obstruction_deg(1000, cfg))
    assert obstruction_toward(end, far, BuildingIndex([]), cfg) == pytest.approx(default_obstruction_deg(1000, cfg))


def test_fill_obstructions_keeps_featured_values_and_handles_bridges(cfg):
    r = Run(name="f", kind="axis", a=ll(0, 0), b=ll(800, 0), points=[xy(0, 0), xy(800, 0)], osm_way_ids=[],
            featured=True, obstruction_toward_b_deg=3.0)
    b = Run(name="b", kind="bridge", a=ll(0, 0), b=ll(800, 0), points=[xy(0, 0), xy(800, 0)], osm_way_ids=[])
    fill_obstructions([r, b], None, cfg)
    assert r.obstruction_toward_b_deg == 3.0
    assert r.obstruction_toward_a_deg == pytest.approx(default_obstruction_deg(800, cfg))
    assert b.obstruction_toward_a_deg == cfg.bridge_obstruction_deg
    assert b.obstruction_toward_b_deg == cfg.bridge_obstruction_deg
