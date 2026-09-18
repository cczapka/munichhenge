"""Canyon flag: buildings within canyon_flank_m on BOTH sides for >= canyon_min_fraction
of the chord (PLAN.md 3.3 step 6)."""
from __future__ import annotations

import math
from typing import Sequence

from shapely.geometry import Polygon

from .config import PipelineConfig
from .geom import dist
from .models import Run
from .obstruction import BuildingIndex


def canyon_fraction(run: Run, index: BuildingIndex, cfg: PipelineConfig) -> float:
    """Fraction of sample points along the chord that have a building within
    canyon_flank_m on the left AND on the right."""
    if index.tree is None:
        return 0.0
    a, b = run.a_xy, run.b_xy
    L = dist(a, b)
    if L == 0.0:
        return 0.0
    ux, uy = (b[0] - a[0]) / L, (b[1] - a[1]) / L   # along
    nx, ny = -uy, ux                                  # left normal
    step = cfg.canyon_sample_m
    n = max(1, int(math.ceil(L / step)))
    hits = 0
    for k in range(n):
        s0, s1 = k * step, min((k + 1) * step, L)
        both = True
        for sign in (1.0, -1.0):
            fx, fy = sign * nx * cfg.canyon_flank_m, sign * ny * cfg.canyon_flank_m
            p0 = (a[0] + ux * s0, a[1] + uy * s0)
            p1 = (a[0] + ux * s1, a[1] + uy * s1)
            box = Polygon([p0, p1, (p1[0] + fx, p1[1] + fy), (p0[0] + fx, p0[1] + fy)])
            if len(index.tree.query(box, predicate="intersects")) == 0:
                both = False
                break
        if both:
            hits += 1
    return hits / n


def fill_canyon(runs: Sequence[Run], index: BuildingIndex | None, cfg: PipelineConfig) -> None:
    for r in runs:
        if index is None or r.open_horizon or r.kind in ("bridge", "river", "canal"):
            r.canyon = False
            continue
        r.canyon = canyon_fraction(r, index, cfg) >= cfg.canyon_min_fraction
