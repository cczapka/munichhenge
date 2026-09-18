"""Straight-run extraction (PLAN.md 3.3).

1. ``stitch_ways``   — join ways that share an end node and a name into chains, following
                       the straightest continuation at forks.
2. ``split_chain``   — greedily cut a chain into maximal straight runs (``geom.is_straight``).
3. ``dedupe_runs``   — collapse parallel carriageways / cycleways into one run.

Everything here works on projected metres and is independent of OSM I/O, so it can be
tested on synthetic polylines.
"""
from __future__ import annotations

import logging
from collections import defaultdict
from typing import Sequence

from shapely.geometry import LineString, Point, Polygon
from shapely.strtree import STRtree

from .config import PipelineConfig
from .geom import (
    Pt,
    along_fraction,
    angle_diff_deg,
    axis_diff_deg,
    dist,
    grid_bearing,
    is_straight,
    max_offset,
    perp_offset,
    polyline_length,
    project,
)
from .models import Chain, Run, Way

log = logging.getLogger(__name__)


# --------------------------------------------------------------------------- stitching

def _chain_from_way(w: Way) -> Chain:
    pts = [project(c) for c in w.coords]
    # drop zero-length segments (duplicate consecutive nodes)
    points, latlons, node_ids = [pts[0]], [w.coords[0]], [w.node_ids[0]]
    for p, ll, nid in zip(pts[1:], w.coords[1:], w.node_ids[1:]):
        if dist(p, points[-1]) > 0.0:
            points.append(p)
            latlons.append(ll)
            node_ids.append(nid)
    return Chain(points=points, latlons=latlons, node_ids=node_ids,
                 seg_way_ids=[w.id] * (len(points) - 1), name=w.name, kind=w.kind)


def _reverse(c: Chain) -> Chain:
    return Chain(points=c.points[::-1], latlons=c.latlons[::-1], node_ids=c.node_ids[::-1],
                 seg_way_ids=c.seg_way_ids[::-1], name=c.name, kind=c.kind)


def _join(first: Chain, second: Chain) -> Chain:
    """first.end must equal second.start."""
    assert first.node_ids[-1] == second.node_ids[0]
    return Chain(points=first.points + second.points[1:], latlons=first.latlons + second.latlons[1:],
                 node_ids=first.node_ids + second.node_ids[1:],
                 seg_way_ids=first.seg_way_ids + second.seg_way_ids, name=first.name, kind=first.kind)


def _junction_angle(a: Chain, a_end: str, b: Chain, b_end: str) -> float:
    """Turn angle (0 = dead straight) when leaving chain a at a_end into chain b at b_end."""
    if a_end == "end":
        into = grid_bearing(a.points[-2], a.points[-1])
    else:
        into = grid_bearing(a.points[1], a.points[0])
    if b_end == "start":
        out = grid_bearing(b.points[0], b.points[1])
    else:
        out = grid_bearing(b.points[-1], b.points[-2])
    return angle_diff_deg(into, out)


def stitch_ways(ways: Sequence[Way], cfg: PipelineConfig) -> list[Chain]:
    """Stitch ways with the same non-empty name that share an end node.

    OSM splits a street at every junction, so one street is many ways. At a node where
    more than two same-named ways meet, the straightest pair is joined and the others stay
    separate. Unnamed ways are never stitched (nothing says they belong together).
    """
    chains: dict[int, Chain] = {i: _chain_from_way(w) for i, w in enumerate(ways)
                                if len(w.coords) >= 2}
    chains = {i: c for i, c in chains.items() if len(c.points) >= 2}
    by_name: dict[tuple[str, str], list[int]] = defaultdict(list)
    for i, c in chains.items():
        if c.name:
            by_name[(c.name, c.kind)].append(i)

    for _, ids in by_name.items():
        if len(ids) < 2:
            continue
        _stitch_group(chains, ids)
    return list(chains.values())


def _stitch_group(chains: dict[int, Chain], ids: list[int]) -> None:
    alive = set(ids)
    changed = True
    while changed:
        changed = False
        ends: dict[int, list[tuple[int, str]]] = defaultdict(list)
        for i in alive:
            c = chains[i]
            ends[c.node_ids[0]].append((i, "start"))
            ends[c.node_ids[-1]].append((i, "end"))
        for node, members in ends.items():
            if len(members) < 2:
                continue
            best = None
            for x in range(len(members)):
                for y in range(x + 1, len(members)):
                    (i, ie), (j, je) = members[x], members[y]
                    if i == j:
                        continue  # closed loop; leave alone
                    ang = _junction_angle(chains[i], ie, chains[j], je)
                    if best is None or ang < best[0]:
                        best = (ang, i, ie, j, je)
            if best is None:
                continue
            _, i, ie, j, je = best
            a, b = chains[i], chains[j]
            if ie == "start":
                a = _reverse(a)
            if je == "end":
                b = _reverse(b)
            merged = _join(a, b)
            chains[i] = merged
            del chains[j]
            alive.discard(j)
            changed = True
            break  # indices changed; rebuild the endpoint table


# --------------------------------------------------------------------------- splitting

def split_points(points: Sequence[Pt], cfg: PipelineConfig) -> list[tuple[int, int]]:
    """Greedy maximal straight runs as (start_index, end_index) pairs over ``points``.

    Consecutive runs share their boundary node. A run of a single segment is always
    "straight", so every node index is covered.
    """
    n = len(points)
    runs: list[tuple[int, int]] = []
    i = 0
    while i < n - 1:
        best = i + 1
        j = i + 2
        while j < n and is_straight(points[i:j + 1], cfg.merge_tol_deg, cfg.max_offset_m,
                                    cfg.min_seg_for_bearing_m):
            best = j
            j += 1
        runs.append((i, best))
        i = best
    return runs


