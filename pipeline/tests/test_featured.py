import logging
import textwrap

import pytest

from pipeline.featured import (
    featured_pois,
    load_featured,
    merge_featured_sightlines,
    open_horizon_run,
    validate_endpoints,
)
from pipeline.ids import assign_sightline_ids
from pipeline.models import Run
from tests.conftest import ll, make_way, xy


def write_yaml(tmp_path, text):
    p = tmp_path / "featured.yaml"
    p.write_text(textwrap.dedent(text), encoding="utf-8")
    return p


def test_load_featured_parses_sightlines_and_spots(tmp_path, cfg):
    a, b, s = ll(0, 0), ll(1000, 0), ll(500, 10)
    p = write_yaml(tmp_path, f"""
        sightlines:
          - id: f_axis
            name: Test Axis
            kind: axis
            a: [{a[0]}, {a[1]}]
            b: [{b[0]}, {b[1]}]
            obstruction_toward_b_deg: 3.0
            notes: hello
        spots:
          - id: s_spot
            name: Spot
            kind: viewpoint
            at: [{s[0]}, {s[1]}]
            open_horizon: true
            sightline_ids: [f_axis, "auto:Teststraße"]
        """)
    f = load_featured(p, cfg)
    assert len(f.sightlines) == 1 and len(f.spots) == 1
    r = f.sightlines[0]
    assert r.id == "f_axis" and r.featured and r.kind == "axis" and r.notes == "hello"
    assert r.obstruction_toward_b_deg == 3.0 and r.obstruction_toward_a_deg is None
    assert f.spots[0].open_horizon and f.spots[0].sightline_ids == ["f_axis", "auto:Teststraße"]


def test_bad_ids_and_coordinates_are_rejected(tmp_path):
    with pytest.raises(ValueError):
        load_featured(write_yaml(tmp_path, "sightlines: [{id: x, name: n, a: [48.1, 11.5], b: [48.2, 11.5]}]"))
    with pytest.raises(ValueError):
        load_featured(write_yaml(tmp_path, "spots: [{id: x, name: n, at: [48.1, 11.5]}]"))
    with pytest.raises(ValueError):
        load_featured(write_yaml(tmp_path, "spots: [{id: s_x, name: n, at: [48.1]}]"))


def test_swapped_latlon_warns(tmp_path, cfg, caplog):
    with caplog.at_level(logging.WARNING):
        load_featured(write_yaml(tmp_path, "spots: [{id: s_x, name: n, at: [11.5, 48.1]}]"), cfg)
    assert "outside the bbox" in caplog.text


def test_empty_yaml_is_fine(tmp_path):
    f = load_featured(write_yaml(tmp_path, "# nothing yet\n"))
    assert f.sightlines == [] and f.spots == []


def test_validate_endpoints_warns_when_far_from_any_way(tmp_path, cfg):
    way = make_way([(0, 0), (1000, 0)])
    near = Run(id="f_near", name="n", kind="axis", a=ll(0, 5), b=ll(1000, -5), points=[xy(0, 5), xy(1000, -5)],
               osm_way_ids=[], featured=True)
    far = Run(id="f_far", name="f", kind="axis", a=ll(0, 5), b=ll(1000, 200), points=[xy(0, 5), xy(1000, 200)],
              osm_way_ids=[], featured=True)
    from pipeline.featured import Featured
    warns = validate_endpoints(Featured(sightlines=[near, far]), [way], cfg)
    assert len(warns) == 1 and "f_far" in warns[0] and "endpoint b" in warns[0]


def test_featured_overrides_auto_with_same_id(cfg):
    from pipeline.featured import Featured
    pts = [xy(0, 0), xy(500, 0)]
    auto = Run(id="f_x", name="auto", kind="street", a=ll(0, 0), b=ll(500, 0), points=pts, osm_way_ids=[1])
    other = Run(id="sl_other", name="o", kind="street", a=ll(0, 0), b=ll(500, 0), points=pts, osm_way_ids=[2])
    feat = Run(id="f_x", name="featured", kind="axis", a=ll(0, 0), b=ll(500, 0), points=pts, osm_way_ids=[],
               featured=True)
    merged = merge_featured_sightlines([auto, other], Featured(sightlines=[feat]))
    assert [r.name for r in merged] == ["o", "featured"]


def test_spot_resolves_auto_name_and_open_horizon(tmp_path, cfg):
    s = ll(20, 10)
    f = load_featured(write_yaml(tmp_path, f"""
        spots:
          - id: s_v
            name: V
            kind: viewpoint
            at: [{s[0]}, {s[1]}]
            open_horizon: true
            sightline_ids: ["auto:Prinzregentenstraße", "auto:Nope"]
        """), cfg)
    street = Run(name="Prinzregentenstraße", kind="street", a=ll(0, 0), b=ll(1200, 0),
                 points=[xy(0, 0), xy(1200, 0)], osm_way_ids=[1])
    far_same_name = Run(name="Prinzregentenstraße", kind="street", a=ll(0, 3000), b=ll(1200, 3000),
                        points=[xy(0, 3000), xy(1200, 3000)], osm_way_ids=[2])
    runs = [street, far_same_name, open_horizon_run(f.spots[0], cfg)]
    assign_sightline_ids(runs)
    oh = runs[2]
    assert oh.id == "sl_open_v" and oh.open_horizon and oh.a == oh.b == f.spots[0].at
    assert oh.obstruction_toward_a_deg == cfg.open_horizon_obstruction_deg
    pois = featured_pois(f, runs, cfg)
    assert len(pois) == 1
    p = pois[0]
    assert p.id == "s_v" and p.featured and p.open_horizon
    ids = {(v.sightline_id, v.toward) for v in p.views}
    assert (street.id, "b") in ids            # 20 m from end a -> looks toward b
    assert (oh.id, "b") in ids
    assert not any(v.sightline_id == far_same_name.id for v in p.views)
