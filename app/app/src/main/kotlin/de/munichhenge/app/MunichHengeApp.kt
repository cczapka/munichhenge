package de.munichhenge.app

import android.app.Application
import de.munichhenge.app.notify.DailyDigestWorker
import de.munichhenge.app.notify.Notifications
import org.osmdroid.config.Configuration
import java.io.File

class MunichHengeApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // osmdroid: identify to the tile servers and keep the tile cache in app storage
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        graph = AppGraph(this)
        Notifications.ensureChannels(this)
        DailyDigestWorker.schedule(this)
    }
}
