package de.munichhenge.app.model

import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Sightline
import java.time.Instant
import java.time.LocalDate

/** One day on Upcoming: the cards shown and how many the filter lets through in all. */
data class UpcomingDay(val date: LocalDate, val events: List<HengeEvent>, val total: Int) {
    val hidden: Int get() = total - events.size
}

data class Upcoming(val hero: List<HengeEvent>, val days: List<UpcomingDay>)

/** Upcoming screen model (PLAN-links-and-ui.md 2.6): best few on top, then capped days. */
object UpcomingModel {
    fun build(
        events: List<HengeEvent>, filter: EventFilter, sightlineOf: (String) -> Sightline?, now: Instant,
        expanded: Set<LocalDate>, perDay: Int = UiConfig.UPCOMING_PER_DAY, heroCount: Int = UiConfig.HERO_COUNT,
    ): Upcoming {
        val ahead = filter.apply(events, sightlineOf).filter { !Presentation.isOver(it, now) }
        // one card per place: a street aligning on three evenings is one recommendation
        val hero = ahead.sortedWith(compareByDescending<HengeEvent> { it.score }.thenBy { it.tBest })
            .distinctBy { it.sightlineId }.take(heroCount)
        val days = ahead.groupBy { it.date }.toSortedMap().map { (d, es) ->
            UpcomingDay(d, if (d in expanded) es else es.take(perDay), es.size)
        }
        return Upcoming(hero, days)
    }
}
