# munichhenge — Implementation plan for the next version (M5–M11)

Status: **proposal, awaiting approval**. Nothing here is implemented. Continues `PLAN.md`
(M0–M4, all built) and follows its conventions: milestone by milestone, tests before code,
every tunable in one config object, vocabulary from `CLAUDE.md`.

Companion document: `docs/PLAN-links-and-ui.md` holds the screen-level design for M5, M7
and M9. This file is the master plan: why, in what order, how each part works, how it is
tested, and what has to be decided.

---

## 0. Where we stand

### 0.1 What the review of 2026-11-16 measured

| Check | Result |
|---|---|
| Astronomy: 350 events over 7 days recomputed with an independent library | 349 match; times within 30 s, alignment within 0.01° |
| Canyon streets, sun inside the street's opening at the shown moment (20 m width assumed) | perfect 37/37, good 69/78, near 6/96 |
| Non-perfect events where the sun is never centred above the roofline | 124 of 293 |
| Obstruction angle based on an assumed 20 m building | 23 % of viewing directions |
| Ranking | alignment only; top entry of a typical day is an unnamed 423 m path |
| Spots open at the event time (hours known) | sunset 64/86, sunrise 8/153 |
| Spots more than 20 m from the axis | 42 % |
| Data age | OSM extract of 2026-09-18, no scheduled rebuild |
| Upcoming tab | about 700 rows, no filter |

### 0.2 The three root causes

1. **The event definition ignores the street.** An event is "sun centre at obstruction +
   one radius, azimuth within a fixed 0.5°/1.5°/3° of the bearing". Real visibility depends
   on the street's opening: its width, length and the roofline on both sides. A typical
   event street is about 1° wide at its far end, so most "near" events are behind a wall,
   and half of the off-axis events are shown at a moment when the sun is not in the street.
2. **The skyline is guessed.** One obstruction angle per direction, from buildings near the
   far end or a flat 20 m assumption; no trees, no terrain, no view of what actually stands
   on the line of sight.
3. **The app lists rather than recommends.** No ranking by how good the place is, no way to
   check a forecast before going, no search, noisy lists.

### 0.3 What became possible

The Bavarian surveying office publishes, free, without account, under CC BY 4.0 (verified
by download from a GitHub runner on 2026-11-16):

| Dataset | Content | Munich |
|---|---|---|
| LoD2 building models | every building with roof geometry and measured height, CityGML, 2 km tiles, EPSG:25832 | 109 tiles, 6.5 GB |
| DGM5 terrain | ground elevation, 5 m grid, zipped text | 374 tiles, 0.1 GB |
| DGM1 terrain | 1 m grid, GeoTIFF | 374 tiles, 1.0 GB |
| Einzelbäume | single trees with height above ground, GeoPackage | lot file, 0.2–1 GB (lot covering Munich to be located) |
| DOM20 surface model | 20 cm grid incl. vegetation | up to 24 GB per municipality; Munich coverage unconfirmed; **not used in this plan** |

Sample tile 690_5334: 5,499 buildings, all with a height; median 18.9 m, maximum 98.6 m.

---

## 1. Target and order

**Target:** every event the app shows is one where the sun is really visible down the
sightline at the stated time, the list leads with places worth going to, and each forecast
can be checked in one tap before leaving the house.

| # | Milestone | Fixes | Size | Depends on |
|---|---|---|---|---|
| M5 | Take me there: Street View and navigation links | no way to check or get there | S | — |
| M6 | Visibility engine v2 and ranking | root causes 1 and 3 | L | — |
| M7 | UI part 1: structure, filter, event card, Day, Upcoming | noise, raw ids | M | M6 for final values |
| M8 | Skyline from Bavarian open data | root cause 2 | L | M6 |
| M9 | UI part 2: detail and season strip, Places, Map, reminders, polish | search, map speed, late reminders | L | M5, M7 |
| M10 | Spots worth showing | closed and far-off spots | M | M8 optional |
| M11 | Operations: scheduled rebuild, forecast check in CI, releases | stale data, expiring APK links | S | — |

