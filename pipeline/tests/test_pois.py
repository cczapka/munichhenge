from pipeline.ids import assign_sightline_ids, poi_id
from pipeline.models import Run
from pipeline.pois import snap_pois
from tests.conftest import ll, make_poi, xy


def street(x0, x1, y=0, name="S"):
    return Run(name=name, kind="street", a=ll(x0, y), b=ll(x1, y), points=[xy(x0, y), xy(x1, y)], osm_way_ids=[])


def views(poi):
    return sorted((v.sightline_id, v.toward, v.distance_m) for v in poi.views)


def test_poi_near_end_a_looks_toward_b(cfg):
    s = street(0, 800)
    assign_sightline_ids([s])
    pois = snap_pois([make_poi(30, 12)], [s], cfg)
    assert len(pois) == 1
    assert views(pois[0]) == [(s.id, "b", 12.0)]


def test_poi_in_the_middle_looks_both_ways(cfg):
    s = street(0, 800)
    assign_sightline_ids([s])
    pois = snap_pois([make_poi(400, -20)], [s], cfg)
    assert views(pois[0]) == [(s.id, "a", 20.0), (s.id, "b", 20.0)]


def test_poi_too_far_from_chord_is_dropped(cfg):
    s = street(0, 800)
    assign_sightline_ids([s])
    assert snap_pois([make_poi(400, 41)], [s], cfg) == []
    assert len(snap_pois([make_poi(400, 39)], [s], cfg)) == 1


def test_poi_beyond_the_end_uses_segment_distance(cfg):
    s = street(0, 800)
    assign_sightline_ids([s])
    assert snap_pois([make_poi(860, 0)], [s], cfg) == []       # 60 m past the end
    pois = snap_pois([make_poi(820, 0)], [s], cfg)             # 20 m past end b: looks toward a
    assert views(pois[0]) == [(s.id, "a", 20.0)]


def test_poi_on_two_sightlines_gets_both(cfg):
    s1 = street(0, 800, y=0, name="S1")
    s2 = street(0, 800, y=30, name="S2")
    assign_sightline_ids([s1, s2])
    pois = snap_pois([make_poi(100, 15)], [s1, s2], cfg)
    assert {v[0] for v in views(pois[0])} == {s1.id, s2.id}


def test_poi_ids_and_record_fields(cfg):
    assert poi_id("node/123") == "poi_n123"
    assert poi_id("way/45") == "poi_w45"
    assert poi_id("relation/6") == "poi_r6"
    s = street(0, 800)
    assign_sightline_ids([s])
    p = snap_pois([make_poi(30, 12, name="Bar X", kind="bar")], [s], cfg)[0]
    assert p.id.startswith("poi_n") and p.kind == "bar" and p.name == "Bar X" and p.open_horizon is False


def test_sightline_ids_are_stable_and_unique():
    a = street(0, 800)
    b = street(0, 800)
    c = street(0, 900)
    assign_sightline_ids([a, b, c])
    assert a.id != b.id and a.id != c.id
    assert a.id.startswith("sl_") and len(a.id) == 11
    again = street(0, 800)
    assign_sightline_ids([again])
    assert again.id == a.id
