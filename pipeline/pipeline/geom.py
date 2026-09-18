"""Pure geometry helpers.

Projected math happens in EPSG:25832 (UTM 32N) metres; ``Pt`` is an ``(x, y)`` tuple there.
WGS84 coordinates are ``(lat, lon)`` tuples everywhere in this package (CLAUDE.md).

Bearings returned by ``grid_bearing`` are relative to *grid* north. In Munich grid north is
~1.9° west of true north (meridian convergence: (lon - 9°) * sin(lat)), which is far more
than our tolerances, so anything that ends up in the output must use ``true_bearing``
(geodesic, WGS84). Grid bearings are only used for *relative* comparisons between nearby
segments, where the convergence cancels out.
"""
from __future__ import annotations

import math
from typing import Iterable, Sequence

from pyproj import Geod, Transformer

Pt = tuple[float, float]
LatLon = tuple[float, float]

_TO_UTM = Transformer.from_crs("EPSG:4326", "EPSG:25832", always_xy=True)
_TO_WGS = Transformer.from_crs("EPSG:25832", "EPSG:4326", always_xy=True)
_GEOD = Geod(ellps="WGS84")


def normalize_deg(deg: float) -> float:
    """Wrap an angle into [0, 360)."""
    return deg % 360.0


def angle_diff_deg(a: float, b: float) -> float:
    """Smallest absolute difference between two bearings, in [0, 180]."""
    d = (a - b) % 360.0
    return min(d, 360.0 - d)


def axis_diff_deg(a: float, b: float) -> float:
    """Difference between two undirected axes (bearing mod 180), in [0, 90]."""
    d = (a - b) % 180.0
    return min(d, 180.0 - d)


def project(latlon: LatLon) -> Pt:
    lat, lon = latlon
    x, y = _TO_UTM.transform(lon, lat)
    return (x, y)


def unproject(pt: Pt) -> LatLon:
    lon, lat = _TO_WGS.transform(pt[0], pt[1])
    return (lat, lon)


def project_many(coords: Iterable[LatLon]) -> list[Pt]:
    return [project(c) for c in coords]


def dist(a: Pt, b: Pt) -> float:
    return math.hypot(b[0] - a[0], b[1] - a[1])


def grid_bearing(a: Pt, b: Pt) -> float:
    """Bearing from a to b in projected coordinates, degrees clockwise from grid north."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    return normalize_deg(math.degrees(math.atan2(dx, dy)))


def true_bearings(a: LatLon, b: LatLon) -> tuple[float, float, float]:
    """(bearing_ab, bearing_ba, distance_m) on the WGS84 ellipsoid, true north.

    bearing_ba is the azimuth seen by a viewer standing at b looking toward a.
    """
    az_ab, az_ba, d = _GEOD.inv(a[1], a[0], b[1], b[0])
    return normalize_deg(az_ab), normalize_deg(az_ba), d


def perp_offset(p: Pt, a: Pt, b: Pt) -> float:
    """Perpendicular distance of p from the infinite line through a and b.

    Returns dist(p, a) when a == b.
    """
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    L = math.hypot(dx, dy)
    if L == 0.0:
        return dist(p, a)
    return abs(dx * (p[1] - ay) - dy * (p[0] - ax)) / L


def along_fraction(p: Pt, a: Pt, b: Pt) -> float:
    """Position of p's projection on the line a->b as a fraction (0 at a, 1 at b)."""
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    L2 = dx * dx + dy * dy
    if L2 == 0.0:
        return 0.0
    return ((p[0] - ax) * dx + (p[1] - ay) * dy) / L2


def max_offset(points: Sequence[Pt], a: Pt, b: Pt) -> float:
    """Largest perpendicular offset of any point from the chord a-b."""
    return max((perp_offset(p, a, b) for p in points), default=0.0)


def polyline_length(points: Sequence[Pt]) -> float:
    return sum(dist(points[i], points[i + 1]) for i in range(len(points) - 1))


def interpolate(a: Pt, b: Pt, t: float) -> Pt:
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def is_straight(points: Sequence[Pt], tol_deg: float, max_offset_m: float,
                min_seg_for_bearing_m: float) -> bool:
    """The straightness criterion shared by run extraction and merging.

    A polyline is straight when every node lies within ``max_offset_m`` of the chord and
    every segment at least ``min_seg_for_bearing_m`` long has a bearing within ``tol_deg``
    of the chord bearing.
    """
    if len(points) < 2:
        return False
    a, b = points[0], points[-1]
    if dist(a, b) == 0.0:
        return False
    chord = grid_bearing(a, b)
    for i in range(len(points) - 1):
        p, q = points[i], points[i + 1]
        if perp_offset(q, a, b) >= max_offset_m or perp_offset(p, a, b) >= max_offset_m:
            return False
        if dist(p, q) >= min_seg_for_bearing_m and angle_diff_deg(grid_bearing(p, q), chord) >= tol_deg:
            return False
    return True


def bearing_bucket(bearing: float, width: int = 30) -> str:
    """Label like "090-120" used to colour debug GeoJSON by axis (bearing mod 180)."""
    b = int(bearing % 180.0) // width * width
    return f"{b:03d}-{b + width:03d}"
