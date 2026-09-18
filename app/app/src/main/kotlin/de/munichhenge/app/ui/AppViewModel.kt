package de.munichhenge.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.munichhenge.app.MunichHengeApp
import de.munichhenge.app.model.Presentation
import de.munichhenge.app.notify.ReminderWorker
import de.munichhenge.app.weather.CloudCover
import de.munichhenge.app.settings.Settings
import de.munichhenge.engine.HengeData
import de.munichhenge.engine.HengeEngine
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** State shared by the screens: selected date and modes, the engine, the day's events. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as MunichHengeApp).graph

    val engine: StateFlow<HengeEngine?> = graph.engine
    val data: HengeData get() = graph.data

    val selectedDate = MutableStateFlow(LocalDate.now(Presentation.zone))
    val modes = MutableStateFlow(setOf(Mode.SUNRISE, Mode.SUNSET))

    /** Include "near" (1.5-3°) events in the Today list. Off by default: at the equinoxes
     * dozens of east-west streets are within 3°. */
    val showNear = MutableStateFlow(false)

    val settings: StateFlow<Settings?> = graph.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Null while computing. */
    val dayEvents: StateFlow<List<HengeEvent>?> = combine(engine, selectedDate, modes) { e, d, m ->
        if (e == null) null else withContext(Dispatchers.Default) { e.eventsFor(d, m) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Best events in the next 14 days, grouped by day (PLAN.md 4.3 screen 4). */
    val upcoming: StateFlow<List<Pair<LocalDate, List<HengeEvent>>>?> = combine(engine, settings) { e, s ->
        if (e == null || s == null) null else withContext(Dispatchers.Default) {
            Presentation.groupByDay(e.bestUpcoming(LocalDate.now(Presentation.zone), UPCOMING_DAYS, UPCOMING_MIN_QUALITY))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDate(d: LocalDate) { selectedDate.value = d }
    fun shiftDate(days: Long) { selectedDate.value = selectedDate.value.plusDays(days) }

    fun toggleNear() { showNear.value = !showNear.value }

    fun toggleMode(m: Mode) {
        val cur = modes.value
        modes.value = if (m in cur) (if (cur.size > 1) cur - m else cur) else cur + m
    }

    /** Cloud cover at the event's viewing end and t_full; null offline or out of forecast range. */
    suspend fun cloudCover(e: HengeEvent): CloudCover? {
        val sl = data.sightline(e.sightlineId) ?: return null
        return graph.weather.cloudCover(sl.standingPoint(e.viewFrom), e.tFull)
    }

    /** @return false when the reminder would already be in the past. */
    fun addReminder(e: HengeEvent): Boolean {
        val lead = settings.value?.leadMinutes ?: Settings().leadMinutes
        return ReminderWorker.schedule(getApplication(), e, data.sightline(e.sightlineId)?.name, lead)
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { graph.settings.update(transform) }
    }

    fun resetSettings() {
        viewModelScope.launch { graph.settings.reset() }
    }

    companion object {
        const val UPCOMING_DAYS = 14
        /** The Upcoming screen shows everything down to "near"; notifications use Settings.minQuality. */
        const val UPCOMING_MIN_QUALITY = 0.0
    }
}