Why this order: M5 is small and gives you the verification tool first. M6 changes what an
event *is*, so it comes before the UI is rebuilt around events; it already works on
approximate street shapes derived from today's data. M8 then replaces the approximation
with measured skylines without touching the app code. M11's pieces can be pulled forward
at any time.

Each milestone ends with: tests green in CI, a new APK, the debug map updated where data
changed, and a short list of things for you to check on the phone or the street.

---

## 2. M5 — Take me there

Specified in `docs/PLAN-links-and-ui.md` section 1. Summary: a pure URL builder for Street
View along the bearing, walking directions and `geo:` links; buttons on the detail screens;
the same links as properties in the debug GeoJSON and in a `check` command of the engine
CLI. No dependency, no key, no permission.

Done when the three buttons work on your phone and the GitHub debug map shows working
Street View links for every sightline direction.

---

## 3. M6 — Visibility engine v2 and ranking

### 3.1 Concept: the skyline profile

For one viewing direction (standing point S, bearing β) a **skyline profile** is the
apparent altitude of the highest obstruction as a function of the azimuth offset δ from the
bearing:

- δ from −4.0° to +4.0° in steps of 0.1° (81 samples), positive = clockwise = to the right.
- value = degrees above the horizontal at eye height, stored as one byte in units of 0.05°
  (0–12.75°).
- two layers: **hard** (buildings and terrain) and **soft** (trees), see 3.6.

The range ±4° covers the sun's sideways travel while it is low: at Munich it moves about
1.1° in azimuth per 1° of altitude near sunset, and events higher than about 4° above the
far end are not interesting.

In M6 the profile is **synthesised** from the data we already have; in M8 the pipeline
measures it. The engine does not know the difference.

Synthetic profile (`SkylineProfile.synthetic`), from schema-v1 fields:

- floor `f` = the direction's obstruction angle;
- half-width of the far end `e0 = atan((W/2) / length)`, `W = assumedStreetWidthM`
  (config, default 20 m);
- canyon sightlines: `skyline(δ) = f` for `|δ| ≤ e0`, rising linearly as `f·|δ|/e0` outside
  (the roofline of a flanking wall seen in perspective);
- non-canyon sightlines: `f` everywhere (open sides);
- open-horizon spots: `f` everywhere, any azimuth.

### 3.2 New event definition

For a direction, a date and a mode, the engine follows the sun through the low part of its
path and asks, sample by sample:

| Term | Definition |
|---|---|
| visible | sun centre above the hard skyline at its offset: `alt > skyline(δ)` |
| in the opening | no skyline sample between the axis (δ = 0) and the sun's offset is higher than the sun: the sun is in the same patch of sky as the end of the street |
| opening at altitude a | `[δL(a), δR(a)]`: how far the sky extends left and right of the axis at that altitude; a side with no wall within ±4° is "open" and capped at `openSideGateDeg` (1.5°) |
| centring r | `|δ − mid| / half` of the opening at the sun's altitude, 0 = centred, 1 = touching a wall |
| height above floor g | `alt − skyline(δ)` |
| disc fraction | share of five points on the disc (centre and four limbs) that are above the skyline |

An **event** exists when at least one sample is visible and in the opening with
`g ≤ maxHeightAboveFloorDeg` (4°). Its fields:

- `window`: the contiguous time span, around the best sample, in which the sun is visible
  and in the opening; edges refined to 1 s by bisection.
- `tBest`: the sample with the highest moment score
  `(1 − r) · lowness(g) · discFraction`, where `lowness` is 1 up to one sun diameter above
  the floor and falls linearly to 0 at 4°.
- `style`: `ON_HORIZON` when at `tBest` the disc sits within one diameter of the floor
  (the classic henge picture), otherwise `IN_CANYON` (sun centred between the walls, higher up).
