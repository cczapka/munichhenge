"""Writers for data/sightlines.json, data/pois.json and the debug GeoJSON (PLAN.md 3.6/3.7)."""
from __future__ import annotations

import datetime as dt
import json
import logging
import os
from typing import Sequence

from .config import PipelineConfig
from .geom import bearing_bucket, true_bearings
from .models import Poi, Run

log = logging.getLogger(__name__)

SCHEMA_VERSION = 1


def _ll(ll, cfg: PipelineConfig) -> list[float]:
    return [round(ll[0], cfg.coord_decimals), round(ll[1], cfg.coord_decimals)]


def sightline_record(r: Run, cfg: PipelineConfig) -> dict:
    if r.open_horizon:
        bearing_ab = bearing_ba = None
        length = 0.0
    else:
        bearing_ab, bearing_ba, length = true_bearings(r.a, r.b)
        bearing_ab, bearing_ba = round(bearing_ab, 1), round(bearing_ba, 1)
    return {
        "id": r.id,
        "name": r.name,
        "kind": r.kind,
        "a": _ll(r.a, cfg),
        "b": _ll(r.b, cfg),
        "length_m": round(length),
        "max_offset_m": round(r.max_offset_m, 1),
        "bearing_ab": bearing_ab,
        "bearing_ba": bearing_ba,
        "obstruction_toward_a_deg": round(r.obstruction_toward_a_deg, 2),
        "obstruction_toward_b_deg": round(r.obstruction_toward_b_deg, 2),
        "canyon": bool(r.canyon),
        "featured": bool(r.featured),
        "notes": r.notes,
        "osm_way_ids": sorted(r.osm_way_ids),
    }


def poi_record(p: Poi, cfg: PipelineConfig) -> dict:
    rec = {
        "id": p.id,
        "name": p.name,
        "kind": p.kind,
        "at": _ll(p.at, cfg),
        "osm_id": p.osm_id,
        "open_horizon": bool(p.open_horizon),
        "views": [{"sightline_id": v.sightline_id, "toward": v.toward, "distance_m": v.distance_m}
                  for v in p.views],
        "tags": dict(sorted(p.tags.items())),
    }
    if p.featured:
        rec["featured"] = True
        rec["notes"] = p.notes
    return rec


def sightlines_doc(runs: Sequence[Run], cfg: PipelineConfig, generated: dt.date | None = None) -> dict:
    recs = sorted((sightline_record(r, cfg) for r in runs), key=lambda d: d["id"])
    return {
        "version": SCHEMA_VERSION,
        "generated": (generated or dt.date.today()).isoformat(),
        "params": cfg.params_for_json(),
        "sightlines": recs,
    }


def pois_doc(pois: Sequence[Poi], cfg: PipelineConfig) -> dict:
    return {"version": SCHEMA_VERSION, "pois": sorted((poi_record(p, cfg) for p in pois), key=lambda d: d["id"])}


def _dump(doc: dict, path: str, list_key: str | None = None) -> int:
    """Write JSON with one record per line for the array under ``list_key`` (records are
    sorted by id upstream, so git diffs show one changed sightline/POI per line)."""
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        if list_key is None:
            json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
        else:
            f.write("{\n")
            for k, v in doc.items():
                if k == list_key:
                    continue
                f.write(f"{json.dumps(k)}: {json.dumps(v, ensure_ascii=False)},\n")
            f.write(f"{json.dumps(list_key)}: [\n")
            recs = doc[list_key]
            for i, rec in enumerate(recs):
                f.write(json.dumps(rec, ensure_ascii=False, separators=(",", ":")))
                f.write(",\n" if i < len(recs) - 1 else "\n")
            f.write("]}")
        f.write("\n")
    size = os.path.getsize(path)
    log.info("wrote %s (%.0f kB)", path, size / 1024)
    return size


def write_json(runs: Sequence[Run], pois: Sequence[Poi], out_dir: str, cfg: PipelineConfig,
               generated: dt.date | None = None) -> tuple[int, int]:
    s = _dump(sightlines_doc(runs, cfg, generated), os.path.join(out_dir, "sightlines.json"), "sightlines")
    p = _dump(pois_doc(pois, cfg), os.path.join(out_dir, "pois.json"), "pois")
    return s, p


# --------------------------------------------------------------------------- debug GeoJSON

_BUCKET_COLOURS = {
    "000-030": "#e41a1c", "030-060": "#ff7f00", "060-090": "#ffd92f",
    "090-120": "#4daf4a", "120-150": "#377eb8", "150-180": "#984ea3",
}


def sightlines_geojson(runs: Sequence[Run], cfg: PipelineConfig) -> dict:
    feats = []
    for r in sorted(runs, key=lambda r: r.id or ""):
        rec = sightline_record(r, cfg)
        if r.open_horizon:
            geom = {"type": "Point", "coordinates": [rec["a"][1], rec["a"][0]]}
            colour = "#000000"
            bucket = None
        else:
            geom = {"type": "LineString", "coordinates": [[rec["a"][1], rec["a"][0]], [rec["b"][1], rec["b"][0]]]}
            bucket = bearing_bucket(rec["bearing_ab"])
            colour = _BUCKET_COLOURS.get(bucket, "#000000")
        props = dict(rec)
        props.update({"bearing_bucket": bucket, "stroke": colour, "stroke-width": 4 if r.featured else 2,
                      "stroke-opacity": 0.9})
        feats.append({"type": "Feature", "geometry": geom, "properties": props})
    return {"type": "FeatureCollection", "features": feats}


def pois_geojson(pois: Sequence[Poi], cfg: PipelineConfig) -> dict:
    feats = []
    for p in sorted(pois, key=lambda p: p.id):
        rec = poi_record(p, cfg)
        props = dict(rec)
        props["views"] = json.dumps(rec["views"], ensure_ascii=False)
        props["tags"] = json.dumps(rec["tags"], ensure_ascii=False)
        props["marker-color"] = "#000000" if p.featured else "#1f78b4"
        feats.append({"type": "Feature", "geometry": {"type": "Point", "coordinates": [rec["at"][1], rec["at"][0]]},
                      "properties": props})
    return {"type": "FeatureCollection", "features": feats}


def write_debug_geojson(runs: Sequence[Run], pois: Sequence[Poi], out_dir: str, cfg: PipelineConfig) -> None:
    _dump(sightlines_geojson(runs, cfg), os.path.join(out_dir, "sightlines.geojson"), "features")
    _dump(pois_geojson(pois, cfg), os.path.join(out_dir, "pois.geojson"), "features")
