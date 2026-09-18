package de.munichhenge.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.munichhenge.app.MunichHengeApp
import de.munichhenge.app.model.Presentation
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Daily at ~10:00 local: tomorrow's best events as one notification (PLAN.md 4.5). */
class DailyDigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MunichHengeApp
        val graph = app.graph
        val engine = graph.engine.filterNotNull().first()
        val settings = graph.settings.flow.first()
        val tomorrow = LocalDate.now(Presentation.zone).plusDays(1)
        val candidates = engine.bestUpcoming(tomorrow, 1, settings.minQuality)
        val clouds = HashMap<String, de.munichhenge.app.weather.CloudCover?>()
        for (e in candidates.take(Digest.TOP_N * 3)) {
            val sl = graph.data.sightline(e.sightlineId) ?: continue
            clouds[key(e)] = graph.weather.cloudCover(sl.standingPoint(e.viewFrom), e.tFull)
        }
        val cloudOf: (HengeEvent) -> de.munichhenge.app.weather.CloudCover? = { clouds[key(it)] }
        val chosen = Digest.select(candidates, settings.minQuality, cloudOf)
        if (chosen.isEmpty()) return Result.success()
        val (title, body) = Digest.text(tomorrow, chosen, { graph.data.sightline(it)?.name }, cloudOf)
        Notifications.postDigest(applicationContext, tomorrow, title, body)
        return Result.success()
    }

    private fun key(e: HengeEvent) = "${e.sightlineId}/${e.viewFrom}/${e.mode}"

    companion object {
        const val UNIQUE_NAME = "daily-digest"

        fun schedule(context: Context) {
            val delay = Digest.delayUntilNextRun(ZonedDateTime.now(Presentation.zone))
            val request = PeriodicWorkRequestBuilder<DailyDigestWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

/** One-off reminder `leadMinutes` before an event's t_full (the "add reminder" button). */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: ""
        val date = LocalDate.parse(inputData.getString(KEY_DATE) ?: return Result.failure())
        Notifications.postReminder(applicationContext, inputData.getInt(KEY_ID, 2), date, title, body)
        return Result.success()
    }

    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_DATE = "date"
        private const val KEY_ID = "id"

        /** @return false when the reminder time is already in the past. */
        fun schedule(context: Context, event: HengeEvent, sightlineName: String?, leadMinutes: Int): Boolean {
            val fireAt = event.tFull.minus(Duration.ofMinutes(leadMinutes.toLong()))
            val delay = Duration.between(Instant.now(), fireAt)
            if (delay.isNegative) return false
            val name = sightlineName ?: event.sightlineId
            val modeWord = if (event.mode == Mode.SUNSET) "sunset" else "sunrise"
            val title = "Henge in $leadMinutes min: $name"
            val body = "${Presentation.localTime(event.tFull)} $modeWord · ${Presentation.instruction(event)} · ${Presentation.gradeWord(event.grade)}"
            val key = "${event.sightlineId}/${event.viewFrom}/${event.mode}/${event.date}"
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(KEY_TITLE, title).putString(KEY_BODY, body)
                    .putString(KEY_DATE, event.date.toString()).putInt(KEY_ID, 1000 + (key.hashCode() and 0xffff)).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("reminder-$key", ExistingWorkPolicy.REPLACE, request)
            return true
        }
    }
}