- `offsetDeg` (signed), `heightAboveFloorDeg`, `sunAzimuth`, `sunAltitude`, `discFraction`.
- `lateralOffsetM = length · tan(offsetDeg)`: how far left or right of the centre line to
  stand so the sun sits over the middle of the far end, reported only when it fits inside
  the street ("stand on the south pavement").
- `treesInWay`: the soft layer is above the sun at `tBest` (3.6).
- `confidence`: `MEASURED` (profile from M8, complete), `PARTIAL` (wedge leaves the data
  area), `ESTIMATED` (synthetic).
- `quality` in [0, 1] = the moment score at `tBest`.
- `grade` (thresholds in `EngineConfig`, to be calibrated on the fixtures in 3.5):
  `PERFECT` = full disc, `r ≤ 0.35`, `ON_HORIZON`; `GOOD` = at least half the disc,
  `r ≤ 0.7`, `g ≤ 1.5°`; `NEAR` = anything else that qualifies as an event.

What this fixes: events on the wrong side of the axis disappear; events where the sun is
centred earlier and higher get the right time and are labelled `IN_CANYON`; tolerance
scales with the real opening instead of fixed degrees.

`tFull`, `tHalf`, `azimuthAtFull` and `alignmentErrorDeg` are removed from `HengeEvent`.
17 call sites in `:app` change (rows, digest, reminders, CLI).

### 3.3 Algorithm and cost

Per date (cached as today):

1. Sun path at the city reference point, once: azimuth range while the altitude is between
   −0.5° and `maxScanAltitudeDeg` (10°).
2. Prefilter: keep directions whose bearing lies within that azimuth range ± 4°. About 5 %
   of the 2,730 directions survive.
3. For each survivor: sample the sun at its own standing point every `scanStepSeconds`
   (15 s) while it is in the altitude band, about 200–300 samples; evaluate the table in
   3.2; refine window edges.

Expected cost about 30,000 sun positions per day for the whole city. `PLAN.md` M2 required
a full year over the real data in under 2 s; v2 does more work per event, so the criterion
becomes: **one day under 50 ms warm, 14 days under 1 s, one sightline for a year under
300 ms, full year under 6 s.** If the full year misses, the fallback is a 60 s coarse scan
with refinement around the best sample.

### 3.4 Ranking: quality × interest

`quality` says how good today's alignment is. **Interest** says how good the place is and
does not change from day to day:

`interest = wFeatured·featured + wLength·norm(length, 400 m…1500 m) + wFramed·framed + wSpots·norm(openSpots, 0…5) + wNamed·named`

- `framed` = how canyon-like the opening is, from the profile: wall height at ±1.5° relative
  to the floor, clamped to 0–1 (replaces the boolean `canyon` for ranking);
- weights live in `EngineConfig` (defaults 0.30 / 0.25 / 0.20 / 0.15 / 0.10);
- `score = quality · (interestFloor + (1 − interestFloor) · interest)`, `interestFloor` 0.4
  so a superb alignment on a plain street still shows.

`eventsFor` and `bestUpcoming` sort by `score`; both values stay on the event so the UI can
offer "best alignment" and "best place" sorts. The daily digest uses `score`.

### 3.5 Tests (written first)

`:core-engine`, synthetic profiles unless noted:

1. Profile: decode/encode round trip; interpolation; `openingAt(a)` on a V-shaped profile
   equals the closed form; blocked axis gives no opening.
2. Canyon street, sun reaching the floor exactly on the axis: event `PERFECT`,
   `ON_HORIZON`, window ends within 20 s of the old `tHalf`.
3. Same street, sun 1.5° to the far side when it reaches the floor (already past the axis):
   event exists, `IN_CANYON`, `tBest` earlier than the old time by the expected minutes,
   `offsetDeg` near 0 at `tBest`.
