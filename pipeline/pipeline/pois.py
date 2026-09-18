"""POI snapping (PLAN.md 3.4): attach every POI to the sightlines within poi_snap_m of the
chord, recording which endpoint it can look toward."""
from __future__ import annotations

import logging
from typing import Sequence

from shapely.geometry import LineString, Point
from shapely.strtree import STRtree

from .config import PipelineConfig
from .geom import Pt, along_fraction, dist, project
from .ids import poi_id
from .models import Poi, RawPoi, Run, View

log = logging.getLogger(__name__)


class SightlineIndex:
    def __init__(self, runs: Sequence[Run]):
        self.runs = [r for r in runs if not r.open_horizon]
        self.lines = [LineString([r.a_xy, r.b_xy]) for r in self.runs]
        self.tree = STRtree(self.lines) if self.lines else None

    def within(self, xy: Pt, radius_m: float) -> list[tuple[Run, float]]:
        """(run, distance to chord segment) for runs whose chord is within radius_m."""
        if self.tree is None:
            return []
        p = Point(xy)
        out = []
        for i in self.tree.query(p.buffer(radius_m), predicate="intersects"):
            i = int(i)
            d = self.lines[i].distance(p)
            if d <= radius_m:
                out.append((self.runs[i], d))
        return out


def views_for(xy: Pt, run: Run, distance_m: float, cfg: PipelineConfig,
              min_view_m: float | None = None) -> list[View]:
    """Views a viewer at xy has down ``run``. Looking toward an endpoint only counts when
    that endpoint is at least min_view_m away along the chord."""
    if min_view_m is None:
        min_view_m = cfg.poi_min_view_m
    L = dist(run.a_xy, run.b_xy)
    f = min(1.0, max(0.0, along_fraction(xy, run.a_xy, run.b_xy)))
    out: list[View] = []
    if (1.0 - f) * L >= min_view_m:
        out.append(View(sightline_id=run.id, toward="b", distance_m=round(distance_m, 1)))
    if f * L >= min_view_m:
        out.append(View(sightline_id=run.id, toward="a", distance_m=round(distance_m, 1)))
    return out


def snap_pois(raw: Sequence[RawPoi], runs: Sequence[Run], cfg: PipelineConfig) -> list[Poi]:
    """Only POIs with at least one view are returned (the app has no use for the rest)."""
    index = SightlineIndex(runs)
    out: list[Poi] = []
    for rp in raw:
        xy = project(rp.at)
        views: list[View] = []
        for run, d in index.within(xy, cfg.poi_snap_m):
            views.extend(views_for(xy, run, d, cfg))
        if not views:
            continue
        views.sort(key=lambda v: (v.distance_m, v.sightline_id, v.toward))
        out.append(Poi(id=poi_id(rp.osm_id), name=rp.name, kind=rp.kind, at=rp.at, osm_id=rp.osm_id,
                       open_horizon=False, views=views, tags=dict(rp.tags)))
    log.info("pois: %d raw -> %d attached to a sightline", len(raw), len(out))
    return out
