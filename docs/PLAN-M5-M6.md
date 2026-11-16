# munichhenge — Proposal: M5 "Take me there" and M6 UI overhaul

Status: **proposal, not approved**. Nothing here is implemented. It follows the review of
2026-11-16 (astronomy verified; visibility model, ranking and UI found weak). Vocabulary is
the one fixed in `CLAUDE.md`. Each milestone lists its tests and "done when", as `PLAN.md` does.

Out of scope here, but both milestones are designed to absorb them later:

- **Engine rework** (width-based tolerance, the true centred moment, a ranking score). M6 uses
  presentation-level stand-ins and names the seam where the engine values plug in.
- **LoD2 skyline** from Bavarian open geodata (real obstruction angles and street widths).

Open decisions are collected in section 3. Nothing in M5 needs a decision.

---

## 1. M5 — "Take me there": Street View preview and navigation

### 1.1 Why

A forecast is only useful if you can (a) judge beforehand whether the view is any good and
(b) get to the right end of the street in time. Today the app does neither. A Street View
link from the standing point along the bearing shows the real far end of the sightline —
the building, tree or open sky that our obstruction angle only guesses at — and it is also
the cheapest verification tool we have for the data itself.

### 1.2 Approach

No SDK, no API key, no new dependency, no in-app network call: the app builds a URL and
hands it to Android with `ACTION_VIEW`. `CLAUDE.md` says the app's only network calls are
Open-Meteo and map tiles; opening an external app does not change that, and one line will be
added to `CLAUDE.md` to say so.

**Links** (built by a pure Kotlin object, `app/.../model/ExternalLinks.kt`, no Android imports):

| Purpose | Form |
|---|---|
| Street View along a bearing | `https://www.google.com/maps/@?api=1&map_action=pano&viewpoint={lat},{lon}&heading={deg}&pitch={deg}&fov={deg}` |
| Walking directions to the standing point | `https://www.google.com/maps/dir/?api=1&destination={lat},{lon}&travelmode=walking` |
| Any installed map app (OsmAnd, Organic Maps, …) | `geo:{lat},{lon}?q={lat},{lon}({url-encoded label})` |

Parameter rules, to be pinned by tests:

- Coordinates with 6 decimals, formatted with `Locale.ROOT` (a German locale would write
  `48,143` and break the URL).
- `heading` = the bearing the viewer looks toward, rounded to a whole degree, normalised so
  360 becomes 0.
- `pitch` = the obstruction angle of that direction rounded up, clamped to 0–10. It tilts
  the camera to where the sun will sit.
- `fov` = 60. The street's notch is a degree or two wide; the default 90 makes it tiny.
- The Street View parameter ranges come from secondary sources (Google's reference page was
  not reachable while writing this). The first implementation step is to open three links
  on the phone and confirm heading and pitch behave as assumed.

**Which point and bearing**

| Kind | Viewpoint | Heading |
|---|---|---|
| street, axis | standing point of the direction (`Sightline.standingPoint(viewFrom)`) | `Sightline.bearing(viewFrom)` |
| bridge | the crossing point on the deck (endpoint A) | bearing along the river |
| canal, river | standing point; labelled "nearest street imagery" because the endpoint is on water | bearing |
| open horizon | the spot | sun azimuth of the event shown |
| spot (POI) | the POI's own position | bearing of the view |

Street View snaps to the nearest panorama, so an endpoint in the middle of a junction is fine.

**Android glue** (`app/.../ui/ExternalLinkLauncher.kt`): `startActivity(Intent(ACTION_VIEW, uri))`
inside `try/catch (ActivityNotFoundException)`. The https links open in Google Maps when it
is installed and in the browser otherwise. The `geo:` link gets a chooser. No `<queries>`
entry and no permission are needed because we never resolve packages ourselves.

### 1.3 Where it appears

1. **Sightline detail** — a "direction card" per viewing direction, replacing the two plain
   text lines: compass arrow, "Stand at the east end, look WNW (287°)", obstruction angle,
   and three actions: **Street View**, **Walk there**, **Open in maps**.
2. **Event rows in detail** ("next events") — the row's direction decides which card is
   highlighted; no extra buttons on the row.
