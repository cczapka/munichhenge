"""Stable ids for sightlines and POIs.

Sightline ids are content hashes (name + rounded endpoints) so re-running the pipeline on
fresher OSM data keeps ids for unchanged streets and data/*.json diffs stay readable.
"""
from __future__ import annotations

import hashlib

from .models import Run


def sightline_id(run: Run) -> str:
    key = f"{run.kind}|{run.name or ''}|{run.a[0]:.4f},{run.a[1]:.4f}|{run.b[0]:.4f},{run.b[1]:.4f}"
    return "sl_" + hashlib.sha1(key.encode("utf-8")).hexdigest()[:8]


def poi_id(osm_id: str) -> str:
    """"node/123" -> "poi_n123", "way/45" -> "poi_w45", "relation/6" -> "poi_r6"."""
    typ, num = osm_id.split("/", 1)
    return f"poi_{typ[0]}{num}"


def assign_sightline_ids(runs: list[Run]) -> None:
    """Featured runs keep their yaml id; others get a content hash, de-collided by suffix."""
    seen: set[str] = {r.id for r in runs if r.id}
    for r in runs:
        if r.id:
            continue
        base = sightline_id(r)
        cand, n = base, 1
        while cand in seen:
            n += 1
            cand = f"{base}{n}"
        r.id = cand
        seen.add(cand)
