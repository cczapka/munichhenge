"""featured/featured.yaml: hand-curated sightlines and spots (PLAN.md 3.5)."""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Sequence

import yaml
from shapely.geometry import LineString, Point
from shapely.strtree import STRtree

from .config import PipelineConfig
from .geom import LatLon, project
from .models import Poi, Run, View, Way
from .pois import SightlineIndex, views_for

log = logging.getLogger(__name__)


@dataclass
class FeaturedSpot:
    id: str
    name: str
    kind: str
    at: LatLon
    open_horizon: bool = False
    sightline_ids: list[str] = field(default_factory=list)
    notes: str | None = None
    tags: dict[str, str] = field(default_factory=dict)


@dataclass
class Featured:
    sightlines: list[Run] = field(default_factory=list)
    spots: list[FeaturedSpot] = field(default_factory=list)


def _latlon(v) -> LatLon:
    if not (isinstance(v, (list, tuple)) and len(v) == 2):
        raise ValueError(f"expected [lat, lon], got {v!r}")
    lat, lon = float(v[0]), float(v[1])
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        raise ValueError(f"coordinate out of range: {v!r}")
    return (lat, lon)


def load_featured(path, cfg: PipelineConfig | None = None) -> Featured:
    """Parse featured.yaml. With a cfg, warn about coordinates outside cfg.bbox (the usual
    symptom of swapped [lon, lat])."""
    with open(path, encoding="utf-8") as f:
        doc = yaml.safe_load(f) or {}
    out = Featured()

    def check(sid: str, ll: LatLon) -> LatLon:
        if cfg is not None:
            min_lat, min_lon, max_lat, max_lon = cfg.bbox
            if not (min_lat <= ll[0] <= max_lat and min_lon <= ll[1] <= max_lon):
                log.warning("%s: %s is outside the bbox %s — swapped [lat, lon]?", sid, ll, cfg.bbox)
        return ll

    for s in doc.get("sightlines") or []:
        sid = str(s["id"])
        if not sid.startswith("f_"):
            raise ValueError(f"featured sightline id must start with f_: {sid}")
        a, b = check(sid, _latlon(s["a"])), check(sid, _latlon(s["b"]))
        pa, pb = project(a), project(b)
        out.sightlines.append(Run(
            id=sid, name=str(s["name"]), kind=str(s.get("kind", "axis")), a=a, b=b,
            points=[pa, pb], osm_way_ids=[], max_offset_m=0.0, featured=True,
            notes=s.get("notes"),
            obstruction_toward_a_deg=_opt_float(s.get("obstruction_toward_a_deg")),
            obstruction_toward_b_deg=_opt_float(s.get("obstruction_toward_b_deg")),
        ))
    for s in doc.get("spots") or []:
        sid = str(s["id"])
        if not sid.startswith("s_"):
            raise ValueError(f"featured spot id must start with s_: {sid}")
        out.spots.append(FeaturedSpot(
            id=sid, name=str(s["name"]), kind=str(s.get("kind", "featured")), at=check(sid, _latlon(s["at"])),
            open_horizon=bool(s.get("open_horizon", False)),
            sightline_ids=[str(x) for x in (s.get("sightline_ids") or [])],
            notes=s.get("notes"), tags={k: str(v) for k, v in (s.get("tags") or {}).items()},
        ))
    return out


def _opt_float(v) -> float | None:
    return None if v is None else float(v)


def validate_endpoints(featured: Featured, ways: Sequence[Way], cfg: PipelineConfig) -> list[str]:
    """Warn (and return the messages) for endpoints > featured_snap_warn_m from any candidate way."""
    warnings: list[str] = []
    lines = [LineString([project(c) for c in w.coords]) for w in ways if len(w.coords) >= 2]
    tree = STRtree(lines) if lines else None
    for r in featured.sightlines:
        for label, ll in (("a", r.a), ("b", r.b)):
            p = Point(project(ll))
            d = float("inf")
            if tree is not None:
                idx = tree.nearest(p)
                if idx is not None:
                    d = lines[int(idx)].distance(p)
            if d > cfg.featured_snap_warn_m:
                msg = (f"featured sightline {r.id}: endpoint {label} {ll} is {d:.0f} m from the nearest "
                       f"candidate way (> {cfg.featured_snap_warn_m:.0f} m) — check the coordinates")
                warnings.append(msg)
                log.warning(msg)
    return warnings


