"""CLI: python -m pipeline.run --pbf raw/oberbayern-latest.osm.pbf --out data [--debug-geojson data/debug]"""
from __future__ import annotations

import argparse
import dataclasses
import logging
import os
import sys
import time

from .bridges import bridge_runs
from .canyon import fill_canyon
from .clip import clip_extract
from .config import DEFAULT_CONFIG, PipelineConfig
from .extract import read_pbf
from .featured import (
    Featured,
    featured_pois,
    load_featured,
    merge_featured_sightlines,
    open_horizon_run,
    validate_endpoints,
)
from .ids import assign_sightline_ids
from .models import Extract, Poi, Run
from .obstruction import BuildingIndex, fill_obstructions
from .output import write_debug_geojson, write_json
from .pois import snap_pois
from .runs import dedupe_runs, extract_runs

log = logging.getLogger("pipeline")

MAX_TOTAL_BYTES = 2 * 1024 * 1024
"""PLAN.md M0: data/*.json must stay under 2 MB total. Enforced as a hard failure."""


def build(ex: Extract, featured: Featured, cfg: PipelineConfig) -> tuple[list[Run], list[Poi]]:
    """The whole pipeline after reading: pure function of the extract + featured + config."""
    t0 = time.time()
    validate_endpoints(featured, ex.ways, cfg)
    runs = extract_runs(ex.ways, cfg)
    runs.extend(bridge_runs(ex.ways, cfg))
    runs = dedupe_runs(runs, cfg)
    runs = merge_featured_sightlines(runs, featured)
    runs.extend(open_horizon_run(s, cfg) for s in featured.spots if s.open_horizon)
    assign_sightline_ids(runs)
    index = BuildingIndex(ex.buildings) if ex.buildings else None
    fill_obstructions(runs, index, cfg)
    fill_canyon(runs, index, cfg)
    pois = snap_pois(ex.pois, runs, cfg)
    pois.extend(featured_pois(featured, runs, cfg))
    log.info("build: %d sightlines, %d pois in %.1f s", len(runs), len(pois), time.time() - t0)
    return runs, pois


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--pbf", required=True, help="OSM extract (.osm.pbf)")
    ap.add_argument("--out", required=True, help="output directory for sightlines.json + pois.json")
    ap.add_argument("--featured", default=None, help="featured.yaml (default: <repo>/featured/featured.yaml)")
    ap.add_argument("--debug-geojson", default=None, metavar="DIR", help="also write GeoJSON for QGIS/geojson.io")
    ap.add_argument("--clip", choices=["admin", "bbox"], default="admin",
                    help="clip to the München admin relation (fallback bbox) or the bbox only")
    ap.add_argument("--min-length", type=float, default=None, help="override min_length_m")
    ap.add_argument("--merge-tol", type=float, default=None, help="override merge_tol_deg")
    ap.add_argument("--max-offset", type=float, default=None, help="override max_offset_m")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args(argv)

    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO,
                        format="%(asctime)s %(levelname)s %(name)s: %(message)s", datefmt="%H:%M:%S")

    overrides = {}
    if args.min_length is not None:
        overrides["min_length_m"] = args.min_length
    if args.merge_tol is not None:
        overrides["merge_tol_deg"] = args.merge_tol
    if args.max_offset is not None:
        overrides["max_offset_m"] = args.max_offset
    cfg = dataclasses.replace(DEFAULT_CONFIG, **overrides)

    featured_path = args.featured or os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                                                  "..", "featured", "featured.yaml")
    featured = load_featured(featured_path, cfg) if os.path.exists(featured_path) else Featured()
    if not os.path.exists(featured_path):
        log.warning("no featured.yaml at %s", featured_path)

    t0 = time.time()
    ex = read_pbf(args.pbf, cfg)
    log.info("read in %.0f s", time.time() - t0)
    ex = clip_extract(ex, cfg, mode=args.clip)
    runs, pois = build(ex, featured, cfg)
    s, p = write_json(runs, pois, args.out, cfg)
    if args.debug_geojson:
        write_debug_geojson(runs, pois, args.debug_geojson, cfg)
    total = s + p
    log.info("total JSON %.0f kB (limit %.0f kB)", total / 1024, MAX_TOTAL_BYTES / 1024)
    if total > MAX_TOTAL_BYTES:
        log.error("data/*.json exceed %d bytes; raise min_length_m or trim poi_tag_keys", MAX_TOTAL_BYTES)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
