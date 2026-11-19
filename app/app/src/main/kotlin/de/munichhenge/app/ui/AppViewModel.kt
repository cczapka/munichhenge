package de.munichhenge.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.munichhenge.app.MunichHengeApp
import de.munichhenge.app.model.DayModel
import de.munichhenge.app.model.EventFilter
import de.munichhenge.app.model.Presentation
import de.munichhenge.app.model.UiConfig
import de.munichhenge.app.model.Upcoming
import de.munichhenge.app.model.UpcomingModel
import de.munichhenge.app.notify.ReminderWorker
import de.munichhenge.app.settings.Settings
import de.munichhenge.app.weather.CloudCover
import de.munichhenge.app.weather.WeatherRules
import de.munichhenge.engine.HengeData
import de.munichhenge.engine.HengeEngine
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Sightline
import de.munichhenge.solar.SunEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

/** State shared by the screens: the selected date, the shared filter, the engine and its events. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as MunichHengeApp).graph

    val engine: StateFlow<HengeEngine?> = graph.engine
    val data: HengeData get() = graph.data

    val selectedDate = MutableStateFlow(LocalDate.now(Presentation.zone))

    /** One filter for Day, Upcoming and Map, persisted across restarts. */
    val filter: StateFlow<EventFilter> = graph.settings.filter
        .stateIn(viewModelScope, SharingStarted.Eagerly, EventFilter())

    /** Ticks every half minute for countdowns and "earlier today". */
    val now: StateFlow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(UiConfig.CLOCK_TICK_SECONDS * 1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Instant.now())

    val settings: StateFlow<Settings?> = graph.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** All of the selected day's events, unfiltered; null while computing. */
    val dayEvents: StateFlow<List<HengeEvent>?> = combine(engine, selectedDate) { e, d ->
        if (e == null) null else withContext(Dispatchers.Default) { e.eventsFor(d) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Days on Upcoming the user opened with "Show all". */
    val expandedDays = MutableStateFlow<Set<LocalDate>>(emptySet())

    /** Every event of the next 14 days; filtered and capped in [upcoming]. */
    private val upcomingAll: StateFlow<List<HengeEvent>?> = engine.map { e ->
        e?.let {
            withContext(Dispatchers.Default) {
                it.bestUpcoming(LocalDate.now(Presentation.zone), UiConfig.UPCOMING_DAYS, minQuality = 0.0)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val upcoming: StateFlow<Upcoming?> = combine(upcomingAll, filter, now, expandedDays) { all, f, t, x ->
        all?.let { UpcomingModel.build(it, f, ::sightline, t, x) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun sightline(id: String): Sightline? = data.sightline(id)

    fun setDate(d: LocalDate) { selectedDate.value = d }
    fun shiftDate(days: Long) { selectedDate.value = selectedDate.value.plusDays(days) }

    fun updateFilter(transform: (EventFilter) -> EventFilter) {
        viewModelScope.launch { graph.settings.updateFilter(transform) }
    }

    fun toggleExpanded(d: LocalDate) {
        val cur = expandedDays.value
        expandedDays.value = if (d in cur) cur - d else cur + d
    }

    /** Next date after `from` with an event at the filter, or null within the scan horizon. */
    suspend fun nextDateWithEvent(from: LocalDate, f: EventFilter): LocalDate? {
        val e = engine.value ?: return null
        return withContext(Dispatchers.Default) {
            DayModel.nextDateWithEvent(from, f, ::sightline, UiConfig.NEXT_EVENT_SCAN_DAYS) { e.eventsFor(it) }
        }
    }

    /** Cloud cover at the event's viewing end and best moment; null offline or out of forecast range. */
    suspend fun cloudCover(e: HengeEvent): CloudCover? {
        val sl = data.sightline(e.sightlineId) ?: return null
        return graph.weather.cloudCover(sl.standingPoint(e.viewFrom), e.tBest)
    }

    /** "sunrise ☀ 10% · sunset ☁ 80%" at the city centre, or null beyond the forecast. */
    suspend fun dayOutlook(d: LocalDate): String? {
        val c = UiConfig.CITY_CENTRE
        val rise = SunEvents.sunrise(d, c, Presentation.zone)?.let { graph.weather.cloudCover(c, it) }
        val set = SunEvents.sunset(d, c, Presentation.zone)?.let { graph.weather.cloudCover(c, it) }
        return WeatherRules.outlook(rise, set)
    }

    /** @return false when the reminder would already be in the past. */
    fun addReminder(e: HengeEvent): Boolean {
        val lead = settings.value?.leadMinutes ?: Settings().leadMinutes
        val name = data.sightline(e.sightlineId)?.let(Presentation::sightlineName)
        return ReminderWorker.schedule(getApplication(), e, name, lead)
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { graph.settings.update(transform) }
    }

    fun resetSettings() {
        viewModelScope.launch { graph.settings.reset() }
    }

}
