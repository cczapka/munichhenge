"""Whole pipeline on a synthetic PBF: extract -> clip -> build -> JSON/GeoJSON."""
import json
import os

import pytest

from pipeline import run as runmod
from pipeline.clip import clip_extract
from pipeline.extract import read_pbf
from tests.conftest import PbfBuilder, ll


def E(x_m, y_m):
    """Point x_m east along the parallel and y_m north: true-east geometry (grid east is
    ~1.9° off in Munich, so everything in this fixture uses the same frame)."""
    return (ll(0, y_m)[0], ll(x_m, 0)[1])


@pytest.fixture
def pbf(tmp_path):
    b = PbfBuilder()
    ll = E  # noqa: F811 — every coordinate below is true-east/north
    # Teststraße: 3 ways sharing end nodes, 1500 m due east
    n = [b.node(ll(x, 0)) for x in (0, 500, 1000, 1500)]
    for i in range(3):
        b.way(None, {"highway": "residential", "name": "Teststraße"}, node_ids=[n[i], n[i + 1]])
    # a parallel cycleway 8 m south -> deduped into Teststraße
    b.way([ll(100, -8), ll(1400, -8)], {"highway": "cycleway"})
    # an L-shaped street: two 350 m legs -> no run >= 400 m
    b.way([ll(0, 300), ll(350, 300), ll(350, 650)], {"highway": "residential", "name": "Knickstraße"})
    # a street outside the admin polygon but inside the bbox
    b.way([ll(0, 5000), ll(800, 5000)], {"highway": "residential", "name": "Außenstraße"})
    # a river with a bridge over it
    b.way([ll(2000 + x, -1500) for x in (0, 500, 1000, 1500, 2000)], {"waterway": "river", "name": "Isar"})
    b.way([ll(3000, -1600), ll(3000, -1400)], {"highway": "secondary", "name": "Brücke", "bridge": "yes"})
    # buildings flanking Teststraße (both sides) with levels
    for x in range(0, 1500, 30):
        for y in (12, -28):
            b.polygon([ll(x, y), ll(x + 26, y), ll(x + 26, y + 14), ll(x, y + 14)],
                      {"building": "yes", "building:levels": "5"})
    # POIs: a café near end a of Teststraße, a bar far from everything, a restaurant mapped as a building
    b.node(ll(40, 15), {"amenity": "cafe", "name": "Café A", "opening_hours": "Mo-Su 09:00-23:00"})
    b.node(ll(0, 2000), {"amenity": "bar", "name": "Nowhere Bar"})
    b.polygon([ll(1400, 14), ll(1420, 14), ll(1420, 30), ll(1400, 30)],
              {"building": "yes", "amenity": "restaurant", "name": "Restaurant B", "website": "https://b.example"})
    # a park with a long track inside, and a forest track outside any park
    b.polygon([ll(-100, 1400), ll(1200, 1400), ll(1200, 1800), ll(-100, 1800)], {"leisure": "park", "name": "Park"})
    b.way([ll(0, 1600), ll(1000, 1600)], {"highway": "track", "name": "Parkallee"})
    b.way([ll(0, -800), ll(1000, -800)], {"highway": "track", "name": "Forstweg"})
    # admin polygon: covers everything except Außenstraße
    ring = [ll(-500, -3000), ll(6000, -3000), ll(6000, 3000), ll(-500, 3000)]
    ids = [b.node(c) for c in ring]
    wid = b.way(ring, {}, node_ids=ids + [ids[0]])
    b.relation([("w", wid, "outer")], {"type": "boundary", "boundary": "administrative", "admin_level": "6",
                                       "name": "München"})
    return b.write(str(tmp_path / "synthetic.osm.pbf"))


@pytest.fixture
def featured_yaml(tmp_path):
    a, bb, s = E(0, 1000), E(700, 1000), E(50, 40)
    p = tmp_path / "featured.yaml"
    p.write_text(f"""
sightlines:
  - id: f_axis
    name: Test Axis
    kind: axis
    a: [{a[0]}, {a[1]}]
    b: [{bb[0]}, {bb[1]}]
    obstruction_toward_b_deg: 3.0
spots:
  - id: s_hill
    name: Hill
    kind: viewpoint
    at: [{s[0]}, {s[1]}]
    open_horizon: true
    sightline_ids: ["auto:Teststraße"]
""", encoding="utf-8")
    return str(p)


def test_extract_reads_everything(pbf, cfg):
    ex = read_pbf(pbf, cfg)
    names = sorted(w.name or "" for w in ex.ways)
    assert names == ["", "Außenstraße", "Brücke", "Forstweg", "Isar", "Knickstraße", "Parkallee",
                     "Teststraße", "Teststraße", "Teststraße"]
    assert ex.admin_polygon is not None
    assert len(ex.parks) == 1
    assert len(ex.buildings) == 100 + 1
    kinds = sorted(p.kind for p in ex.pois)
    assert kinds == ["bar", "cafe", "restaurant"]
    rest = next(p for p in ex.pois if p.kind == "restaurant")
    assert rest.osm_id.startswith("way/") and rest.tags == {"website": "https://b.example"}


def test_clip_to_admin_polygon_drops_outside_street(pbf, cfg):
    ex = clip_extract(read_pbf(pbf, cfg), cfg, mode="admin")
    assert "Außenstraße" not in {w.name for w in ex.ways}
    ex_bbox = clip_extract(read_pbf(pbf, cfg), cfg, mode="bbox")
    assert "Außenstraße" in {w.name for w in ex_bbox.ways}


