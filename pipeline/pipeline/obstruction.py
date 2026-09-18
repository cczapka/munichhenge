"""Obstruction angle toward each end of a run (PLAN.md 2 / 3.3 step 6)."""
from __future__ import annotations

import math
import statistics
from typing import Sequence

from shapely.geometry import Point, Polygon
from shapely.strtree import STRtree

from .config import PipelineConfig
from .geom import Pt, dist
from .models import Building, Run


def default_obstruction_deg(length_m: float, cfg: PipelineConfig,
                            building_h_m: float | None = None) -> float:
    """atan((H - h_eye) / length). H defaults to cfg.default_building_h_m."""
    h = cfg.default_building_h_m if building_h_m is None else building_h_m
    if length_m <= 0.0:
        return 90.0
    return math.degrees(math.atan2(h - cfg.eye_h_m, length_m))


def building_height_m(b: Building, cfg: PipelineConfig) -> float | None:
    """Explicit height wins, else levels * m_per_level + roof_extra_m, else None."""
    if b.height_m is not None and b.height_m > 0:
        return b.height_m
    if b.levels is not None and b.levels > 0:
        return b.levels * cfg.m_per_level + cfg.roof_extra_m
    return None


class BuildingIndex:
    """Spatial index over building footprints (projected)."""

    def __init__(self, buildings: Sequence[Building]):
        self.buildings = list(buildings)
        self.polys = [Polygon(b.ring) for b in self.buildings]
        self.tree = STRtree(self.polys) if self.polys else None

    def near(self, pt: Pt, radius_m: float) -> list[int]:
        if self.tree is None:
            return []
        probe = Point(pt).buffer(radius_m)
        return [int(i) for i in self.tree.query(probe, predicate="intersects")]

    def height_near(self, pt: Pt, radius_m: float, cfg: PipelineConfig) -> float | None:
        """Median height of buildings with known height/levels within radius, else None.

        Median rather than max so one church tower does not dominate a block."""
        hs = [h for i in self.near(pt, radius_m)
              if (h := building_height_m(self.buildings[i], cfg)) is not None]
        return statistics.median(hs) if hs else None


def obstruction_toward(end_xy: Pt, far_xy: Pt, index: BuildingIndex | None, cfg: PipelineConfig) -> float:
    """Obstruction angle seen from far_xy looking at end_xy."""
    length = dist(end_xy, far_xy)
    h = index.height_near(end_xy, cfg.obstruction_search_m, cfg) if index is not None else None
    return default_obstruction_deg(length, cfg, h)


def fill_obstructions(runs: Sequence[Run], index: BuildingIndex | None, cfg: PipelineConfig) -> None:
    """Set obstruction_toward_a/b on every run that does not have them yet (featured values
    from featured.yaml are kept)."""
    for r in runs:
        if r.open_horizon:
            continue
        if r.kind == "bridge":
            if r.obstruction_toward_a_deg is None:
                r.obstruction_toward_a_deg = cfg.bridge_obstruction_deg
            if r.obstruction_toward_b_deg is None:
                r.obstruction_toward_b_deg = cfg.bridge_obstruction_deg
            continue
        if r.obstruction_toward_a_deg is None:
            r.obstruction_toward_a_deg = obstruction_toward(r.a_xy, r.b_xy, index, cfg)
        if r.obstruction_toward_b_deg is None:
            r.obstruction_toward_b_deg = obstruction_toward(r.b_xy, r.a_xy, index, cfg)
