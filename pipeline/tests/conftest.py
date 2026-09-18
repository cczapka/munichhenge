"""Shared helpers: synthetic ways in projected metres around a Munich origin, and a tiny
synthetic PBF writer for the end-to-end test."""
from __future__ import annotations

import itertools
import os

import osmium
import pytest
from osmium.osm import mutable as m

from pipeline.config import PipelineConfig
from pipeline.geom import LatLon, Pt, project, unproject
from pipeline.models import Building, RawPoi, Way

ORIGIN: LatLon = (48.15, 11.55)
ORIGIN_XY: Pt = project(ORIGIN)

_way_ids = itertools.count(1000)
_node_ids = itertools.count(1)


def xy(x_m: float, y_m: float) -> Pt:
    """Projected point x_m east / y_m north of ORIGIN."""
    return (ORIGIN_XY[0] + x_m, ORIGIN_XY[1] + y_m)


def ll(x_m: float, y_m: float) -> LatLon:
    return unproject(xy(x_m, y_m))


def make_way(points_m: list[tuple[float, float]], name: str | None = "Teststraße", kind: str = "street",
             node_ids: list[int] | None = None, way_id: int | None = None, highway: str = "residential",
             bridge: bool = False) -> Way:
    coords = [ll(x, y) for x, y in points_m]
    if node_ids is None:
        node_ids = [next(_node_ids) for _ in coords]
    tags = {"highway": highway} if kind == "street" else {"waterway": kind}
    if bridge:
        tags["bridge"] = "yes"
    return Way(id=way_id if way_id is not None else next(_way_ids), name=name, kind=kind,
               node_ids=node_ids, coords=coords, tags=tags, bridge=bridge)


def make_building(x_m: float, y_m: float, w_m: float = 15.0, h_m: float = 15.0,
                  levels: float | None = None, height_m: float | None = None) -> Building:
    ring = [xy(x_m, y_m), xy(x_m + w_m, y_m), xy(x_m + w_m, y_m + h_m), xy(x_m, y_m + h_m), xy(x_m, y_m)]
    return Building(osm_id=f"way/{next(_way_ids)}", ring=ring, levels=levels, height_m=height_m)


def make_poi(x_m: float, y_m: float, name: str = "Café Test", kind: str = "cafe") -> RawPoi:
    return RawPoi(osm_id=f"node/{next(_node_ids)}", name=name, kind=kind, at=ll(x_m, y_m), tags={})


@pytest.fixture
def cfg() -> PipelineConfig:
    return PipelineConfig()


class PbfBuilder:
    """Collects nodes/ways/relations and writes a sorted .osm.pbf."""

    def __init__(self):
        self.nodes: list[m.Node] = []
        self.ways: list[m.Way] = []
        self.rels: list[m.Relation] = []
        self._nid = itertools.count(1)
        self._wid = itertools.count(1)
        self._rid = itertools.count(1)

    def node(self, latlon: LatLon, tags: dict | None = None) -> int:
        nid = next(self._nid)
        self.nodes.append(m.Node(id=nid, location=(latlon[1], latlon[0]), tags=tags or {}))
        return nid

    def way(self, latlons: list[LatLon], tags: dict, node_ids: list[int] | None = None) -> int:
        if node_ids is None:
            node_ids = [self.node(c) for c in latlons]
        wid = next(self._wid)
        self.ways.append(m.Way(id=wid, nodes=node_ids, tags=tags))
        return wid

    def polygon(self, ring: list[LatLon], tags: dict) -> int:
        ids = [self.node(c) for c in ring]
        return self.way(ring, tags, node_ids=ids + [ids[0]])

    def relation(self, members: list[tuple[str, int, str]], tags: dict) -> int:
        rid = next(self._rid)
        self.rels.append(m.Relation(id=rid, members=members, tags=tags))
        return rid

    def write(self, path: str) -> str:
        if os.path.exists(path):
            os.remove(path)
        w = osmium.SimpleWriter(path)
        for n in self.nodes:
            w.add_node(n)
        for way in self.ways:
            w.add_way(way)
        for r in self.rels:
            w.add_relation(r)
        w.close()
        return path