4. Same street, sun 1.5° to the near side (never crosses the axis above the floor): no event.
5. Open road (flat profile): event only while `|δ| ≤ openSideGateDeg`; `ON_HORIZON`.
6. Sunrise mirrors 2–4 with the sides swapped.
7. `lateralOffsetM`: sign and size; omitted when larger than half the street width.
8. Soft layer above the sun: `treesInWay` true; leaf-off months per 3.6.
9. Regression: the 270° street still has exactly two sunset seasons with best days within
   ±2 days of the equinoxes; the 200° street never has an event.
10. Ranking: featured long framed street outranks an unnamed 420 m path at equal quality;
    equal interest sorts by quality; weights respected.
11. Schema: v1 data loads and gets synthetic profiles (`ESTIMATED`); v2 data with profiles
    loads (`MEASURED`).
12. Performance budgets of 3.3 on the real data (smoke test).

`:app`: `Presentation`/digest/reminder tests updated to the new fields.

Independent check (Python, `pipeline/tests/test_forecast_crosscheck.py`): for the events of
seven days dumped by the CLI as JSON, recompute the sun's azimuth and altitude at `tBest`
with astral and re-evaluate "above the profile and in the opening" with a separate
50-line implementation. This is the review's manual check turned into a test.

### 3.6 Trees

The soft layer exists from M8. Rule in the engine (`EngineConfig.leafOnMonths`, default
April–October):

- leaf-on: a tree above the sun blocks it (treated as hard) but the event is kept with
  `treesInWay = true` and its quality halved, because tree data is coarse;
- leaf-off: trees do not block; `treesInWay` is still set so the UI can say "through bare
  branches".

### 3.7 Files

`core-engine`: new `SkylineProfile.kt`, `SunPath.kt`, `Interest.kt`; rewritten
`HengeEngine.kt`; `Models.kt` (event fields, `Style`, `Confidence`); `EngineConfig.kt`
(new tunables, old tolerance bands removed); `HengeData.kt` (schema v2, profiles);
`Cli.kt` (`dump` as JSON, `check` with links). `app`: `Presentation`, `Digest`, `Workers`,
`Settings` (tolerance sliders replaced by "assumed street width" and "max height above
floor").

### 3.8 Done when

All tests above pass; on the real data the share of events graded good or better that
pass the canyon check of the review is 100 % by construction; the count of daily events is
reported before and after (expected to drop from about 50 to 15–25); you compare five
events against Street View with the M5 links and none shows a wall where the sun should be.

### 3.9 As built (M6, 2026-11-19)

Where the implementation differs from 3.1–3.8, and what was measured:

- **Disc fraction** is the share of the disc's vertical diameter above the skyline at the
  sun's offset, not five sample points: smooth, and 0.5 exactly when the centre touches.
- **PERFECT** needs at least 90 % of the disc (`perfectMinDisc` 0.9), because the best
  moment of an aligned street lands at the full-disc kink within one 5 s step.
- **Open sides:** a side counts as open road only if nothing on it rises above the floor.
  A wall that rises beyond the ±4° edge reports the opening at ±4°. Without this the
  opening snapped from ±4° to ±1.5° as a high sun passed the wall's top at the edge (found
  by the cross-check).
- **Scanning** uses an interpolated sun path (exact every 3 min, linear in between, within
  0.005° of exact; tested), a 60 s pass and a 5 s refinement around the best sample.
- **Window** = contiguous span around the best moment scoring at least 70 % of it
  (`windowScoreFraction`). For an aligned street it ends within a minute of the moment
  the centre reaches the skyline (tested).
- **Stable ids** (prerequisite): the pipeline stores every street with its western end as A
  (`runs.canonicalize`), so rebuilds no longer swap ends and rename a third of the ids.

Measured on the real data (1,369 sightlines):

| | budget | measured |
|---|---|---|
| one day, warm | 50 ms | 19 ms |
| 14 days | 1 s | 0.22 s |
| one sightline, a year | 300 ms | 20 ms |
| full year | 6 s | 2.8 s |