3. **Spot detail** — **Street View from here** per view, plus **Walk there**.
4. **Map bottom sheet** (arrives with M6 step 7) — the same three actions.
5. Detail route gains the direction: `sightline/{id}?from={A|B}` so a tap on an event opens
   the detail with the right card first.

### 1.4 The same links as a verification tool

- `pipeline/pipeline/output.py`: the debug GeoJSON gets `streetview_ab` and `streetview_ba`
  properties. Clicking a line on GitHub's map view then shows two links: one tap to see
  whether a sightline is real.
- `core-engine` CLI: a `check <date> [n]` command printing the top n events with their
  Street View and directions links, so ten forecasts can be checked from the sofa.
- The URL builder therefore lives twice (Python and Kotlin). Both get the same test vectors.

### 1.5 Known limits (shown as a one-line hint under the buttons)

- Imagery has a date and a season: summer foliage hides what a November sunset shows.
- The camera is about 2.5 m up, on the road; you will stand lower and on the pavement.
- No coverage in pedestrian zones, park axes and along canals; Google then shows the
  nearest panorama or none. We cannot test coverage without an API key, so the button is
  always offered.
- It shows the geometry of the far end, not the sun.

### 1.6 Tests (JVM, no network)

- `ExternalLinksTest`: exact strings for a street, a bridge and a spot; `Locale.GERMANY`
  as default locale still yields dots; heading 359.6 → 0; pitch clamps (0.4 → 1, 14 → 10);
  label with `ß`, umlauts, space and parentheses is encoded.
- Python `test_output.py`: same vectors produce the same URLs; GeoJSON features carry both
  properties; open-horizon features carry none.
- Presentation: direction-card text for both endpoints.

### 1.7 Done when

On the phone, from a sightline's detail: Street View opens facing down the street toward
the far end; Walk there routes to the standing point; Open in maps offers the installed map
apps; none of them crash with Google Maps uninstalled. The debug GeoJSON on GitHub shows
working links. Tests pass.

Size: small. One working session including the CI round trip.

---

## 2. M6 — UI overhaul

### 2.1 What the app has to answer

1. **What is worth seeing soon?** (Day, Upcoming)
2. **Where exactly do I stand, when, and is it worth going?** (Detail, Map, M5 links, weather)
3. **When does my street or spot align?** (Places: search, favourites, season strip)

Today's UI answers 1 with too much noise, 2 partially, and 3 only if you happen to find the
street on the map.

### 2.2 Step 0 — get eyes on the UI (blocks the visual steps)

The UI was written without ever being rendered. Before restyling anything, one of the
options in section 3.1 has to be in place. The plan assumes screenshots land in
`docs/screenshots/` on the branch (committed by CI, as `data/` is), because files in the
repo are what the assistant can actually open.

Screens to capture, light and dark: Day (with events, empty), Upcoming, Map, Sightline
detail, Spot detail, Places (empty search, results), Settings.

### 2.3 Step 1 — structure and shared state

- **Tabs:** Day · Upcoming · Map · Places. Settings moves to a gear in the top bar.
- **One filter state** shared by Day, Upcoming and Map, held in `AppViewModel` and persisted
  in DataStore: modes (sunrise/sunset), minimum grade (perfect / good / near), "with spots",
  "canyon only", sort order. Today each screen filters on its own and Upcoming not at all.
- **Top bar** on every tab: title, the filter summary as one tappable chip ("Sunset · good+"),
  gear.
- Tests: filter reducer (toggle rules, at least one mode stays on), persistence round trip.

### 2.4 Step 2 — the event card (the component everything else reuses)

Replaces `EventRow`. Content, top to bottom:

1. **Title**: sightline name. Never a raw id: unnamed sightlines read "Unnamed path, 423 m"
   now, and "Path near {street}" once the pipeline adds a `near` field (small pipeline task,
   listed in 2.13).
2. **Time line**: `16:21` large; the visible window `16:19–16:23` (between `tFull` and
   `tHalf`, order depends on mode); a countdown "in 2 h 14 min" when the event is today.
3. **Direction chip**: a small arrow rotated to the bearing, "look WSW 243°", and
   "east end" for where to stand.
4. **Badges**: grade (dark text on amber, white on green and grey, all at AA contrast),
   canyon, featured, length, spot count ("3 spots · 2 open" once opening hours are parsed).
