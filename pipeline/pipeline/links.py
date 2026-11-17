"""External links that open Street View, walking directions or a map app at a sightline end.

Mirrors app/core-engine Links.kt; both are checked against testdata/links_vectors.json.
Rules are spelled out there. No network access happens here: these are only strings.
"""
from __future__ import annotations

import math

STREETVIEW_FOV_DEG = 60
"""Field of view of the Street View camera. A street's far end spans a degree or two;
Google's default of 90 makes it tiny, narrower than ~40 hides the flanking buildings."""

PITCH_MAX_DEG = 10
"""Upper clamp for the camera pitch. The pitch follows the obstruction angle so the camera
centres on the roofline where the sun will sit; above 10° that is no longer a street view."""

_UNRESERVED = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")


def _coord(lat: float, lon: float) -> str:
    return f"{lat:.6f},{lon:.6f}"


def heading(bearing_deg: float) -> int:
    """Whole degrees, rounded half up (not Python's half-to-even), 360 -> 0."""
    return int(math.floor(bearing_deg + 0.5)) % 360


def pitch(obstruction_deg: float) -> int:
    return min(PITCH_MAX_DEG, max(0, math.ceil(obstruction_deg)))


def _encode(label: str) -> str:
    return "".join(ch if ch in _UNRESERVED else "".join(f"%{b:02X}" for b in ch.encode("utf-8")) for ch in label)


def streetview_url(lat: float, lon: float, bearing_deg: float, obstruction_deg: float) -> str:
    return (f"https://www.google.com/maps/@?api=1&map_action=pano&viewpoint={_coord(lat, lon)}"
            f"&heading={heading(bearing_deg)}&pitch={pitch(obstruction_deg)}&fov={STREETVIEW_FOV_DEG}")


def walk_url(lat: float, lon: float) -> str:
    return f"https://www.google.com/maps/dir/?api=1&destination={_coord(lat, lon)}&travelmode=walking"


def geo_url(lat: float, lon: float, label: str) -> str:
    c = _coord(lat, lon)
    return f"geo:{c}?q={c}({_encode(label)})"
