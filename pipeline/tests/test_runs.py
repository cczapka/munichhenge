import dataclasses

import pytest

from pipeline.models import Run
from pipeline.runs import chord_length, dedupe_runs, extract_runs, split_points
from tests.conftest import ll, make_way, xy


def lengths(runs):
    return sorted(round(chord_length(r)) for r in runs)


def test_straight_way_is_one_run(cfg):
    w = make_way([(0, 0), (100, 0), (250, 0), (500, 0)])
    runs = extract_runs([w], cfg)
    assert lengths(runs) == [500]
    assert runs[0].osm_way_ids == [w.id]
    assert runs[0].max_offset_m == pytest.approx(0, abs=1e-6)
    assert runs[0].a == w.coords[0] and runs[0].b == w.coords[-1]


def test_slightly_bent_way_is_one_run(cfg):
    # 1000 m with nodes wandering up to 4 m off the chord and segment bearings within 2°
    pts = [(0, 0), (200, 3), (400, 4), (600, 2), (800, -3), (1000, 0)]
    runs = extract_runs([make_way(pts)], cfg)
    assert lengths(runs) == [1000]
    assert 3.5 < runs[0].max_offset_m < 4.5


def test_sharply_bent_way_splits_at_the_corner(cfg):
    pts = [(0, 0), (300, 0), (600, 0), (600, 300), (600, 600)]
    runs = extract_runs([make_way(pts)], cfg)
    assert lengths(runs) == [600, 600]
    names = {r.name for r in runs}
    assert names == {"Teststraße"}


def test_gentle_curve_splits_into_straight_pieces(cfg):
    # a wide arc: consecutive 100 m segments turn by 1° each -> chord/offset limits break it
    import math
    pts, x, y, ang = [(0.0, 0.0)], 0.0, 0.0, 0.0
    for _ in range(20):
        x += 100 * math.cos(math.radians(ang))
        y += 100 * math.sin(math.radians(ang))
        pts.append((x, y))
        ang += 1.0
    runs = extract_runs([make_way(pts)], cfg)
    assert 2 <= len(runs) <= 5
    for r in runs:
        assert r.max_offset_m < cfg.max_offset_m


def test_short_noisy_segments_do_not_fragment_a_street(cfg):
    """Real OSM: nodes every few tens of metres with ~1 m lateral survey noise (3-4° of
    bearing noise per segment). Physically one straight street; must be one run."""
    pts = [(x, [0.0, 1.2, -0.8, 0.5, -1.1, 0.9][i % 6]) for i, x in enumerate(range(0, 801, 40))]
    runs = extract_runs([make_way(pts)], cfg)
    assert lengths(runs) == [800]


def test_real_kink_still_splits(cfg):
    # two 300 m legs meeting at 5°: the corner is 13 m off the joint chord -> two runs
    import math
    pts = [(0, 0), (300, 0), (300 + 300 * math.cos(math.radians(5)), 300 * math.sin(math.radians(5)))]
    c = dataclasses.replace(cfg, min_length_m=250)
    assert lengths(extract_runs([make_way(pts)], c)) == [300, 300]


def test_split_points_covers_all_nodes(cfg):
    pts = [xy(0, 0), xy(100, 0), xy(100, 100), xy(200, 100), xy(200, 0)]
    segs = split_points(pts, cfg)
    assert segs == [(0, 1), (1, 2), (2, 3), (3, 4)]
    assert segs[0][0] == 0 and segs[-1][1] == len(pts) - 1


def test_runs_shorter_than_min_length_are_dropped(cfg):
    runs = extract_runs([make_way([(0, 0), (399, 0)])], cfg)
    assert runs == []
    runs = extract_runs([make_way([(0, 0), (401, 0)])], cfg)
    assert len(runs) == 1


def test_split_across_ways_with_same_name_merges(cfg):
    # OSM splits a street at every junction: three ways sharing end nodes
    w1 = make_way([(0, 0), (200, 0)], node_ids=[1, 2], way_id=1)
    w2 = make_way([(200, 0), (450, 0)], node_ids=[2, 3], way_id=2)
    w3 = make_way([(450, 0), (700, 0)], node_ids=[3, 4], way_id=3)
    runs = extract_runs([w1, w3, w2], cfg)
    assert lengths(runs) == [700]
    assert runs[0].osm_way_ids == [1, 2, 3]


def test_reversed_way_direction_still_merges(cfg):
    w1 = make_way([(0, 0), (300, 0)], node_ids=[1, 2], way_id=1)
    w2 = make_way([(600, 0), (300, 0)], node_ids=[3, 2], way_id=2)   # drawn the other way round
    runs = extract_runs([w1, w2], cfg)
    assert lengths(runs) == [600]
    assert runs[0].osm_way_ids == [1, 2]


def test_different_names_do_not_merge(cfg):
    w1 = make_way([(0, 0), (300, 0)], name="A-Straße", node_ids=[1, 2])
    w2 = make_way([(300, 0), (600, 0)], name="B-Straße", node_ids=[2, 3])
    assert extract_runs([w1, w2], cfg) == []          # each is < 400 m on its own
    w3 = make_way([(0, 0), (300, 0)], name=None, node_ids=[1, 2])
    w4 = make_way([(300, 0), (600, 0)], name=None, node_ids=[2, 3])
    assert extract_runs([w3, w4], cfg) == []          # unnamed ways are never stitched