Events per day, 19–25 Nov 2026, against the old engine for 16–22 Nov:

| grade | old | new |
|---|---|---|
| perfect | 8.0 | 10.6 |
| good | 18.0 | 21.0 |
| near | 24.0 | 70.2 |

The plan expected 15–25 events a day; good-or-better stayed near 30. Near grew because
events higher up between the buildings now count; they are hidden by default in the app and
shrink with the "max height above skyline" setting.

Independent cross-check (sun position with astral, visibility rule re-implemented in
Python): all 712 events of that week visible and inside the opening at their best moment;
sun azimuth and altitude within 0.004° of astral. This ran locally; running it in CI needs
astral as a dev dependency (decision for M11).

---

## 4. M7 — UI part 1

Specified in `docs/PLAN-links-and-ui.md` section 2, steps 0–4: screenshots pipeline,
four-tab structure with one shared filter, the new event card, Day screen, Upcoming screen.

Changes caused by M6, to be applied to that design:

- the card shows the **window** (`16:19–16:24`), `tBest`, and the style as a word:
  "on the horizon" or "between the buildings";
- the instruction gains the side of the street: "Stand at the east end, south pavement,
  look WSW";
- a confidence mark: nothing for `MEASURED`, "estimated" for `ESTIMATED`/`PARTIAL`;
- "through bare branches" / "trees may hide it" from `treesInWay`;
- the interim ranking in that document is dropped; `score` from the engine is used;
- sorts: Best (score), Alignment (quality), Time.

Done when: no raw id appears, Upcoming shows at most five cards per day by default, the
filter is shared by all tabs and survives a restart, and you have approved screenshots of
Day and Upcoming in light and dark.

### 4.1 As built (M7, 2026-11-19)

- **Screenshots:** from the phone (D5), not CI. Roborazzi stays a later option.
- **Structure:** tabs Day · Upcoming · Map. Settings is behind the gear in the top bar. The
  top bar's chip shows the filter ("Sunrise & sunset · good+") and opens the filter sheet.
- **Filter:** `model/EventFilter.kt`. It is stored as one DataStore string (`event_filter`).
  Resetting the settings leaves the filter alone.
- **Card:** `ui/EventCard.kt` replaces `EventRow`, including on the detail screens. Long-press
  or ⋮ offers Remind me, Street View, Walk there and Details. The texts live in
  `Presentation`: title fallback "Unnamed path, 423 m", countdown, "east end, north side",
  "look WNW 287°" and the badges. The side of the street is shown when the side step is at
  least 1 m.
- **Day:** sun line from `SunEvents` at Marienplatz, which matches astral to the minute and
  degree. The day is split into Sunrise, Sunset and a dimmed "Earlier today" group. A row of
  quick filter chips scrolls sideways. A footer counts the events the filter hides. An empty
  day offers the next date with an event, looking up to 60 days ahead.
- **Upcoming:** the three best events still ahead come first, then sticky day headers with
  the cloud outlook at sunrise and sunset, then five cards per day and "Show all N".
- **Map:** draws only events the filter passes.
- **Raw ids** are gone from the screens and notifications. `Presentation.sightlineName` and
  `poiName` give the fallbacks.
- **Tunables:** `model/UiConfig.kt`.
- **Tests:** EventFilterTest, EventPresentationTest, DayModelTest, UpcomingModelTest and the
  outlook test in OpenMeteoTest. Together with the older app tests that makes 36, all pure
  JVM.
- **Not done:** the trees badge appears only once M8 provides `treesInWay`. Every card says
  "estimated" until M8 measures the skylines.

---

## 5. M8 — Skyline from Bavarian open data

### 5.1 Inputs and attribution

