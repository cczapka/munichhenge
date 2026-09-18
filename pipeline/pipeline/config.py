"""All tunables of the pipeline in one place.

Every number that trades something off lives here with a comment saying what it
trades off (CLAUDE.md rule 3). Nothing else in the pipeline may hard-code one.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass, field


@dataclass(frozen=True)
class PipelineConfig:
    # --- Geometry / straight-run extraction -------------------------------------------
    min_length_m: float = 400.0
    """Shortest chord that still counts as a sightline. Lower finds more canyon streets
    but also more junk; PLAN.md suggests tuning between 300 and 400 m from the GeoJSON."""

    max_offset_m: float = 8.0
    """Max perpendicular offset of any node from the run chord: the physical criterion for
    "you can see down the street" (the far end stays within a lane of the line of sight).
    Larger tolerates gentler curves and longer runs; 8 m is about one lane."""

    merge_tol_deg: float = 8.0
    """Max difference between a segment bearing and the run's chord bearing, tested only for
    segments >= min_seg_for_bearing_m. A coarse kink detector on top of max_offset_m, not
    the main criterion: on real Munich data nodes sit every 10-30 m and a 1 m survey
    error on a 20 m segment is already 3°, so PLAN.md's 2° cut Leopoldstraße into 765 m
    fragments where the offset test allows 1524 m (tests/test_real_streets.py). Tightening
    below ~6° starts cutting straight streets again; loosening further changes nothing
    because the offset test governs."""

    min_seg_for_bearing_m: float = 30.0
    """Segments shorter than this are exempt from the bearing test (offset test still
    applies). OSM junctions, crossings and lane splits produce 1-15 m segments whose bearing
    is noise; without this exemption they cut long runs in two. Kinks they could hide are
    still bounded by max_offset_m."""

    dedupe_dist_m: float = 25.0
    """Parallel runs closer than this (perpendicular distance) and within
    dedupe_bearing_deg collapse into one: dual carriageways, cycleways next to a street.
    Larger merges neighbouring parallel streets that are really separate sightlines."""

    dedupe_bearing_deg: float = 1.0
    """See dedupe_dist_m."""

    dedupe_min_overlap: float = 0.5
    """Fraction of the shorter run that must project onto the longer one for the two to
    count as duplicates. Prevents collapsing a run into a collinear one that is merely
    further down the same street."""

    footway_min_length_m: float = 300.0
    """highway=footway ways are only candidates from this length (PLAN.md 3.2). Many
    footways are 20 m stubs; this keeps the candidate set small."""

    # --- Obstruction angle --------------------------------------------------------------
    default_building_h_m: float = 20.0
    """Assumed height of whatever blocks the far end when OSM has no building:levels
    nearby: a typical 5-storey Munich block. Higher = later/higher henge moments."""

    eye_h_m: float = 1.7
    """Viewer eye height above street level."""

    m_per_level: float = 3.2
    """building:levels -> height: levels * m_per_level + roof_extra_m."""

    roof_extra_m: float = 2.0
    """Added on top of levels * m_per_level for the roof/attic."""

    obstruction_search_m: float = 60.0
    """Radius around a sightline endpoint in which buildings are consulted for the
    obstruction height. Too small finds nothing; too large mixes in the next block."""

    bridge_obstruction_deg: float = 0.5
    """Obstruction angle used for bridge-deck sightlines along a river: open water,
    only distant trees/bridges."""

    open_horizon_obstruction_deg: float = 0.5
    """Obstruction angle of the all-azimuth virtual sightline of an open_horizon spot."""

    # --- Canyon flag ----------------------------------------------------------------------
    canyon_flank_m: float = 25.0
    """A run is a canyon when buildings sit within this distance on BOTH sides for at least
    canyon_min_fraction of its length. 25 m ~ half a Munich Altbau street incl. sidewalks."""

    canyon_min_fraction: float = 0.6
    """See canyon_flank_m."""

    canyon_sample_m: float = 20.0
    """Sampling step along the chord for the canyon test. Smaller is slower and more exact."""

    # --- Bridges ----------------------------------------------------------------------------
    bridge_river_offset_m: float = 30.0
    """Straightness offset tolerance when walking a river centerline away from a bridge.
    Rivers are wide, so a much looser tolerance than max_offset_m still gives open views."""

    bridge_river_tol_deg: float = 4.0
    """Bearing tolerance for the same walk."""

    bridge_max_view_m: float = 2000.0
    """Stop walking the river after this distance; beyond it the view is haze anyway."""

    # --- POIs ---------------------------------------------------------------------------------
    poi_snap_m: float = 40.0
    """A POI attaches to every sightline whose chord is within this distance. 40 m covers
    the far side of a wide street plus a terrace; larger attaches POIs on parallel streets."""

    poi_min_view_m: float = 150.0
    """A POI only gets a view toward an endpoint that is at least this far away along the
    chord. Standing 20 m from the end of a street you look at a wall, not down a street."""

    # --- Featured ------------------------------------------------------------------------------
    featured_snap_warn_m: float = 30.0
    """Warn when a featured sightline endpoint is further than this from any candidate way
    (PLAN.md 3.5): the coordinates are probably wrong."""

    featured_resolve_m: float = 150.0
    """When a featured spot references auto:<name>, pick the nearest auto sightline with that
    name; warn if it is further than this."""

    # --- Clip -------------------------------------------------------------------------------------
    bbox: tuple[float, float, float, float] = (48.06, 11.36, 48.25, 11.72)
    """(min_lat, min_lon, max_lat, max_lon). Prefilter while reading the PBF and the fallback
    clip when the München admin_level=6 relation is not found."""

    admin_name: str = "München"
    admin_level: str = "6"

    # --- Output ---------------------------------------------------------------------------------------
    coord_decimals: int = 5
    """~1 m. Keeps data/*.json small and diffs stable."""

    highway_kinds: frozenset[str] = field(default_factory=lambda: frozenset({
        "primary", "secondary", "tertiary", "residential", "unclassified", "living_street",
        "pedestrian", "cycleway", "footway", "track",
    }))
    """highway=* values that are candidates. motorway/trunk/service/steps are excluded on
    purpose: you cannot stand there or they are not sightlines."""

    waterway_kinds: frozenset[str] = field(default_factory=lambda: frozenset({"canal", "river"}))

    poi_amenities: frozenset[str] = field(default_factory=lambda: frozenset({
        "bar", "pub", "restaurant", "cafe", "biergarten", "ice_cream",
    }))

    poi_tag_keys: tuple[str, ...] = ("opening_hours", "website", "phone", "cuisine", "outdoor_seating")
    """OSM tags copied into pois.json tags{} when present."""

    def params_for_json(self) -> dict:
        """The subset of tunables recorded in data/sightlines.json 'params'."""
        return {
            "min_length_m": self.min_length_m,
            "merge_tol_deg": self.merge_tol_deg,
            "max_offset_m": self.max_offset_m,
            "default_building_h_m": self.default_building_h_m,
            "eye_h_m": self.eye_h_m,
        }

    def as_dict(self) -> dict:
        d = asdict(self)
        for k, v in d.items():
            if isinstance(v, frozenset):
                d[k] = sorted(v)
        return d


DEFAULT_CONFIG = PipelineConfig()
