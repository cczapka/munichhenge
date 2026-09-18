package de.munichhenge.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.munichhenge.app.R
import de.munichhenge.app.ui.MainActivity
import java.time.LocalDate

object Notifications {
    const val CHANNEL_DIGEST = "digest"
    const val CHANNEL_REMINDER = "reminder"
    const val EXTRA_DATE = "de.munichhenge.date"
    private const val ID_DIGEST = 1

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DIGEST, "Daily henge digest", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Tomorrow's best aligned sightlines, once a day" })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDER, "Event reminders", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Reminders you set for a specific henge moment" })
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openOnDate(context: Context, date: LocalDate, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DATE, date.toString())
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun postDigest(context: Context, date: LocalDate, title: String, body: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openOnDate(context, date, ID_DIGEST))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_DIGEST, n)
    }

    fun postReminder(context: Context, id: Int, date: LocalDate, title: String, body: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openOnDate(context, date, id))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}