LoD2 (roofs), DGM5 (ground height; the 5 m text grid avoids a GeoTIFF reader), Einzelbäume
(trees). Licence CC BY 4.0: the app's About screen, the README and the JSON `sources` block
name the Bayerische Vermessungsverwaltung next to OpenStreetMap. The exact attribution
wording is copied from the portal's licence page when this milestone starts.

### 5.2 Stage 1 — reduce the raw data once (`pipeline/pipeline/lod2.py`, `terrain.py`, `trees.py`)

The raw files are far too big to handle on every build, so a separate, cached stage turns
them into compact arrays:

- **Buildings.** Stream each CityGML tile with the standard-library XML parser, take every
  `RoofSurface` polygon, and keep its edges as 3D segments (x1, y1, z1, x2, y2, z2,
  float32) plus one bounding box per building. Roof edges are sufficient: along any ray the
  highest point of a planar roof lies on its boundary, so the skyline is the maximum over
  roof edges. One tile is downloaded, reduced and deleted before the next (the runner has
  about 14 GB of disk). Expected result: roughly 200,000 buildings, a few million edges,
  100–200 MB as compressed `.npz`.
- **Terrain.** DGM5 tiles into one float32 grid for the Munich bounding box.
- **Trees.** Read the GeoPackage with the standard `sqlite3` module (it is an SQLite file;
  point geometries are WKB behind a short header), keep x, y, height above ground for the
  Munich area. First task: read the lot index (a KML on the portal) to find the lot that
  contains Munich.

Caching: the reduced arrays are stored with `actions/cache`, keyed by a hash of the
portal's download lists (file names and sizes), so the 6.5 GB download happens only when
the office publishes new data. Run time of a cold stage 1 is estimated at 15–30
minutes; a warm build adds seconds.

### 5.3 Stage 2 — one profile per viewing direction (`pipeline/pipeline/skyline.py`)

For each sightline direction:

1. Eye point: standing point, ground height from the terrain grid, plus `eye_h_m` (1.7 m).
   Bridges add `bridge_deck_h_m` (config, default 6 m) because the terrain model shows the
   river bank, not the deck; flagged `PARTIAL`.
2. Wedge: ±4.5° around the bearing, from `skyline_min_dist_m` (10 m) to
   `skyline_range_m` (6 km). Candidate buildings from a spatial index over the bounding
   boxes.
3. Rasterise every candidate roof edge into the profile: sample the segment at an angular
   spacing of 0.05°, compute offset and altitude of each sample from the eye point, keep
   the maximum per 0.1° bin (vectorised with numpy).
4. Terrain: sample the ground along each of the 81 rays every 25 m and merge into the hard
   layer.
5. Trees: each tree contributes its top altitude over the angular width of a crown of
   radius `tree_crown_ratio` × height (default 0.25, minimum 2 m) into the soft layer.
6. Completeness: if the wedge leaves the area covered by the downloaded tiles before
   `skyline_range_m`, fill with `far_horizon_deg` (0.3°) and mark the direction `PARTIAL`.

Output per sightline (schema version 2): `skyline_ab`, `skyline_ba` (base64 of 81 bytes),
`trees_ab`, `trees_ba` (only when a tree rises above the hard layer somewhere),
`skyline_status_ab/ba`, `eye_elev_m_a/b`. `obstruction_toward_*` is kept and now holds the
profile's floor, so schema-v1 readers keep working. `canyon` is recomputed from the profile.

Size: about 220 bytes per sightline for the hard layers, up to the same again for trees:
0.3–0.6 MB on top of today's 1.07 MB. The 2 MB limit in `CLAUDE.md` is kept if trees stay
sparse; otherwise see decision D4.

### 5.4 Looking at the result

- `data/debug/skyline/<id>_<dir>.svg`: a small drawing of the profile (hard layer filled,
  trees outlined, axis marked) for every featured sightline and the 100 longest. GitHub
  shows SVG files directly; side by side with the Street View link this is the visual check.
- Debug GeoJSON gains `floor_ab/ba`, `opening_width_ab/ba` (at floor + 0.5°),
  `skyline_status`, and the old estimate for comparison.