5. **Cloud hint** right-aligned.

Behaviour:

- Events already over today are dimmed and collected under an "Earlier today" divider.
- Long-press or the overflow icon: Remind me, Street View, Walk there, Favourite.
- **Seam for the engine rework:** the card reads `displayTime`, `windowStart`, `windowEnd`
  and `score` from a small `EventPresentation` class. Today these derive from
  `tFull`/`tHalf`/`quality`; after the rework they come from the engine unchanged.
- **Interim ranking** ("Best" sort), presentation-level until the engine has a score:
  featured first, then grade, then spots open or present, then length descending.
  Other sorts: Time, Length.

Tests: `EventPresentation` (window order per mode, countdown text at 59 s / 61 min / past),
interim ranking order on a fixture, title fallback, badge set.

### 2.5 Step 3 — Day screen

- Header: previous / date / next, a "Today" chip that appears only when another date is
  selected, and a **sun line**: "Sunrise 07:21 at 118° · Sunset 16:34 at 242°" from
  `SunEvents`. It teaches what bearings matter this week.
- Two sections, "Sunrise" and "Sunset", each with its count; "Earlier today" last.
- Filter chips in a horizontally scrolling row (today's fixed row can overflow on narrow
  phones).
- Empty state names the next date that has an event at the current filter.
- Open-horizon footer stays as is.

### 2.6 Step 4 — Upcoming

- Honours the shared filter; default good and better. Today it lists about 700 rows.
- **Hero block**: the three best events of the next 14 days with weather.
- Per day: header with date and the cloud outlook for sunrise and sunset hour, then the top
  five cards and a "Show all 23" expander.
- Sticky day headers.
- Tests: grouping and capping, hero selection, expander state.

### 2.7 Step 5 — Sightline and spot detail

- Header: name, kind, length, badges, **favourite star**.
- **Mini map**, 180 dp, non-interactive, fitted to the sightline with the sun ray drawn;
  tapping it opens the Map tab centred there.
- **Direction cards** from M5.
- **Season strip**: a twelve-month bar per direction showing the weeks in which it aligns,
  sunrise and sunset in different colours, grade by intensity, today marked. This is the
  at-a-glance answer to "when does my street align". Computed off the main thread by
  scanning 365 days for that one sightline and cached per sightline and settings.
- **Next events** as cards with a bell toggle (filled when a reminder exists).
- **Spots** with distance to the axis, outdoor seating if tagged, and open/closed at the
  next event's time once opening hours are parsed; raw hours until then.
- Spot detail mirrors this: its views, season strip of its best view, next events.
- Tests: season-strip bucketing (a 270° street shows two blocks around the equinoxes; a
  200° street shows none), cache key.

### 2.8 Step 6 — Places (search and favourites)

- Search field over sightline and spot names, in memory. Matching is case-insensitive and
  folds `ß`→`ss`, umlauts, `straße`/`str.`; prefix matches rank before substring matches.
- Results in two groups, Streets and Spots, each row with its next event date and grade.
- Above the results when the field is empty: **Favourites**, then **Featured**.
- Favourites are ids in DataStore. A setting "Notify only for favourites" narrows the daily
  digest.
- Tests: normalisation table, ranking, favourites store.

### 2.9 Step 7 — Map

- **Performance first.** Today `drawOverlays` rebuilds 1,365 polylines on every
  recomposition. Split into a base layer built once (one custom overlay drawing all
  sightlines in a single pass, hidden below zoom 13) and an event layer rebuilt only when
  date or filter changes (`remember` keyed on both).
- Date control and filter chip on the map, same components as the Day screen.
- **Bottom sheet** on tap with the event card and the M5 actions, instead of jumping
  straight to the detail screen.
- Legend for the grade colours; spot markers as small kind icons, shown from zoom 15.
- **My location** button: asks for location permission only when pressed, never at start.
- Tests: overlay-model builder (which lines, widths, colours for a date) as pure logic;
  the osmdroid drawing itself stays untested.
- Replacing osmdroid with MapLibre is a separate decision (3.5) and not part of this step.

### 2.10 Step 8 — Reminders that fire on time

