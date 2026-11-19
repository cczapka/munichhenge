package de.munichhenge.app.model

import de.munichhenge.solar.GeoPoint

/** Tunables of the screens (CLAUDE.md rule 3). Engine tunables live in EngineConfig. */
object UiConfig {
    /** Cards per day on Upcoming before "Show all". More shows everything at once; fewer may
     * hide a good event behind the expander. */
    const val UPCOMING_PER_DAY = 5

    /** Events in the "best in the next two weeks" block on top of Upcoming. */
    const val HERO_COUNT = 3

    /** Days Upcoming looks ahead. Longer costs ~20 ms per day and outruns the 16-day forecast. */
    const val UPCOMING_DAYS = 14

    /** A card shows a countdown only this far ahead; beyond, the date says enough. */
    const val COUNTDOWN_MAX_HOURS = 24L

    /** How far an empty Day screen looks for the next date with an event (~20 ms per day). */
    const val NEXT_EVENT_SCAN_DAYS = 60

    /** A side step smaller than this is not worth telling: the street is wide enough. */
    const val SIDE_STEP_MIN_M = 1.0

    /** How often countdowns and "earlier today" refresh. Shorter costs recompositions. */
    const val CLOCK_TICK_SECONDS = 30L

    /** "Earlier today" cards shown before "Show all": the evening view stays short. */
    const val EARLIER_SHOWN = 3

    /** Map: zoom from which every sightline is drawn faintly. Lower clutters the city view. */
    const val MAP_BASE_MIN_ZOOM = 15.0

    /** Map: zoom from which spots appear as dots. */
    const val MAP_SPOTS_MIN_ZOOM = 15.5

    /** Map: event line width for quality 0 and 1, dp. Wider stands out more but hides the street. */
    const val MAP_LINE_MIN_DP = 3f
    const val MAP_LINE_MAX_DP = 8f

    /** Where the day's sun line and the Upcoming cloud outlook are computed (Marienplatz). */
    val CITY_CENTRE = GeoPoint(48.1374, 11.5755)
}
