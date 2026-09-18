# munichhenge — Plan

## 1. What the app does

Manhattanhenge works because Manhattan has one street grid, so the whole city aligns with
the setting sun on the same two evenings a year. Munich has streets at every heading, so
we flip the question:

> On any given day the sun sets (and rises) at a known azimuth. Which straight sightlines
> in Munich point at that azimuth, and which bars, restaurants and viewpoints sit on them?

Two query directions, both first-class:

1. **Date → where.** Pick a date, get the aligned sightlines and spots with times.
2. **Spot → when.** Pick a street or spot, get its next henge dates.

Scope for v1: sunset **and** sunrise. Munich only (but nothing is Munich-specific except
the data files and the default map center).

Useful numbers for Munich (48.14° N, 11.58° E):

- Sunset azimuth sweeps ≈ 233° (winter solstice) → 307° (summer solstice) and back.
- Sunrise azimuth sweeps ≈ 53° → 127° and back.
- So every sightline bearing in those ranges gets two events a year (once on the way out,
  once on the way back), except bearings near the extremes, which get a multi-day
  "season" around a solstice because the azimuth barely moves there.
- The azimuth shifts ~0.3–0.5°/day near the equinoxes, so a ±1.5° tolerance is a window
  of a few days, not one evening. Results carry a quality score, not a yes/no.

## 2. Physics and definitions

Vocabulary is fixed in `CLAUDE.md`. The key decisions:

**Effective horizon.** You almost never see the true horizon down a Munich street; there is
a building, tree line or hill at the far end. Each sightline direction therefore has an
*obstruction angle* — the apparent altitude of that blocker. Default estimate when we have
nothing better: `atan((H_building − h_eye) / length)` with `H_building = 20 m` (typical
5-storey Munich block), `h_eye = 1.7 m`. For a 400 m street that is ≈ 2.6°; for the
2 km Nymphenburg canal ≈ 0.5°. Use `building:levels × 3.2 m + 2 m` when OSM has it for
the buildings around the far endpoint. Terrain (DEM) is a later refinement; west of
Munich is flat.

**Henge moment.** Instead of "when does the sun's azimuth equal the bearing" (which gives
an arbitrary altitude), define the moment by altitude and measure the azimuth error:

- `t_full`: sun center altitude = obstruction + 0.2665° → whole disk rests on the line.
- `t_half`: sun center altitude = obstruction → half disk visible.
- Alignment error `e = |azimuth(t_full) − bearing|` (wrapped to ≤ 180°).
- Quality: perfect `e ≤ 0.5°`, good `≤ 1.5°`, near `≤ 3°`, else no event.
  Score `q = max(0, 1 − e / 3)` for sorting; optionally boost `canyon=true` sightlines.

For sunset the viewer stands at the *east* end looking west (bearing in 233–307°); for
sunrise at the *west* end looking east (53–127°). Both directions of every sightline are
evaluated; the relevant obstruction is the one at the far end.

**Sun position.** NOAA/Meeus algorithm (declination, equation of time, hour angle →
geometric altitude and azimuth), then apparent altitude with a standard refraction
formula (Sæmundsson: `R = 1.02 / tan(h + 10.3/(h + 5.11))` arcmin, valid to the horizon).
Accuracy target: azimuth within 0.1°, event times within 1 minute. That is plenty; the
obstruction estimate is far coarser.

**Solving for `t_full` / `t_half`.** For a given date and sightline, bracket the interval
[sunset − 3 h, sunset + 1 h] (or around sunrise), and root-find altitude(t) − target = 0
(bisection is fine; the function is monotonic in that window). Then evaluate azimuth.

## 3. Data pipeline (Python, `pipeline/`)

Runs once on your machine; the app ships the output. Re-run when you change parameters
or want fresher OSM data.

### 3.1 Inputs

- `raw/oberbayern-latest.osm.pbf` from Geofabrik (not committed; script downloads it).
- Clip to Munich: the `admin_level=6` relation for München, or fallback bbox
  `lat 48.06–48.25, lon 11.36–11.72`.
