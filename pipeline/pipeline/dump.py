"""Debug: dump everything the pipeline sees for a way name.

For each name writes <out_dir>/<slug>.geojson with three feature groups: the raw candidate
ways (with tags, node ids, way id), the stitched chains and the straight runs split from
them (all lengths, before the min_length filter and dedupe). Open it in geojson.io to see
where a street breaks."""
from __future__ import annotations

import json
import logging
import os
import re
from typing import Sequence

from .config import PipelineConfig
from .geom import grid_bearing, unproject
from .models import Extract
from .runs import chord_length, filter_candidate_ways, split_chain, stitch_ways

log = logging.getLogger(__name__)


def _slug(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_") or "unnamed"


def dump_names(names: Sequence[str], ex: Extract, cfg: PipelineConfig, out_dir: str) -> None:
    os.makedirs(out_dir, exist_ok=True)
    for name in names:
        ways = [w for w in ex.ways if w.name == name]
        feats = []
        for w in ways:
            feats.append({"type": "Feature",
                          "geometry": {"type": "LineString", "coordinates": [[c[1], c[0]] for c in w.coords]},
                          "properties": {"group": "way", "way_id": w.id, "tags": w.tags, "bridge": w.bridge,
                                         "node_ids": w.node_ids, "n_nodes": len(w.coords),
                                         "stroke": "#999999", "stroke-width": 6, "stroke-opacity": 0.4}})
        kept = filter_candidate_ways(ways, cfg, ex.parks)
        chains = stitch_ways(kept, cfg)
        chain_summary = []
        for ci, c in enumerate(chains):
            runs = split_chain(c, cfg)
            feats.append({"type": "Feature",
                          "geometry": {"type": "LineString", "coordinates": [[c[1], c[0]] for c in c.latlons]},
                          "properties": {"group": "chain", "chain": ci, "n_nodes": len(c.points),
                                         "way_ids": sorted(set(c.seg_way_ids)), "n_runs": len(runs),
                                         "stroke": "#1f78b4", "stroke-width": 3, "stroke-opacity": 0.8}})
            lens = []
            for ri, r in enumerate(runs):
                L = chord_length(r)
                lens.append(round(L))
                feats.append({"type": "Feature",
                              "geometry": {"type": "LineString",
                                           "coordinates": [[ll[1], ll[0]] for ll in (unproject(p) for p in r.points)]},
                              "properties": {"group": "run", "chain": ci, "run": ri, "length_m": round(L),
                                             "grid_bearing": round(grid_bearing(r.a_xy, r.b_xy), 1),
                                             "max_offset_m": round(r.max_offset_m, 1), "n_nodes": len(r.points),
                                             "kept": L >= cfg.min_length_m,
                                             "stroke": "#e41a1c" if L >= cfg.min_length_m else "#ff7f00",
                                             "stroke-width": 2}})
            chain_summary.append((len(c.points), sorted(lens, reverse=True)[:8]))
        path = os.path.join(out_dir, _slug(name) + ".geojson")
        with open(path, "w", encoding="utf-8") as f:
            json.dump({"type": "FeatureCollection", "features": feats}, f, ensure_ascii=False)
        log.info("dump %r: %d ways (%d after tag rules) -> %d chains; per chain (nodes, longest run chords): %s -> %s",
                 name, len(ways), len(kept), len(chains), chain_summary, path)