- Build summary: histogram of (new floor − old obstruction estimate), count per status,
  the ten largest disagreements with links.

### 5.5 Tests

Synthetic geometry (no downloads):

1. A wall of height H at distance d across the axis gives `atan((H − 1.7)/d)` in exactly
   the bins it spans, nothing elsewhere.
2. Two parallel walls give the V shape of 3.1 within 0.05°.
3. A gabled roof: ridge and eaves produce the higher and lower values at the right offsets.
4. Ground rising behind the far end raises the floor; a building on a hill adds both.
5. A tree lands only in the soft layer; crown width in bins is right.
6. Range limits: a building closer than the minimum or beyond the range is ignored;
   leaving the coverage marks `PARTIAL`.
7. Encoding: clamp at 12.75°, round trip with the Kotlin decoder (shared test vectors).

Real fixtures (small cut-outs committed with attribution, as done for the OSM streets):

8. CityGML parser on a cut-out of tile 690_5334: building count, roof-edge count, the
   98.6 m building's height.
9. One known view: from a fixed point, the altitude of that building's top equals
   `atan((98.6 − 1.7)/distance)` within 0.1°.
10. GeoPackage reader on a ten-tree sample; DGM5 reader on a 100-row sample.

End to end on the synthetic PBF plus synthetic LoD2: sightlines get profiles, JSON stays
within the size limit, output is deterministic.

### 5.6 Done when

`build-data` produces schema-v2 files from a warm cache in under five minutes; the app
loads them and shows `MEASURED` on most events; for ten sightlines you compare SVG profile,
Street View and, for at least three, the real sunset (protocol in section 9); the floor of
those ten differs from what you see by no more than 0.3°.

### 5.7 Limits that remain

- The profile is taken from the endpoint on the centre line. Standing elsewhere along the
  street changes the picture; `lateralOffsetM` covers the sideways part only.
- Tree data is positions and heights, not crowns or species.
- Buildings outside the downloaded tiles and anything built since the last survey flight
  are missing.
- Bridges and other raised viewpoints use an assumed eye height.

---

## 6. M9 — UI part 2

Specified in `docs/PLAN-links-and-ui.md` section 2, steps 5–9: sightline and spot detail
with direction cards and the twelve-month season strip, Places (search, favourites), Map
(base layer drawn once, bottom sheet, legend, optional own location), reminders with exact
alarms and a reminder list, visual polish, strings and language.

Additions from M6 and M8:

- detail shows the skyline profile as a small drawing with the sun's path of the next event
  across it: the most direct explanation of a forecast the app can give;
- the season strip is built from `window` and `style` (two colours: on the horizon,
  between the buildings);
- About screen with data sources and licences.

---

## 7. M10 — Spots worth showing

1. **Opening hours evaluated in the pipeline.** The pipeline converts each `opening_hours`
   tag into a weekly table (seven lists of minute ranges) and writes it to `pois.json`;
   unparsable tags give `null`. The app then needs no parser and can say "open" or "closed"
   for any event time. Public holidays and one-off exceptions are ignored and the card says
   "usually open".
2. **Attachment that means something.** For each view the pipeline records `along_m`
   (position along the sightline), `side` (left or right of the axis for that viewing
   direction) and `seating` (`outdoor`, `unknown`). `poi_snap_m` drops from 40 m to 25 m.
   The app labels spots with outdoor seating on the sightline "watch from here" and the
   rest "nearby".
3. **Ranking input.** `openSpots` in 3.4 counts only "watch from here" spots that are open
   at the event time.
4. **Stretch:** a skyline profile from the spot's own position for outdoor spots, so their
   events are computed where people actually sit.

Tests: hour-table conversion on a table of real tag values taken from the current
`pois.json` (plain ranges, split days, `24/7`, `off`, seasonal rules, garbage); side and
`along_m` geometry; snapping at the new distance; app-side open/closed lookup across
midnight.