- `featured/featured.yaml` — hand-curated sightlines and spots (see 3.5).

### 3.2 Candidate sightlines

Take, with sensible tag filters:

- `highway` in {primary, secondary, tertiary, residential, unclassified, living_street,
  pedestrian, cycleway, footway (only if length ≥ 300 m), track (only in parks)}.
  Exclude motorway/trunk (you cannot stand there), service, steps.
- `waterway=canal|river` centerlines and `natural=water` long thin shapes (Nymphenburg
  canal, Isar, Eisbach). Rivers are not straight but bridges over them give open views —
  see next point.
- `man_made=bridge` / `bridge=yes` ways over the Isar: treat the bridge deck as a
  sightline in both directions along the *river* axis, with a low obstruction angle.
- Park axes: `leisure=park` internal paths are already covered by footway/track.

### 3.3 Straight-run extraction

1. Project everything to EPSG:25832.
2. For each way, walk its nodes and compute per-segment bearings.
3. Build chains: consecutive segments stay in the same *run* while the bearing differs
   from the run's chord bearing by < `MERGE_TOL_DEG` (default 2°) **and** the max
   perpendicular offset of any node from the chord stays < `MAX_OFFSET_M` (default 8 m).
   Merge across way boundaries when the next way shares the end node and the same
   `name` (OSM splits streets at every junction; a street is many ways).
4. Drop runs with chord length < `MIN_LENGTH_M` (default 400 m; expose as a config and
   tune by looking at the debug GeoJSON — 300 m may be right for canyon streets).
5. Deduplicate: parallel carriageways of one street (dual carriageways, tram lanes)
   collapse into one run if within 25 m and within 1° in bearing.
6. Per run compute: `bearing_ab`, `bearing_ba`, `length_m`, `max_offset_m`, `canyon`
   (buildings within 25 m on both sides for ≥ 60% of the length), and obstruction angle
   toward each end (from nearby `building:levels` if present, else default formula).
7. Add featured sightlines from `featured.yaml` (they override auto-detected ones with
   the same `id`).

### 3.4 POIs

Tags: `amenity` in {bar, pub, restaurant, cafe, biergarten, ice_cream},
`tourism=viewpoint`, plus featured spots. Attach a POI to every sightline within
`POI_SNAP_M` (default 40 m) of its centerline, recording which end the POI is nearer to
and therefore which direction(s) it can look down. A rooftop bar with an open horizon can
be marked `open_horizon: true` in `featured.yaml` — it then gets an all-azimuth virtual
sightline with obstruction 0.5°.

### 3.5 `featured/featured.yaml`

Hand-curated, because the best Munich sightlines are axes rather than roads. Each entry is
either a sightline (two endpoints) or a spot (point). Start with these candidates and
verify bearings from the map before trusting them:

```yaml
sightlines:
  - id: f_nymphenburg_canal
    name: Nymphenburg Kanal (Schloss ↔ Hubertusbrunnen)
    kind: canal
    a: [48.1584, 11.5182]   # verify
    b: [48.1584, 11.5416]   # verify
    obstruction_toward_a_deg: 1.0   # palace façade
    obstruction_toward_b_deg: 0.6
    notes: Classic axis. Sunset behind the palace when the bearing matches.
  - id: f_theresienwiese_bavaria
    name: Theresienwiese → Bavaria
    kind: axis
    a: [48.1316, 11.5540]
    b: [48.1313, 11.5473]
    obstruction_toward_b_deg: 3.0   # Bavaria statue + Ruhmeshalle on the terrace
spots:
  - id: s_olympiaberg
    name: Olympiaberg
    kind: viewpoint
    at: [48.1710, 11.5520]
    open_horizon: true
  - id: s_friedensengel
    name: Friedensengel terrace
    kind: viewpoint
    at: [48.1436, 11.5991]
    sightline_ids: [auto:Prinzregentenstraße]   # resolved by name after extraction
```