def test_fork_joins_the_straightest_continuation(cfg):
    trunk = make_way([(0, 0), (300, 0)], node_ids=[1, 2], way_id=1)
    straight = make_way([(300, 0), (600, 0)], node_ids=[2, 3], way_id=2)
    branch = make_way([(300, 0), (300, 500)], node_ids=[2, 4], way_id=3)
    runs = extract_runs([branch, trunk, straight], cfg)
    assert lengths(runs) == [500, 600]
    long = next(r for r in runs if round(chord_length(r)) == 600)
    assert long.osm_way_ids == [1, 2]


def test_merged_chain_still_splits_at_bends(cfg):
    w1 = make_way([(0, 0), (500, 0)], node_ids=[1, 2])
    w2 = make_way([(500, 0), (500, 500)], node_ids=[2, 3])
    runs = extract_runs([w1, w2], cfg)
    assert lengths(runs) == [500, 500]


def test_small_jog_mid_street_does_not_break_run(cfg):
    # a 2 m jog at a crossing (very common in OSM) must not cut a 800 m street in two
    pts = [(0, 0), (400, 0), (402, 1.5), (800, 1.5)]
    runs = extract_runs([make_way(pts)], cfg)
    assert lengths(runs) == [800]


def test_footway_min_length_rule(cfg):
    short = make_way([(0, 0), (450, 0)], highway="footway")
    assert extract_runs([short], cfg) != []           # 450 >= footway_min_length (300) and >= 400
    c = dataclasses.replace(cfg, footway_min_length_m=500)
    assert extract_runs([short], c) == []


def test_tracks_only_inside_parks(cfg):
    park = [xy(-50, -50), xy(2000, -50), xy(2000, 300), xy(-50, 300), xy(-50, -50)]
    inside = make_way([(0, 100), (800, 100)], name="Parkweg", highway="track")
    outside = make_way([(0, 1000), (800, 1000)], name="Forstweg", highway="track")
    assert [r.name for r in extract_runs([inside, outside], cfg, parks=[park])] == ["Parkweg"]
    assert extract_runs([inside, outside], cfg) == []                 # no parks known -> no tracks
    street = make_way([(0, 1000), (800, 1000)], name="Straße")
    assert [r.name for r in extract_runs([street], cfg)] == ["Straße"]


def test_duplicate_consecutive_nodes_are_ignored(cfg):
    w = make_way([(0, 0), (200, 0), (200, 0), (500, 0)])
    runs = extract_runs([w], cfg)
    assert lengths(runs) == [500]


# ----------------------------------------------------------------------------- dedupe

def run_from(points_m, name="X", way_ids=(1,), featured=False):
    pts = [xy(x, y) for x, y in points_m]
    return Run(name=name, kind="street", a=ll(*points_m[0]), b=ll(*points_m[-1]), points=pts,
               osm_way_ids=list(way_ids), featured=featured, id=None)


def test_parallel_carriageways_collapse_into_the_longer(cfg):
    a = run_from([(0, 0), (1000, 0)], way_ids=[1])
    b = run_from([(50, 10), (900, 10)], way_ids=[2])      # 10 m to the side, same bearing
    kept = dedupe_runs([b, a], cfg)
    assert len(kept) == 1
    assert round(chord_length(kept[0])) == 1000
    assert kept[0].osm_way_ids == [1, 2]


def test_parallel_but_far_apart_is_kept(cfg):
    a = run_from([(0, 0), (1000, 0)])
    b = run_from([(0, 40), (1000, 40)])
    assert len(dedupe_runs([a, b], cfg)) == 2


def test_collinear_but_not_overlapping_is_kept(cfg):
    a = run_from([(0, 0), (1000, 0)])
    b = run_from([(1100, 0), (1700, 0)])
    assert len(dedupe_runs([a, b], cfg)) == 2


def test_close_but_different_bearing_is_kept(cfg):
    a = run_from([(0, 0), (1000, 0)])
    b = run_from([(0, 5), (1000, 40)])   # 2° off
    assert len(dedupe_runs([a, b], cfg)) == 2


def test_unnamed_duplicate_gives_its_name_to_nothing_but_named_gives_name(cfg):
    a = run_from([(0, 0), (1000, 0)], name=None, way_ids=[1])
    b = run_from([(0, 5), (900, 5)], name="Radweg", way_ids=[2])
    kept = dedupe_runs([a, b], cfg)
    assert len(kept) == 1 and kept[0].name == "Radweg"


def test_featured_run_absorbs_its_auto_duplicate_whatever_the_length(cfg):
    a = run_from([(0, 0), (1000, 0)], way_ids=[1])
    f = run_from([(0, 5), (900, 5)], featured=True)
    kept = dedupe_runs([a, f], cfg)
    assert len(kept) == 1 and kept[0].featured
    far = run_from([(0, 40), (1000, 40)])
    assert len(dedupe_runs([far, f], cfg)) == 2


def test_bridge_run_is_not_deduped_into_the_river(cfg):
    river = run_from([(0, 0), (2000, 0)], name="Isar")
    river.kind = "river"
    bridge = run_from([(1000, 0), (2000, 0)], name="Brücke (Isar downstream)")
    bridge.kind = "bridge"
    assert len(dedupe_runs([river, bridge], cfg)) == 2
