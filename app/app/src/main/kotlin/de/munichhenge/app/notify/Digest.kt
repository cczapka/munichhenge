package de.munichhenge.app.notify

import de.munichhenge.app.model.Presentation
import de.munichhenge.app.weather.CloudCover
import de.munichhenge.app.weather.WeatherRules
import de.munichhenge.engine.HengeEvent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure logic of the daily digest (PLAN.md 4.5), unit-tested on the JVM. */
object Digest {
    const val TOP_N = 3

    /** Local time the daily digest runs. */
    val RUN_AT: LocalTime = LocalTime.of(10, 0)

    /** Events worth a notification: quality >= minQuality and not forecast cloudy; best first. */
    fun select(events: List<HengeEvent>, minQuality: Double, cloudOf: (HengeEvent) -> CloudCover?): List<HengeEvent> =
        events.filter { !it.openHorizon && it.quality >= minQuality && WeatherRules.worthNotifying(cloudOf(it)) }
            .sortedWith(compareByDescending<HengeEvent> { it.quality }.thenBy { it.tFull })
            .take(TOP_N)

    /** Title and one line per event. */
    fun text(date: LocalDate, events: List<HengeEvent>, nameOf: (String) -> String?,
             cloudOf: (HengeEvent) -> CloudCover?): Pair<String, String> {
        val title = if (events.size == 1) "Henge tomorrow: ${nameOf(events[0].sightlineId) ?: events[0].sightlineId}"
        else "${events.size} henge moments tomorrow (${Presentation.dateLabel(date)})"
        val body = events.joinToString("\n") { e ->
            val cloud = WeatherRules.label(cloudOf(e))?.let { " · $it" } ?: ""
            "${Presentation.localTime(e.tFull)} ${Presentation.modeWord(e.mode)} · ${nameOf(e.sightlineId) ?: e.sightlineId} · " +
                "${Presentation.gradeWord(e.grade)}$cloud"
        }
        return title to body
    }

    /** Delay from `now` to the next RUN_AT in `zone` (today if still ahead, else tomorrow). */
    fun delayUntilNextRun(now: ZonedDateTime, zone: ZoneId = Presentation.zone): Duration {
        val local = now.withZoneSameInstant(zone)
        var next = local.toLocalDate().atTime(RUN_AT).atZone(zone)
        if (!next.isAfter(local)) next = next.plusDays(1)
        return Duration.between(local, next)
    }
}