def test_cli_end_to_end(pbf, featured_yaml, tmp_path, cfg):
    out = tmp_path / "data"
    dbg = tmp_path / "debug"
    rc = runmod.main(["--pbf", pbf, "--out", str(out), "--featured", featured_yaml, "--debug-geojson", str(dbg),
                      "--dump-names", "Teststraße,Knickstraße"])
    assert rc == 0
    dump = json.loads((dbg / "dump" / "teststra_e.geojson").read_text(encoding="utf-8"))
    groups = [f["properties"]["group"] for f in dump["features"]]
    assert groups.count("way") == 3 and groups.count("chain") == 1 and groups.count("run") == 1
    dump2 = json.loads((dbg / "dump" / "knickstra_e.geojson").read_text(encoding="utf-8"))
    assert [f["properties"]["kept"] for f in dump2["features"] if f["properties"]["group"] == "run"] == [False, False]
    sl = json.loads((out / "sightlines.json").read_text(encoding="utf-8"))
    po = json.loads((out / "pois.json").read_text(encoding="utf-8"))
    assert sl["version"] == 1 and po["version"] == 1
    assert sl["params"] == cfg.params_for_json()

    ids = [s["id"] for s in sl["sightlines"]]
    assert ids == sorted(ids)
    by_name = {s["name"]: s for s in sl["sightlines"]}
    assert set(by_name) == {"Teststraße", "Test Axis", "Hill", "Isar", "Parkallee",
                            "Brücke (Isar downstream)", "Brücke (Isar upstream)"}   # Forstweg: track outside park
    assert by_name["Isar"]["kind"] == "river"

    t = by_name["Teststraße"]
    assert t["length_m"] == pytest.approx(1500, abs=2)
    assert t["bearing_ab"] == pytest.approx(90.0, abs=0.1)        # true north, not grid north
    assert t["bearing_ba"] == pytest.approx(270.0, abs=0.1)
    assert len(t["osm_way_ids"]) == 4                              # 3 street ways + the deduped cycleway
    assert t["canyon"] is True
    # buildings with 5 levels near both ends: H = 5*3.2+2 = 18 m
    import math
    assert t["obstruction_toward_b_deg"] == pytest.approx(math.degrees(math.atan((18 - 1.7) / 1500)), abs=0.02)
    assert t["featured"] is False and t["kind"] == "street" and t["max_offset_m"] == 0

    f = by_name["Test Axis"]
    assert f["featured"] is True and f["obstruction_toward_b_deg"] == 3.0 and f["id"] == "f_axis"
    assert f["obstruction_toward_a_deg"] == pytest.approx(math.degrees(math.atan(18.3 / 700)), abs=0.02)

    h = by_name["Hill"]
    assert h["kind"] == "open_horizon" and h["bearing_ab"] is None and h["a"] == h["b"] and h["length_m"] == 0
    assert h["obstruction_toward_a_deg"] == cfg.open_horizon_obstruction_deg

    b = by_name["Brücke (Isar downstream)"]
    assert b["kind"] == "bridge" and b["obstruction_toward_b_deg"] == cfg.bridge_obstruction_deg
    assert b["length_m"] == pytest.approx(1000, abs=2)

    pids = [p["id"] for p in po["pois"]]
    assert pids == sorted(pids)
    by_poi = {p["name"]: p for p in po["pois"]}
    assert set(by_poi) == {"Café A", "Restaurant B", "Hill"}       # Nowhere Bar has no sightline
    cafe = by_poi["Café A"]
    assert cafe["views"] == [{"sightline_id": t["id"], "toward": "b", "distance_m": 15.0}]
    assert cafe["tags"] == {"opening_hours": "Mo-Su 09:00-23:00"} and cafe["osm_id"].startswith("node/")
    rest = by_poi["Restaurant B"]
    assert [v["toward"] for v in rest["views"]] == ["a"]           # near end b -> looks toward a
    hill = by_poi["Hill"]
    assert hill["featured"] is True and hill["open_horizon"] is True and hill["osm_id"] is None
    assert {(v["sightline_id"], v["toward"]) for v in hill["views"]} >= {(t["id"], "b"), (h["id"], "b")}

    gj = json.loads((dbg / "sightlines.geojson").read_text(encoding="utf-8"))
    assert gj["type"] == "FeatureCollection" and len(gj["features"]) == len(sl["sightlines"])
    line = next(ft for ft in gj["features"] if ft["properties"]["name"] == "Teststraße")
    assert line["geometry"]["type"] == "LineString" and line["properties"]["bearing_bucket"] == "090-120"
    assert "stroke" in line["properties"]
    pj = json.loads((dbg / "pois.geojson").read_text(encoding="utf-8"))
    assert len(pj["features"]) == len(po["pois"])

    total = os.path.getsize(out / "sightlines.json") + os.path.getsize(out / "pois.json")
    assert total < runmod.MAX_TOTAL_BYTES


def test_cli_is_deterministic(pbf, featured_yaml, tmp_path):
    outs = []
    for i in range(2):
        out = tmp_path / f"d{i}"
        assert runmod.main(["--pbf", pbf, "--out", str(out), "--featured", featured_yaml]) == 0
        outs.append(((out / "sightlines.json").read_text(), (out / "pois.json").read_text()))
    assert outs[0] == outs[1]
