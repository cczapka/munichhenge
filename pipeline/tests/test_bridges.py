from pipeline.bridges import bridge_runs
from pipeline.runs import chord_length
from tests.conftest import make_way


def test_bridge_over_straight_river_gives_two_runs_along_the_river(cfg):
    river = make_way([(0, 0), (500, 0), (1000, 0), (1500, 0), (2000, 0), (2500, 0), (3000, 0)],
                     name="Isar", kind="river")
    bridge = make_way([(1000, -80), (1000, 80)], name="Reichenbachbrücke", bridge=True)
    runs = bridge_runs([river, bridge], cfg)
    assert len(runs) == 2
    names = sorted(r.name for r in runs)
    assert names == ["Reichenbachbrücke (Isar downstream)", "Reichenbachbrücke (Isar upstream)"]
    for r in runs:
        assert r.kind == "bridge"
        assert set(r.osm_way_ids) == {river.id, bridge.id}
    lens = sorted(round(chord_length(r)) for r in runs)
    assert lens == [1000, 2000]              # upstream to x=0 (1000 m), downstream to x=3000 (2000 m)


def test_bridge_walk_stops_where_the_river_bends(cfg):
    river = make_way([(0, 0), (800, 0), (1600, 0), (1600, 800), (1600, 1600)], name="Isar", kind="river")
    bridge = make_way([(800, -50), (800, 50)], name="B", bridge=True)
    runs = bridge_runs([river, bridge], cfg)
    lens = sorted(round(chord_length(r)) for r in runs)
    assert lens == [800, 800]


def test_bridge_walk_is_capped_at_max_view(cfg):
    river = make_way([(x, 0) for x in range(0, 6001, 500)], name="Isar", kind="river")
    bridge = make_way([(3000, -50), (3000, 50)], name="B", bridge=True)
    runs = bridge_runs([river, bridge], cfg)
    for r in runs:
        assert chord_length(r) <= cfg.bridge_max_view_m + 1


def test_non_bridge_or_no_river_gives_nothing(cfg):
    river = make_way([(0, 0), (3000, 0)], name="Isar", kind="river")
    street = make_way([(1000, -80), (1000, 80)], name="S")
    assert bridge_runs([river, street], cfg) == []
    bridge = make_way([(1000, -80), (1000, 80)], name="B", bridge=True)
    assert bridge_runs([bridge], cfg) == []
