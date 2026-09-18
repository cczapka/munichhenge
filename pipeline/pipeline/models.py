"""Data models passed between pipeline stages."""
from __future__ import annotations

from dataclasses import dataclass, field

from .geom import LatLon, Pt


@dataclass
class Way:
    """An OSM way that is a sightline candidate, with node coordinates resolved."""
    id: int
    name: str | None
    kind: str                    # "street" | "canal" | "river"
    node_ids: list[int]
    coords: list[LatLon]
    tags: dict[str, str] = field(default_factory=dict)
    bridge: bool = False


@dataclass
class Building:
    osm_id: str                  # "way/123" or "relation/123"
    ring: list[Pt]               # outer ring, projected
    levels: float | None = None
    height_m: float | None = None


@dataclass
class RawPoi:
    osm_id: str                  # "node/123" | "way/123" | "relation/123"
    name: str | None
    kind: str                    # amenity value or "viewpoint"
    at: LatLon
    tags: dict[str, str] = field(default_factory=dict)


@dataclass
class Extract:
    """Everything we take out of the PBF, still unprocessed."""
    ways: list[Way] = field(default_factory=list)
    buildings: list[Building] = field(default_factory=list)
    pois: list[RawPoi] = field(default_factory=list)
    admin_polygon: list[list[LatLon]] | None = None   # outer rings of the München relation
    parks: list[list[Pt]] = field(default_factory=list)   # leisure=park outer rings, projected


@dataclass
class Chain:
    """Several ways stitched end to end (same name), before straight-run splitting."""
    points: list[Pt]
    latlons: list[LatLon]
    node_ids: list[int]
    seg_way_ids: list[int]       # way id of segment i (points[i] -> points[i+1])
    name: str | None
    kind: str


@dataclass
class Run:
    """A straight run: the pipeline-side representation of a sightline before output."""
    name: str | None
    kind: str
    a: LatLon
    b: LatLon
    points: list[Pt]             # all nodes from a to b, projected
    osm_way_ids: list[int]
    max_offset_m: float = 0.0
    featured: bool = False
    notes: str | None = None
    id: str | None = None
    obstruction_toward_a_deg: float | None = None
    obstruction_toward_b_deg: float | None = None
    canyon: bool = False
    open_horizon: bool = False

    @property
    def a_xy(self) -> Pt:
        return self.points[0]

    @property
    def b_xy(self) -> Pt:
        return self.points[-1]


@dataclass
class View:
    sightline_id: str
    toward: str                  # "a" | "b"
    distance_m: float


@dataclass
class Poi:
    id: str
    name: str | None
    kind: str
    at: LatLon
    osm_id: str | None
    open_horizon: bool
    views: list[View]
    tags: dict[str, str]
    featured: bool = False
    notes: str | None = None
