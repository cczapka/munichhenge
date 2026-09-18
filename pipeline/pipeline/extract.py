"""Read an OSM PBF and pull out sightline candidate ways, buildings, POIs and the
München admin polygon, prefiltered to the config bbox.

Uses pyosmium's FileProcessor with C++-side tag filters so the tens of millions of
untagged nodes in the Oberbayern extract never reach Python.
"""
from __future__ import annotations

import logging
import os

import osmium
from osmium import filter as ofilter

from .config import PipelineConfig
from .geom import LatLon, project
from .models import Building, Extract, RawPoi, Way

log = logging.getLogger(__name__)


def _in_bbox(lat: float, lon: float, bbox: tuple[float, float, float, float]) -> bool:
    min_lat, min_lon, max_lat, max_lon = bbox
    return min_lat <= lat <= max_lat and min_lon <= lon <= max_lon


def _way_kind(tags, cfg: PipelineConfig) -> str | None:
    hw = tags.get("highway")
    if hw in cfg.highway_kinds:
        return "street"
    ww = tags.get("waterway")
    if ww in cfg.waterway_kinds:
        return ww
    return None


def _poi_kind(tags, cfg: PipelineConfig) -> str | None:
    am = tags.get("amenity")
    if am in cfg.poi_amenities:
        return am
    if tags.get("tourism") == "viewpoint":
        return "viewpoint"
    return None


def _parse_float(s: str | None) -> float | None:
    if s is None:
        return None
    s = s.strip().replace(",", ".")
    for suffix in (" m", "m"):
        if s.endswith(suffix):
            s = s[: -len(suffix)].strip()
    try:
        return float(s)
    except ValueError:
        return None


def _copy_tags(tags, keys) -> dict[str, str]:
    return {k: tags[k] for k in keys if k in tags}


def read_pbf(path: str | os.PathLike, cfg: PipelineConfig) -> Extract:
    """Single pass over the file (pyosmium does its own relation pre-pass for areas)."""
    ex = Extract()
    keys = ofilter.KeyFilter("highway", "waterway", "amenity", "tourism", "building", "boundary", "leisure")
    fp = (osmium.FileProcessor(path)
          .with_areas()
          .with_filter(ofilter.EmptyTagFilter())
          .with_filter(keys))
    n_seen = 0
    for obj in fp:
        n_seen += 1
        if obj.is_node():
            _take_poi_node(obj, ex, cfg)
        elif obj.is_way():
            _take_way(obj, ex, cfg)
        elif obj.is_area():
            _take_area(obj, ex, cfg)
    log.info("read %s: %d tagged objects, %d candidate ways, %d buildings, %d pois, %d parks, admin polygon %s",
             path, n_seen, len(ex.ways), len(ex.buildings), len(ex.pois), len(ex.parks),
             "found" if ex.admin_polygon else "NOT found")
    return ex


def _take_poi_node(obj, ex: Extract, cfg: PipelineConfig) -> None:
    kind = _poi_kind(obj.tags, cfg)
    if kind is None:
        return
    lat, lon = obj.location.lat, obj.location.lon
    if not _in_bbox(lat, lon, cfg.bbox):
        return
    ex.pois.append(RawPoi(osm_id=f"node/{obj.id}", name=obj.tags.get("name"), kind=kind,
                          at=(lat, lon), tags=_copy_tags(obj.tags, cfg.poi_tag_keys)))


def _take_way(obj, ex: Extract, cfg: PipelineConfig) -> None:
    kind = _way_kind(obj.tags, cfg)
    if kind is None:
        return
    if obj.tags.get("area") == "yes":
        return
    node_ids: list[int] = []
    coords: list[LatLon] = []
    inside = False
    for n in obj.nodes:
        if not n.location.valid():
            continue
        lat, lon = n.location.lat, n.location.lon
        node_ids.append(n.ref)
        coords.append((lat, lon))
        if not inside and _in_bbox(lat, lon, cfg.bbox):
            inside = True
    if not inside or len(coords) < 2:
        return
    bridge = obj.tags.get("bridge") not in (None, "no")
    ex.ways.append(Way(id=obj.id, name=obj.tags.get("name"), kind=kind, node_ids=node_ids,
                       coords=coords, bridge=bridge,
                       tags=_copy_tags(obj.tags, ("highway", "waterway", "bridge", "layer"))))


def _take_area(obj, ex: Extract, cfg: PipelineConfig) -> None:
    tags = obj.tags
    osm_type = "way" if obj.from_way() else "relation"
    osm_id = f"{osm_type}/{obj.orig_id()}"
    is_admin = (tags.get("boundary") == "administrative" and tags.get("admin_level") == cfg.admin_level
                and tags.get("name") == cfg.admin_name)
    is_park = tags.get("leisure") == "park"
    if not is_admin:
        if _poi_kind(tags, cfg) is None and tags.get("building") in (None, "no") and not is_park:
            return
        # cheap bbox prefilter on the first vertex before touching the rings
        first = next((n for ring in obj.outer_rings() for n in ring if n.location.valid()), None)
        if first is None or not _in_bbox(first.lat, first.lon, cfg.bbox):
            return
    rings: list[list[LatLon]] = []
    for ring in obj.outer_rings():
        rings.append([(n.lat, n.lon) for n in ring if n.location.valid()])
    rings = [r for r in rings if len(r) >= 4]
    if not rings:
        return
    if is_admin:
        ex.admin_polygon = rings
        return
    if is_park:
        for r in rings:
            ex.parks.append([project(pt) for pt in r])
    poi_kind = _poi_kind(tags, cfg)
    if poi_kind is not None:
        lat = sum(p[0] for p in rings[0]) / len(rings[0])
        lon = sum(p[1] for p in rings[0]) / len(rings[0])
        ex.pois.append(RawPoi(osm_id=osm_id, name=tags.get("name"), kind=poi_kind, at=(lat, lon),
                              tags=_copy_tags(tags, cfg.poi_tag_keys)))
    b = tags.get("building")
    if b is not None and b != "no":
        for r in rings:
            ex.buildings.append(Building(osm_id=osm_id, ring=[project(p) for p in r],
                                         levels=_parse_float(tags.get("building:levels")),
                                         height_m=_parse_float(tags.get("height"))))
