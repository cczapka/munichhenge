# CLAUDE.md — munichhenge

Personal Android app that predicts "henge" moments in Munich: evenings (and mornings)
when the setting or rising sun lines up exactly with a straight street, canal or other
sightline, Manhattanhenge-style. Read `PLAN.md` for the full design and milestones.

## Repo layout

```
pipeline/   Python 3.11+. Turns an OSM extract into data/sightlines.json + data/pois.json.
app/        Android. Kotlin, Jetpack Compose, Gradle (Kotlin DSL), minSdk 26.
data/       Generated JSON consumed by the app (committed, < 2 MB). Never hand-edit.
featured/   Hand-curated sightlines and spots (featured.yaml). Edited by hand.
testdata/   Test vectors shared by the Python and Kotlin test suites.
docs/       Plans after PLAN.md: IMPROVEMENT-PLAN.md (M5–M11), PLAN-links-and-ui.md.
```

## Shared vocabulary (use these exact terms in code, comments and tests)

- **Sightline** — a straight run between endpoint A and endpoint B with a chord bearing.
  A viewer standing at A and looking toward B sees azimuth `bearing_ab`.
- **Bearing / azimuth** — degrees, 0–360, clockwise from true north. Always normalize.
- **Obstruction angle** — the apparent altitude (degrees above the horizon) of whatever
  blocks the view at the far end of a sightline (building, trees, terrain). The sun is
  visible down the sightline only while its altitude exceeds this angle.
- **Skyline profile** — per viewing direction, the obstruction angle as a function of the
  azimuth offset from the bearing, every 0.1° from −4° to +4° (positive = right). Measured
  (M8) or synthesised from length, obstruction angle and canyon flag (`ESTIMATED`).
- **Opening** — at a given altitude, the sky reaching left and right of the axis before a
  wall; sides with no wall at all are open road, capped at ±1.5°.
- **Event** — one sightline × one direction × one date × sunrise|sunset during which the
  sun's centre is above the skyline, inside the opening and at most 4° above the skyline.
  It has a best moment (`tBest`: centred, low, disc showing), a window around it, a style
  (`ON_HORIZON` within one sun diameter of the skyline, else `IN_CANYON`), a quality in
  [0, 1] for today's alignment, an interest in [0, 1] for the place, and a score
  (quality × interest blend) used for ranking. Definitions: docs/IMPROVEMENT-PLAN.md §3.
- **Offset** — sun azimuth minus bearing at the best moment, signed, degrees. (Until M5 the
  app used an unsigned "alignment error" at a fixed "henge moment"; both are gone.)
- **Spot** — a POI (bar, restaurant, café, biergarten, viewpoint, featured place) attached
  to one or more sightlines with a view direction.

## Conventions

- Angles in degrees everywhere except inside trig calls. Sun disk radius = 0.2665°.
- Altitudes are *apparent* (refraction-corrected) unless a name says `geometric`.
- Times are UTC instants (`java.time.Instant`) internally; convert to `Europe/Berlin`
  only in the UI layer.
- Coordinates: `(lat, lon)` in that order, WGS84. Pipeline does distance/bearing math in
  EPSG:25832 (UTM 32N); the app uses simple geodesic formulas (city scale is fine).
- Pure logic lives in `:core-solar` and `:core-engine` (plain Kotlin/JVM modules, no
  Android dependencies) so it can be unit-tested fast. `:app` is UI + glue only.
- The app never downloads OSM data or does geo processing. It loads `data/*.json`
  from assets. Only network calls: Open-Meteo (weather) and map tiles. Opening an
  external app with a link (Street View, directions, `geo:`) is allowed; the URLs are
  built by `Links` in `:core-engine` and `pipeline/pipeline/links.py`, which must agree
  with `testdata/links_vectors.json`.
- Tests must not touch the network.

## Commands

```
# pipeline
cd pipeline && python -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt
python -m pipeline.run --pbf ../raw/oberbayern-latest.osm.pbf --out ../data   # full build
python -m pipeline.run --pbf ... --out ../data --debug-geojson ../data/debug  # + GeoJSON for QGIS
pytest

# app
cd app && ./gradlew :core-solar:test :core-engine:test   # fast, pure JVM
./gradlew :app:assembleDebug                              # APK for sideloading
```

## Workflow rules

1. Work milestone by milestone as laid out in `PLAN.md` (M0 → M4). Do not start the next
   milestone until the current one's tests pass and its "done when" criteria are met.
2. Before writing code in a milestone, restate the plan for it in 5–10 lines and list the
   tests you will write. Then write tests, then code.
3. Any tunable (min length, tolerances, default building height, bearing merge threshold)
   goes into one config object with a comment saying what it trades off. No magic numbers.
4. Keep `data/*.json` small and stable: sort arrays by id so diffs are readable.
5. When unsure about a real-world fact (a street's bearing, a sunset time), write a test
   that checks it against an independently computed value rather than guessing.
6. Prefer boring, well-known libraries: osmium/pyrosm + shapely + pyproj in the pipeline;
   Compose + Material3 + kotlinx.serialization + osmdroid + WorkManager in the app.
7. Ask before adding a dependency that is not in that list.
