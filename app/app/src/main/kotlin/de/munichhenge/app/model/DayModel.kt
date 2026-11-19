package de.munichhenge.app.model

import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import de.munichhenge.engine.Sightline
import java.time.Instant
import java.time.LocalDate

/** What the Day screen lists, already filtered and sorted (PLAN-links-and-ui.md 2.5). */
data class DaySections(
    val sunrise: List<HengeEvent>,
    val sunset: List<HengeEvent>,
    /** Today's events whose window has closed, dimmed at the end. */
    val earlier: List<HengeEvent>,
    val openHorizon: List<HengeEvent>,
    /** Real events of the day the filter hides. */
    val hiddenCount: Int,
) {
    val isEmpty: Boolean get() = sunrise.isEmpty() && sunset.isEmpty() && earlier.isEmpty()
}

object DayModel {
    fun sections(events: List<HengeEvent>, filter: EventFilter, sightlineOf: (String) -> Sightline?, now: Instant): DaySections {
        val today = now.atZone(Presentation.zone).toLocalDate()
        val shown = filter.apply(events, sightlineOf)
        val (over, ahead) = shown.partition { it.date == today && Presentation.isOver(it, now) }
        return DaySections(
            sunrise = ahead.filter { it.mode == Mode.SUNRISE },
            sunset = ahead.filter { it.mode == Mode.SUNSET },
            earlier = over,
            openHorizon = events.filter { it.openHorizon && it.mode in filter.modes }.sortedBy { it.tBest },
            hiddenCount = events.count { !it.openHorizon } - shown.size,
        )
    }

    /** First date after `from`, at most `maxDays` ahead, with an event the filter shows. */
    fun nextDateWithEvent(
        from: LocalDate, filter: EventFilter, sightlineOf: (String) -> Sightline?, maxDays: Int,
        eventsFor: (LocalDate) -> List<HengeEvent>,
    ): LocalDate? = (1..maxDays).asSequence().map { from.plusDays(it.toLong()) }
        .firstOrNull { d -> eventsFor(d).any { filter.accepts(it, sightlineOf(it.sightlineId)) } }
}