def merge_featured_sightlines(auto: list[Run], featured: Featured) -> list[Run]:
    """Featured sightlines override auto-detected ones with the same id."""
    ids = {r.id for r in featured.sightlines}
    kept = [r for r in auto if r.id not in ids]
    return kept + list(featured.sightlines)


def open_horizon_run(spot: FeaturedSpot, cfg: PipelineConfig) -> Run:
    """The all-azimuth virtual sightline of an open_horizon spot: a == b, no bearing."""
    p = project(spot.at)
    return Run(id=f"sl_open_{spot.id[2:]}", name=spot.name, kind="open_horizon", a=spot.at, b=spot.at,
               points=[p, p], osm_way_ids=[], featured=True, notes=spot.notes, open_horizon=True,
               obstruction_toward_a_deg=cfg.open_horizon_obstruction_deg,
               obstruction_toward_b_deg=cfg.open_horizon_obstruction_deg)


def resolve_spot_sightlines(spot: FeaturedSpot, runs: Sequence[Run], cfg: PipelineConfig) -> list[Run]:
    """Resolve explicit ids and auto:<name> references to runs."""
    by_id = {r.id: r for r in runs}
    out: list[Run] = []
    xy = project(spot.at)
    for ref in spot.sightline_ids:
        if ref.startswith("auto:"):
            name = ref[5:]
            cands = [r for r in runs if r.name == name and not r.featured]
            if not cands:
                log.warning("spot %s: no auto sightline named %r", spot.id, name)
                continue
            best = min(cands, key=lambda r: LineString([r.a_xy, r.b_xy]).distance(Point(xy)))
            d = LineString([best.a_xy, best.b_xy]).distance(Point(xy))
            if d > cfg.featured_resolve_m:
                log.warning("spot %s: nearest %r sightline (%s) is %.0f m away", spot.id, name, best.id, d)
            out.append(best)
        elif ref in by_id:
            out.append(by_id[ref])
        else:
            log.warning("spot %s: unknown sightline id %r", spot.id, ref)
    return out


def featured_pois(featured: Featured, runs: Sequence[Run], cfg: PipelineConfig) -> list[Poi]:
    """Build Poi records for featured spots: explicit/auto: sightlines (no snap-distance
    rule), plus normal snapping, plus the open-horizon virtual sightline when flagged."""
    index = SightlineIndex(runs)
    out: list[Poi] = []
    for s in featured.spots:
        xy = project(s.at)
        views: dict[tuple[str, str], View] = {}
        for r in resolve_spot_sightlines(s, runs, cfg):
            d = LineString([r.a_xy, r.b_xy]).distance(Point(xy))
            # explicit references skip the poi_snap_m distance rule, not the view-length one:
            # a spot beyond an endpoint only looks back along the sightline
            for v in views_for(xy, r, d, cfg):
                views[(v.sightline_id, v.toward)] = v
        for r, d in index.within(xy, cfg.poi_snap_m):
            for v in views_for(xy, r, d, cfg):
                views.setdefault((v.sightline_id, v.toward), v)
        if s.open_horizon:
            oh = next((r for r in runs if r.open_horizon and r.id == f"sl_open_{s.id[2:]}"), None)
            if oh is not None:
                views[(oh.id, "b")] = View(sightline_id=oh.id, toward="b", distance_m=0.0)
        vlist = sorted(views.values(), key=lambda v: (v.distance_m, v.sightline_id, v.toward))
        if not vlist:
            log.warning("featured spot %s has no sightline; it will be written without views", s.id)
        out.append(Poi(id=s.id, name=s.name, kind=s.kind, at=s.at, osm_id=None, open_horizon=s.open_horizon,
                       views=vlist, tags=dict(s.tags), featured=True, notes=s.notes))
    return out