Done when: no sunrise event advertises a closed bar; spot counts on cards equal the number
you could actually sit at.

---

## 8. M11 — Operations

- **Scheduled data build:** `build-data` runs on the first of each month and on demand, with
  the OSM extract and the skyline arrays cached. A build that changes more than 20 % of the
  sightline ids fails instead of committing, so a broken extract cannot wipe the data.
- **Forecast check in CI:** the cross-check of 3.5 runs after every data build and on every
  engine change.
- **Releases instead of expiring artifacts:** pushing a tag `v*` builds the signed APK and
  attaches it to a GitHub Release; `versionCode` comes from the run number. Needs the
  keystore secrets (the `create-keystore` workflow is ready).
- **House-keeping:** remove `probe-geodata.yml`; update `CLAUDE.md` (schema v2, new
  vocabulary: skyline profile, opening, window, style, interest; allowed network targets;
  data size limit) and `PLAN.md` (pointer to this plan); merge the feature branch through a
  pull request per milestone instead of one long-lived branch.

---

## 9. Checking against reality

Tests prove the code does what the model says; only observation proves the model. A short
protocol, three evenings to start with:

1. Pick an event graded good or better with `MEASURED` confidence, check it with Street
   View, go there with a clear sky.
2. Stand where the card says. Note the clock time when the sun (a) first appears in the
   street's opening, (b) touches the far roofline with its lower edge, (c) is gone. A photo
   at (b) with the phone's timestamp is enough.
3. Enter the three times in `docs/observations.md` (date, sightline, direction, predicted
   window, observed times, remarks such as "tree in front").

Acceptance for the model: over ten observations, the observed disappearance lies within
two minutes of the predicted window end for at least eight. Systematic offsets feed back
into three tunables: eye height, tree handling, far-horizon value.

---

## 10. Decisions needed

| # | Decision | Recommendation |
|---|---|---|
| D1 | Approve the order M5 → M11 | yes |
| D2 | New pipeline dependencies: `numpy` (already installed through shapely, now used directly) and an `opening_hours` parser for M10 | yes to both |
| D3 | Replace `PLAN.md`'s "full year under 2 s" by the budgets in 3.3 | yes |
| D4 | Data size limit if tree layers push `data/*.json` past 2 MB | raise to 3 MB |
| D5 | How the UI gets rendered for review (options in `PLAN-links-and-ui.md` 3.1) | your phone screenshots now, emulator in CI later |
| D6 | App language: English, German, or both | both, following the phone |
| D7 | Own-location button on the map (location permission, asked only on tap) | yes |
| D8 | Exact alarms for reminders (one extra permission) | yes |
| D9 | Tab layout: Day · Upcoming · Map · Places, Settings behind a gear | yes |
| D10 | Later: MapLibre instead of the archived osmdroid | decide after M9 |
| D11 | Work through pull requests into the default branch from now on | yes |

M5 and the first half of M6 need none of these and can start immediately.

---

## 11. Risks

| Risk | Effect | Mitigation |
|---|---|---|
| CityGML reduction is slower or larger than estimated | long first build, cache over the 10 GB repository limit | measure on three tiles first; drop to footprint + eaves/ridge heights per building if edges are too many |
| The tree lot for Munich is missing or huge | no soft layer | ship M8 without trees; the engine treats a missing layer as "no trees known" |
| Measured profiles remove most events on short streets | lists become thin | expected and correct; `min_length_m` and the grade thresholds are tunable; the report in 3.8 shows the counts before release |
| Engine v2 too slow on the phone | sluggish Upcoming | budgets in 3.3 enforced by a test; coarse-scan fallback designed in |
| Portal changes its file layout | data build breaks | the build reads the download lists rather than guessing addresses; the 20 % guard in M11 keeps old data |
| The model disagrees with observation | wrong times despite all of the above | section 9 is part of the plan, not an afterthought |