def split_chain(chain: Chain, cfg: PipelineConfig) -> list[Run]:
    out: list[Run] = []
    for i, j in split_points(chain.points, cfg):
        pts = chain.points[i:j + 1]
        way_ids = sorted(set(chain.seg_way_ids[i:j]))
        out.append(Run(name=chain.name, kind=chain.kind, a=chain.latlons[i], b=chain.latlons[j],
                       points=list(pts), osm_way_ids=way_ids,
                       max_offset_m=max_offset(pts, pts[0], pts[-1])))
    return out


def chord_length(run: Run) -> float:
    return dist(run.a_xy, run.b_xy)


def filter_candidate_ways(ways: Sequence[Way], cfg: PipelineConfig,
                          parks: Sequence[Sequence[Pt]] = ()) -> list[Way]:
    """Tag-level rules from PLAN.md 3.2 that need geometry: footways only from
    footway_min_length_m, tracks only inside a leisure=park polygon (forest lanes are not
    city sightlines)."""
    park_polys = [Polygon(r) for r in parks if len(r) >= 4]
    park_tree = STRtree(park_polys) if park_polys else None
    kept: list[Way] = []
    n_tracks_dropped = 0
    for w in ways:
        hw = w.tags.get("highway")
        if hw == "footway":
            if polyline_length([project(c) for c in w.coords]) < cfg.footway_min_length_m:
                continue
        elif hw == "track":
            mid = Point(project(w.coords[len(w.coords) // 2]))
            if park_tree is None or not any(park_polys[int(i)].contains(mid)
                                            for i in park_tree.query(mid, predicate="intersects")):
                n_tracks_dropped += 1
                continue
        kept.append(w)
    log.info("candidate ways: %d -> %d (%d tracks outside parks dropped)", len(ways), len(kept), n_tracks_dropped)
    return kept


def extract_runs(ways: Sequence[Way], cfg: PipelineConfig, parks: Sequence[Sequence[Pt]] = ()) -> list[Run]:
    """ways -> stitched chains -> straight runs >= min_length_m."""
    kept_ways = filter_candidate_ways(ways, cfg, parks)
    chains = stitch_ways(kept_ways, cfg)
    runs: list[Run] = []
    for c in chains:
        runs.extend(r for r in split_chain(c, cfg) if chord_length(r) >= cfg.min_length_m)
    log.info("%d ways -> %d chains -> %d runs >= %.0f m", len(kept_ways), len(chains), len(runs),
             cfg.min_length_m)
    return runs


# --------------------------------------------------------------------------- dedupe

_DEDUPE_FAMILY = {"street": "street", "canal": "water", "river": "water", "bridge": "bridge"}
"""Runs only collapse into runs of the same family: a bridge deck along the Isar is a
different vantage point from the river centerline it lies on, and a towpath is a different
sightline from its canal (the featured canal entry is the one that matters there)."""


def _is_duplicate(short: Run, long: Run, cfg: PipelineConfig) -> bool:
    if _DEDUPE_FAMILY.get(short.kind, short.kind) != _DEDUPE_FAMILY.get(long.kind, long.kind):
        return False
    if axis_diff_deg(grid_bearing(short.a_xy, short.b_xy), grid_bearing(long.a_xy, long.b_xy)) \
            >= cfg.dedupe_bearing_deg:
        return False
    la, lb = long.a_xy, long.b_xy
    if perp_offset(short.a_xy, la, lb) >= cfg.dedupe_dist_m or perp_offset(short.b_xy, la, lb) >= cfg.dedupe_dist_m:
        return False
    fa = along_fraction(short.a_xy, la, lb)
    fb = along_fraction(short.b_xy, la, lb)
    lo, hi = max(0.0, min(fa, fb)), min(1.0, max(fa, fb))
    overlap_m = max(0.0, hi - lo) * chord_length(long)
    return overlap_m >= cfg.dedupe_min_overlap * chord_length(short)


def dedupe_runs(runs: Sequence[Run], cfg: PipelineConfig) -> list[Run]:
    """Longest-first: a run is dropped when a kept, longer run is parallel (within
    dedupe_bearing_deg), within dedupe_dist_m and overlaps it along its length. Its way ids
    are folded into the kept run so nothing is lost for debugging. Featured runs come first
    whatever their length: they are never dropped, and an auto run duplicating one is
    dropped in its favour (the featured entry carries hand-set obstruction angles)."""
    ordered = sorted(runs, key=lambda r: (not r.featured, -chord_length(r), r.name or "", r.a, r.b))
    if not ordered:
        return []
    tree = STRtree([LineString([r.a_xy, r.b_xy]) for r in ordered])
    kept_idx: set[int] = set()
    dropped = 0
    for i, r in enumerate(ordered):
        dup_of = None
        if not r.featured:
            probe = LineString([r.a_xy, r.b_xy]).buffer(cfg.dedupe_dist_m)
            for j in sorted(int(x) for x in tree.query(probe, predicate="intersects")):
                if j in kept_idx and _is_duplicate(r, ordered[j], cfg):
                    dup_of = ordered[j]
                    break
        if dup_of is not None:
            dup_of.osm_way_ids = sorted(set(dup_of.osm_way_ids) | set(r.osm_way_ids))
            if dup_of.name is None and r.name:
                dup_of.name = r.name
            dropped += 1
            continue
        kept_idx.add(i)
    kept = [ordered[i] for i in sorted(kept_idx)]
    log.info("dedupe: %d runs -> %d (dropped %d parallel duplicates)", len(runs), len(kept), dropped)
    return kept
