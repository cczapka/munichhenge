from pipeline.canyon import canyon_fraction, fill_canyon
from pipeline.models import Run
from pipeline.obstruction import BuildingIndex
from tests.conftest import ll, make_building, xy


def street(length=600):
    return Run(name="s", kind="street", a=ll(0, 0), b=ll(length, 0), points=[xy(0, 0), xy(length, 0)], osm_way_ids=[])


def flank(y, length=600, step=30, gap_every=None):
    out = []
    for i, x in enumerate(range(0, length, step)):
        if gap_every and i % gap_every == 0:
            continue
        out.append(make_building(x, y, w_m=step - 2, h_m=12))
    return out


def test_buildings_on_both_sides_is_canyon(cfg):
    idx = BuildingIndex(flank(12) + flank(-24))   # 12 m north and 12..24 m south of the axis
    assert canyon_fraction(street(), idx, cfg) > 0.95
    r = street()
    fill_canyon([r], idx, cfg)
    assert r.canyon is True


def test_buildings_on_one_side_is_not_canyon(cfg):
    idx = BuildingIndex(flank(12))
    assert canyon_fraction(street(), idx, cfg) == 0.0


def test_buildings_too_far_away_is_not_canyon(cfg):
    idx = BuildingIndex(flank(30) + flank(-42))   # both sides > 25 m away
    assert canyon_fraction(street(), idx, cfg) == 0.0


def test_partial_flanking_uses_min_fraction(cfg):
    idx = BuildingIndex(flank(12, gap_every=2) + flank(-24))   # every second building missing north
    f = canyon_fraction(street(), idx, cfg)
    assert 0.3 < f < 0.7
    r = street()
    fill_canyon([r], idx, cfg)
    assert r.canyon == (f >= cfg.canyon_min_fraction)


def test_no_buildings_or_water_kinds_never_canyon(cfg):
    r = street()
    fill_canyon([r], None, cfg)
    assert r.canyon is False
    c = Run(name="c", kind="canal", a=ll(0, 0), b=ll(600, 0), points=[xy(0, 0), xy(600, 0)], osm_way_ids=[])
    fill_canyon([c], BuildingIndex(flank(12) + flank(-24)), cfg)
    assert c.canyon is False