(Coordinates above are approximate placeholders — the pipeline must validate them
against OSM and warn if an endpoint is > 30 m from any matching way.)

### 3.6 Output schemas

`data/sightlines.json`

```json
{
  "version": 1,
  "generated": "2026-09-18",
  "params": {"min_length_m": 400, "merge_tol_deg": 2.0, "max_offset_m": 8,
             "default_building_h_m": 20, "eye_h_m": 1.7},
  "sightlines": [
    {
      "id": "sl_0001a3",
      "name": "Nymphenburger Straße",
      "kind": "street",
      "a": [48.1522, 11.5401],
      "b": [48.1500, 11.5601],
      "length_m": 1490,
      "max_offset_m": 4.2,
      "bearing_ab": 99.7,
      "bearing_ba": 279.7,
      "obstruction_toward_a_deg": 0.9,
      "obstruction_toward_b_deg": 1.1,
      "canyon": true,
      "featured": false,
      "notes": null,
      "osm_way_ids": [12345, 67890]
    }
  ]
}
```

`data/pois.json`

```json
{
  "version": 1,
  "pois": [
    {
      "id": "poi_00042",
      "name": "Café Example",
      "kind": "cafe",
      "at": [48.1510, 11.5505],
      "osm_id": "node/123456",
      "open_horizon": false,
      "views": [
        {"sightline_id": "sl_0001a3", "toward": "b", "distance_m": 12}
      ],
      "tags": {"opening_hours": "Mo-Su 09:00-23:00", "website": "https://..."}
    }
  ]
}
```

`views[].toward` is the endpoint the viewer looks *at*; the app uses `bearing_a?` and the
matching `obstruction_toward_?` accordingly.

### 3.7 Debug output

`--debug-geojson` writes `sightlines.geojson` (lines coloured by bearing bucket, with all
properties) and `pois.geojson`. Open in QGIS or geojson.io. **Do not proceed to M1 until
this looks like real Munich sightlines** — e.g. Nymphenburger Straße, Landsberger Straße,
Prinzregentenstraße, Maximilianstraße, the canal, the Isar bridges should all be present
with plausible lengths and no bent runs.

## 4. Android app (`app/`)

### 4.1 Modules

- `:core-solar` — pure Kotlin. `SunPosition`, `Refraction`, `SunEvents` (rise/set/transit),
  `AltitudeSolver` (root-find). No Android imports. Extensive unit tests.
- `:core-engine` — pure Kotlin. Data models (`Sightline`, `Poi`, `HengeEvent`), JSON
  loading (kotlinx.serialization), `HengeEngine` with:
  - `eventsFor(date: LocalDate, mode: Set<Mode>): List<HengeEvent>`
  - `nextEventsFor(sightlineId | poiId, from: LocalDate, count: Int): List<HengeEvent>`
  - `bestUpcoming(from: LocalDate, days: Int, minQuality: Double)`
  - in-memory cache keyed by date.
- `:app` — Compose UI, osmdroid map, Open-Meteo client, WorkManager notifications,
  DataStore settings.

### 4.2 `HengeEvent`

```kotlin
data class HengeEvent(
    val sightlineId: String,
    val viewFrom: Endpoint,          // A or B — where you stand
    val bearing: Double,             // azimuth you look toward
    val mode: Mode,                  // SUNRISE or SUNSET
    val date: LocalDate,             // local (Europe/Berlin) calendar date
    val tFull: Instant,
    val tHalf: Instant,
    val azimuthAtFull: Double,
    val alignmentErrorDeg: Double,
    val quality: Double,             // 0..1
    val grade: Grade,                // PERFECT, GOOD, NEAR
    val poiIds: List<String>,
)
```

### 4.3 Screens

1. **Today / Date** — date picker (defaults to today), sunrise/sunset toggle, list of
   events sorted by quality then time. Each row: name, `t_full` local time, grade badge,
   "stand at the [east] end, look [W]", spot count. Tap → detail.
