package de.munichhenge.app.model

import de.munichhenge.engine.Grade
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import de.munichhenge.engine.Sightline

enum class SortOrder(val word: String) {
    /** Engine score: today's alignment weighted by how worthwhile the place is. */
    BEST("best"),
    /** Alignment quality alone. */
    ALIGNMENT("alignment"),
    /** Chronological. */
    TIME("time"),
}

/**
 * The one filter shared by Day, Upcoming and Map (PLAN-links-and-ui.md 2.3), persisted as a
 * string in DataStore. Open-horizon events never pass: they "align" every day and have their
 * own footer.
 */
data class EventFilter(
    val modes: Set<Mode> = setOf(Mode.SUNRISE, Mode.SUNSET),
    /** Lowest grade shown; NEAR shows everything. */
    val minGrade: Grade = Grade.GOOD,
    val withSpots: Boolean = false,
    val canyonOnly: Boolean = false,
    val sort: SortOrder = SortOrder.BEST,
) {
    fun accepts(e: HengeEvent, sightline: Sightline?): Boolean =
        !e.openHorizon && e.mode in modes && e.grade.ordinal <= minGrade.ordinal &&
            (!withSpots || e.poiIds.isNotEmpty()) && (!canyonOnly || sightline?.canyon == true)

    val comparator: Comparator<HengeEvent>
        get() = when (sort) {
            SortOrder.BEST -> compareByDescending<HengeEvent> { it.score }.thenBy { it.tBest }
            SortOrder.ALIGNMENT -> compareByDescending<HengeEvent> { it.quality }.thenByDescending { it.score }.thenBy { it.tBest }
            SortOrder.TIME -> compareBy<HengeEvent> { it.tBest }.thenByDescending { it.score }
        }.thenBy { it.sightlineId }.thenBy { it.viewFrom }

    fun apply(events: List<HengeEvent>, sightlineOf: (String) -> Sightline?): List<HengeEvent> =
        events.filter { accepts(it, sightlineOf(it.sightlineId)) }.sortedWith(comparator)

    /** Turns a mode on or off; the last one stays on. */
    fun toggleMode(m: Mode): EventFilter = when {
        m !in modes -> copy(modes = modes + m)
        modes.size > 1 -> copy(modes = modes - m)
        else -> this
    }

    /** "Sunset · good+", for the chip in the top bar. */
    fun summary(): String {
        val parts = mutableListOf(
            when {
                modes.size == 2 -> "Sunrise & sunset"
                Mode.SUNRISE in modes -> "Sunrise"
                else -> "Sunset"
            },
            gradeLabel(minGrade),
        )
        if (withSpots) parts += "spots"
        if (canyonOnly) parts += "canyons"
        if (sort != SortOrder.BEST) parts += "by ${sort.word}"
        return parts.joinToString(" · ")
    }

    fun encode(): String = listOf(
        "modes=" + Mode.entries.filter { it in modes }.joinToString(",") { it.name },
        "grade=${minGrade.name}",
        "spots=${if (withSpots) 1 else 0}",
        "canyon=${if (canyonOnly) 1 else 0}",
        "sort=${sort.name}",
    ).joinToString(";")

    companion object {
        /** "perfect", "good+", "all grades". */
        fun gradeLabel(g: Grade): String = when (g) {
            Grade.PERFECT -> "perfect"
            Grade.GOOD -> "good+"
            Grade.NEAR -> "all grades"
        }

        /** Never throws: unknown or missing fields keep their defaults. */
        fun decode(s: String?): EventFilter {
            val d = EventFilter()
            if (s.isNullOrBlank()) return d
            val kv = s.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
            }.toMap()
            val modes = kv["modes"]?.split(',')?.mapNotNull { n -> Mode.entries.firstOrNull { it.name == n } }?.toSet()
            return EventFilter(
                modes = modes?.takeIf { it.isNotEmpty() } ?: d.modes,
                minGrade = Grade.entries.firstOrNull { it.name == kv["grade"] } ?: d.minGrade,
                withSpots = kv["spots"]?.let { it == "1" } ?: d.withSpots,
                canyonOnly = kv["canyon"]?.let { it == "1" } ?: d.canyonOnly,
                sort = SortOrder.entries.firstOrNull { it.name == kv["sort"] } ?: d.sort,
            )
        }
    }
}
