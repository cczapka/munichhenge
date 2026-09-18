"""Bridge decks over rivers as sightlines along the river axis (PLAN.md 3.2).

For every candidate way tagged bridge=* that crosses a waterway=river centerline we walk
the river from the crossing point in both directions while it stays straight (with the
looser bridge_* tolerances) and emit one Run per direction: a = crossing point on the
bridge, b = far point on the river. Both directions of the run are still evaluated by the
engine; the interesting one is standing on the bridge (at a) looking at b.
"""
from __future__ import annotations

import logging
from typing import Sequence

from shapely.geometry import LineString, Point
from shapely.strtree import STRtree

from .config import PipelineConfig
from .geom import Pt, dist, is_straight, max_offset, project, unproject
from .models import Run, Way

log = logging.getLogger(__name__)


def _walk(points: list[Pt], start_idx: int, start_pt: Pt, direction: int, cfg: PipelineConfig) -> list[Pt]:
    """Walk from start_pt (on segment start_idx) along points in direction ±1 while straight."""
    chain = [start_pt]
    idx = start_idx + (1 if direction > 0 else 0)
    total = 0.0
    while 0 <= idx < len(points):
        seg = dist(chain[-1], points[idx])
        if seg == 0.0:              # crossing point sits exactly on a river node
            idx += direction
            continue
        cand = chain + [points[idx]]
        if total + seg > cfg.bridge_max_view_m or not is_straight(
                cand, cfg.bridge_river_tol_deg, cfg.bridge_river_offset_m, cfg.min_seg_for_bearing_m):
            break
        chain = cand
        total += seg
        idx += direction
    return chain


def bridge_runs(ways: Sequence[Way], cfg: PipelineConfig) -> list[Run]:
    rivers = [w for w in ways if w.kind == "river"]
    bridges = [w for w in ways if w.kind == "street" and w.bridge]
    if not rivers or not bridges:
        return []
    river_pts = [[project(c) for c in w.coords] for w in rivers]
    river_lines = [LineString(p) for p in river_pts if len(p) >= 2]
    tree = STRtree(river_lines)
    out: list[Run] = []
    for b in bridges:
        bpts = [project(c) for c in b.coords]
        if len(bpts) < 2:
            continue
        bline = LineString(bpts)
        for ri in tree.query(bline, predicate="intersects"):
            ri = int(ri)
            river = rivers[ri]
            rpts = river_pts[ri]
            inter = bline.intersection(river_lines[ri])
            if inter.is_empty:
                continue
            # first intersection point
            pt = inter if inter.geom_type == "Point" else list(inter.geoms)[0]
            if pt.geom_type != "Point":
                continue
            cross: Pt = (pt.x, pt.y)
            seg_idx = _segment_index(rpts, cross)
            for direction, label in ((1, "downstream"), (-1, "upstream")):
                chain = _walk(rpts, seg_idx, cross, direction, cfg)
                if len(chain) < 2 or dist(chain[0], chain[-1]) < cfg.min_length_m:
                    continue
                name = f"{b.name or 'Brücke'} ({river.name or 'river'} {label})"
                out.append(Run(name=name, kind="bridge", a=unproject(chain[0]), b=unproject(chain[-1]),
                               points=chain, osm_way_ids=sorted({b.id, river.id}),
                               max_offset_m=max_offset(chain, chain[0], chain[-1])))
    log.info("bridges: %d bridge ways x %d rivers -> %d runs", len(bridges), len(rivers), len(out))
    return out


def _segment_index(points: list[Pt], p: Pt) -> int:
    """Index i of the segment points[i]-points[i+1] closest to p."""
    best, best_d = 0, float("inf")
    for i in range(len(points) - 1):
        d = LineString([points[i], points[i + 1]]).distance(Point(p))
        if d < best_d:
            best, best_d = i, d
    return best
