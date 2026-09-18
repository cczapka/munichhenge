package de.munichhenge.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import de.munichhenge.app.notify.Notifications
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MunichHengeTheme {
                AppRoot()
            }
        }
        applyDateExtra(intent)
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(this)) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyDateExtra(intent)
    }

    /** A notification tap opens the Today screen on the event's date. */
    private fun applyDateExtra(intent: Intent?) {
        val s = intent?.getStringExtra(Notifications.EXTRA_DATE) ?: return
        runCatching { LocalDate.parse(s) }.getOrNull()?.let { ViewModelProvider(this)[AppViewModel::class.java].setDate(it) }
    }
}
