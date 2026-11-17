package de.munichhenge.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Hands an external link (Street View, directions, geo:) to whichever app can open it. */
object LinkLauncher {
    fun open(context: Context, url: String, chooserTitle: String? = null) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val intent = if (chooserTitle != null) Intent.createChooser(view, chooserTitle) else view
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can open that link", Toast.LENGTH_SHORT).show()
        }
    }
}
