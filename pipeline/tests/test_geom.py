import math

import pytest

from pipeline.geom import (
    along_fraction,
    angle_diff_deg,
    axis_diff_deg,
    bearing_bucket,
    grid_bearing,
    is_straight,
    max_offset,
    normalize_deg,
    perp_offset,
    project,
    true_bearings,
    unproject,
)
from tests.conftest import ORIGIN, xy


def test_normalize_and_diffs():
    assert normalize_deg(-10) == 350
    assert normalize_deg(370) == 10
    assert angle_diff_deg(350, 10) == 20
    assert angle_diff_deg(10, 350) == 20
    assert angle_diff_deg(0, 180) == 180
    assert axis_diff_deg(0, 180) == 0
    assert axis_diff_deg(89, 271) == 2
    assert axis_diff_deg(0, 90) == 90


def test_grid_bearing_cardinals():
    o = xy(0, 0)
    assert grid_bearing(o, xy(0, 100)) == pytest.approx(0)
    assert grid_bearing(o, xy(100, 0)) == pytest.approx(90)
    assert grid_bearing(o, xy(0, -100)) == pytest.approx(180)
    assert grid_bearing(o, xy(-100, 0)) == pytest.approx(270)
    assert grid_bearing(o, xy(100, 100)) == pytest.approx(45)


def test_project_roundtrip():
    p = project(ORIGIN)
    back = unproject(p)
    assert back[0] == pytest.approx(ORIGIN[0], abs=1e-9)
    assert back[1] == pytest.approx(ORIGIN[1], abs=1e-9)
    # UTM 32N coordinates for Munich are roughly x=690 km, y=5335 km
    assert 680_000 < p[0] < 700_000
    assert 5_330_000 < p[1] < 5_340_000


def test_true_bearing_of_a_parallel_is_east():
    """A line along a parallel (same latitude) points due east, whatever the grid says."""
    a = (48.15, 11.55)
    b = (48.15, 11.57)
    ab, ba, d = true_bearings(a, b)
    assert ab == pytest.approx(90.0, abs=0.05)
    assert ba == pytest.approx(270.0, abs=0.05)
    assert d == pytest.approx(1486, abs=5)   # 0.02° of longitude at 48.15° N


def test_grid_north_differs_from_true_north_in_munich():
    """Meridian convergence at lon 11.55 (central meridian 9°) is ~1.9°: grid north points
    ~1.9° west of true north, so a true-east line has grid bearing ~88.1°. Anything that
    ends up in the output must therefore use true_bearings, not grid_bearing."""
    a = (48.15, 11.55)
    b = (48.15, 11.57)
    grid = grid_bearing(project(a), project(b))
    true, _, _ = true_bearings(a, b)
    expected = (11.55 - 9.0) * math.sin(math.radians(48.15))
    assert true - grid == pytest.approx(expected, abs=0.1)
    assert 1.7 < true - grid < 2.1


def test_perp_offset_and_along_fraction():
    a, b = xy(0, 0), xy(100, 0)
    assert perp_offset(xy(50, 7), a, b) == pytest.approx(7)
    assert perp_offset(xy(150, -3), a, b) == pytest.approx(3)
    assert perp_offset(xy(5, 5), a, a) == pytest.approx(math.hypot(5, 5))
    assert along_fraction(xy(25, 9), a, b) == pytest.approx(0.25)
    assert along_fraction(xy(-10, 0), a, b) == pytest.approx(-0.1)
    assert max_offset([a, xy(30, 2), xy(60, -5), b], a, b) == pytest.approx(5)


def test_is_straight():
    straight = [xy(0, 0), xy(100, 0.5), xy(200, -0.5), xy(400, 0)]
    assert is_straight(straight, 2.0, 8.0, 5.0)
    bent = [xy(0, 0), xy(200, 0), xy(200, 200)]
    assert not is_straight(bent, 2.0, 8.0, 5.0)
    offset_too_big = [xy(0, 0), xy(200, 9), xy(400, 0)]   # each segment is only 2.6° off
    assert not is_straight(offset_too_big, 3.0, 8.0, 5.0)
    bearing_too_big = [xy(0, 0), xy(20, 3), xy(400, 0)]   # 3 m offset but the 20 m segment is 8.5° off
    assert not is_straight(bearing_too_big, 2.0, 8.0, 5.0)
    short_jog = [xy(0, 0), xy(200, 0), xy(202, 1), xy(400, 0)]   # 2 m jog exempt from the bearing test
    assert is_straight(short_jog, 2.0, 8.0, 5.0)
    assert not is_straight([xy(0, 0)], 2.0, 8.0, 5.0)
    assert not is_straight([xy(0, 0), xy(0, 0)], 2.0, 8.0, 5.0)


def test_bearing_bucket():
    assert bearing_bucket(99.7) == "090-120"
    assert bearing_bucket(279.7) == "090-120"
    assert bearing_bucket(0) == "000-030"
    assert bearing_bucket(179.9) == "150-180"