2. **Map** — osmdroid, sightlines as lines (thickness by quality for the selected date),
   sun-direction arrow from the viewing end, POI markers. Tap a line → detail.
3. **Detail** — sightline or spot: next 5 events (either mode), attached spots with
   opening hours if known, cloud-cover hint for the next event, "add reminder".
4. **Upcoming** — best events in the next 14 days across the city, grouped by day.
5. **Settings** — min quality for notifications, notification lead time, and the engine
   tunables (tolerances, default obstruction) so you can tune without rebuilding.

### 4.4 Weather

Open-Meteo forecast (`cloud_cover`, `cloud_cover_low`, hourly, 7–16 days) for the event
hour at the sightline's viewing end. Show ☀ / ⛅ / ☁ with the percentage. Cache per day.
No API key needed. Fail silently (show nothing) when offline.

### 4.5 Notifications

Daily WorkManager job (~10:00 local): compute `bestUpcoming(tomorrow, 1 day)`; if any
event ≥ settings.minQuality (default 0.7) and cloud cover < 60% (or unknown), post one
notification listing the top 3. Tap → Date screen for that day.

## 5. Milestones

### M0 — Pipeline
Done when: `python -m pipeline.run` produces `data/sightlines.json` and `data/pois.json`
under 2 MB total; debug GeoJSON eyeballed and approved; `pytest` covers bearing math,
chord/offset computation, run merging on synthetic ways (straight, slightly bent, sharply
bent, split across ways), obstruction defaults, POI snapping.

### M1 — Solar core
Done when: `:core-solar:test` passes with checks against reference values for Munich —
sunrise/sunset times for the 2026 solstices and equinoxes within 2 min of an independent
source (timeanddate.com or a `pysolar`/`astral` computation done in the pipeline venv and
pasted into the test as fixtures), sun azimuth at those moments within 0.2°, refraction
at h=0 ≈ 0.57°, and the altitude solver converging in < 40 iterations.

### M2 — Engine
Done when: `:core-engine:test` passes with a synthetic dataset (a 270° street, a 300°
street, a 90° street, a bent one that should never match) and asserts that the 270°
sightline yields exactly two sunset events per year at the expected dates ± 2 days, that
grades follow the tolerance bands, and that `nextEventsFor` and `eventsFor` agree.
Also a smoke test loading the real `data/*.json` and computing a full year in < 2 s.

### M3 — UI
Done when: debug APK installs on your phone; Date, Map, Detail, Upcoming screens work
offline; a real upcoming event is shown that you can go and check in person.

### M4 — Weather + notifications + polish
Done when: cloud hint appears for events in the forecast range; the daily notification
fires; settings persist; release APK built with a signing key you own.

## 6. Kickoff prompt for Claude Code

Paste this in a fresh repo containing `CLAUDE.md`, `PLAN.md` and an empty
`featured/featured.yaml`:

> Read CLAUDE.md and PLAN.md fully. We are starting milestone M0 (the Python pipeline).
> First, restate M0 in your own words in ≤ 10 lines and list the tests you will write.
> Then scaffold `pipeline/` (pyproject or requirements.txt, `pipeline/run.py`,
> `pipeline/extract.py`, `pipeline/runs.py`, `pipeline/pois.py`, `pipeline/config.py`,
> `tests/`), write the tests for run merging on synthetic geometries, and implement until
> they pass. Add a `make download` step that fetches the Geofabrik Oberbayern extract into
> `raw/`. Do not start M1. When M0 is done, tell me how to run it and what to look at in
> the debug GeoJSON.

Subsequent milestones: "M0 is approved. Start M1 per PLAN.md; same procedure."

## 7. Open questions / later ideas

- DEM-based obstruction angles (Copernicus GLO-30) for hills and the Alps to the south.
- Moonhenge (same engine, lunar ephemeris) — fun, rare, cheap to add once M1 exists.
- Sharing: export an event as a calendar entry / link with map position.
- Other cities: only the data files and default map center change.