- WorkManager may deliver many minutes late under Doze, which defeats a reminder for a
  four-minute event. Use `AlarmManager.setExactAndAllowWhileIdle` when exact alarms are
  allowed, WorkManager as the fallback, and say which one is active in Settings.
- Reminders become data: a DataStore list (event key, fire time, title). A boot receiver
  re-arms them, because alarms do not survive a reboot.
- A "Reminders" list under Places with swipe to cancel; the bell on cards reflects it.
- Tests: reminder store, fire-time computation, re-arm selection after boot (drops past
  ones), event key stability.

### 2.11 Step 9 — visual polish and language

- **Theme:** a DayNight platform theme in the manifest so the window background matches
  Compose (no white flash in dark mode); one colour scheme with checked contrast; a small
  type scale (title, time, body, label).
- **Launcher icon:** adaptive icon with foreground, background and a monochrome layer.
- **Strings:** all user-facing text moves to `strings.xml`; German added if decided (3.2).
  `Presentation` stays pure Kotlin and takes a small `Strings` interface so it remains
  JVM-testable.
- **Accessibility:** 48 dp touch targets, content descriptions, one spoken sentence per card.
- **Explainer:** tapping a grade badge opens a short sheet: what a henge moment is, what
  the grades mean, why times are estimates (obstruction angles are estimated).

### 2.12 Order, size, done when

| Step | Depends on | Size |
|---|---|---|
| 0 Screenshots | decision 3.1 | small |
| 1 Structure and filter | — | small |
| 2 Event card | 1 | medium |
| 3 Day | 2 | small |
| 4 Upcoming | 2 | small |
| 5 Detail and season strip | 2, M5 | medium |
| 6 Places | 1 | medium |
| 7 Map | 1, 2 | medium |
| 8 Reminders | decision 3.4 | medium |
| 9 Polish and language | 0, decision 3.2 | medium |

Steps 1–4 already fix the largest annoyances (noise, raw ids, no filter in Upcoming) and
can ship as one APK. Each step ends with green `:app:testDebugUnitTest`, a new APK from
`android-build`, and fresh screenshots.

**M6 is done when:** from a cold start you can find a named street in Places and see its
season strip in under ten seconds; Upcoming shows at most five cards per day by default;
no raw id appears anywhere; the map pans smoothly; a reminder fires within a minute of its
time on your phone; and you have looked at every screenshot and approved the look.

### 2.13 Small pipeline tasks this plan leans on

- `near` field for unnamed sightlines: the name of the nearest named candidate way.
- `streetview_*` properties in the debug GeoJSON (M5).
- A monthly schedule for `build-data` so OSM changes and closed venues are picked up.

---

## 3. Decisions needed

### 3.1 How the UI gets rendered for review (needed before step 9, useful from step 2)

| Option | What it shows | Cost |
|---|---|---|
| **A. You send phone screenshots in the chat** | the truth on your device | zero setup; manual each round |
| **B. Emulator + Maestro flow in GitHub Actions** | the real app end to end, including map and navigation | no app dependency; about 10 min per run; emulator runs on CI can be flaky |
| **C. Roborazzi** | single screens rendered on the JVM in about a minute | adds Robolectric and Roborazzi as test dependencies; no real map tiles |
| **D. Paparazzi** | like C | Gradle plugin; no Robolectric, so screens that need a ViewModel or DataStore need more scaffolding; known to break on Compose/AGP upgrades |
| **E. Compose Preview Screenshot Testing (Google)** | `@Preview` functions only | AGP plugin; previews in a separate source set; maturity uncertain |

Recommendation: **A now, B as the automated path.** B adds nothing to the app and shows what
you actually see. C is the better choice only if fast regression checks on every push
become important.

### 3.2 Language: English only, German only, or both (following the phone)?

### 3.3 Location permission for the "my location" button on the map: yes or no?

### 3.4 Exact alarms for reminders (one extra permission, granted in system settings on
recent Android): yes or no?

### 3.5 New dependencies (CLAUDE.md rule 7), each optional

- An `opening_hours` parser, to show spots as open or closed at event time.
- MapLibre in place of osmdroid (archived since November 2024). Larger change; recommended
  only after M6.
- Whichever of C/D/E is chosen in 3.1, if any.

### 3.6 Tab layout as in 2.3, with Settings behind a gear: agreed?
