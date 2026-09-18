"""Clip the extract to Munich: the admin polygon when found, else the bbox."""
from __future__ import annotations

import logging

from shapely.geometry import MultiPolygon, Point, Polygon
from shapely.prepared import prep

from .config import PipelineConfig
from .geom import project
from .models import Extract

log = logging.getLogger(__name__)


def clip_extract(ex: Extract, cfg: PipelineConfig, mode: str = "admin") -> Extract:
    """Keep ways with at least one node inside, POIs inside, buildings whose first vertex
    is inside. mode: "admin" (fallback to bbox when the relation is missing) or "bbox"."""
    if mode == "admin" and ex.admin_polygon:
        polys = [Polygon([project(p) for p in ring]) for ring in ex.admin_polygon]
        area = prep(MultiPolygon(polys) if len(polys) > 1 else polys[0])
        inside_xy = lambda xy: area.contains(Point(xy))  # noqa: E731
        inside = lambda latlon: inside_xy(project(latlon))  # noqa: E731
        log.info("clipping to admin polygon %s (%d rings)", cfg.admin_name, len(polys))
    else:
        if mode == "admin":
            log.warning("admin polygon for %s not found; falling back to bbox %s", cfg.admin_name, cfg.bbox)
        min_lat, min_lon, max_lat, max_lon = cfg.bbox
        inside = lambda ll: min_lat <= ll[0] <= max_lat and min_lon <= ll[1] <= max_lon  # noqa: E731
        inside_xy = None
    out = Extract(admin_polygon=ex.admin_polygon, parks=list(ex.parks))
    out.ways = [w for w in ex.ways if any(inside(c) for c in w.coords)]
    out.pois = [p for p in ex.pois if inside(p.at)]
    if inside_xy is not None:
        out.buildings = [b for b in ex.buildings if inside_xy(b.ring[0])]
    else:
        out.buildings = list(ex.buildings)  # already bbox-prefiltered on read
    log.info("after clip: %d ways, %d buildings, %d pois", len(out.ways), len(out.buildings), len(out.pois))
    return out
