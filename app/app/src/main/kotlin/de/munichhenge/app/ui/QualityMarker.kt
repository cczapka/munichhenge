package de.munichhenge.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import de.munichhenge.app.R
import de.munichhenge.app.model.MapModel
import de.munichhenge.app.model.UiConfig
import de.munichhenge.engine.Mode

/**
 * Map marker where you stand: the sunrise / sunset symbol on a white disc, ringed by an arc
 * whose length shows today's alignment quality (full circle = perfect). Cached per mode and
 * quality step, so a day with 30 events creates at most a couple of dozen drawables.
 */
class QualityMarker private constructor(context: Context, mode: Mode, step: Int) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val sizePx = (34 * density).toInt()
    private val ringPx = 3.5f * density
    private val color = (if (mode == Mode.SUNSET) ModeColors.sunset else ModeColors.sunrise).toArgb()
    private val sweep = MapModel.ringSweepDeg(step.toDouble() / UiConfig.MAP_RING_STEPS)
    private val glyph = ContextCompat.getDrawable(context, if (mode == Mode.SUNSET) R.drawable.ic_sunset else R.drawable.ic_sunrise)!!
        .mutate()

    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = ringPx; this.color = android.graphics.Color.argb(70, 0, 0, 0)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = ringPx; strokeCap = Paint.Cap.ROUND; this.color = this@QualityMarker.color
    }

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = minOf(b.width(), b.height()) / 2f
        canvas.drawCircle(cx, cy, r, disc)
        val ring = RectF(cx - r + ringPx / 2 + 1, cy - r + ringPx / 2 + 1, cx + r - ringPx / 2 - 1, cy + r - ringPx / 2 - 1)
        canvas.drawOval(ring, track)
        canvas.drawArc(ring, -90f, sweep, false, arc)          // from 12 o'clock, clockwise
        val inset = (r * 0.42f).toInt()
        glyph.setBounds(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
        glyph.draw(canvas)
    }

    override fun setAlpha(alpha: Int) { disc.alpha = alpha; arc.alpha = alpha; glyph.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { glyph.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        private val cache = HashMap<Pair<Mode, Int>, QualityMarker>()

        fun of(context: Context, mode: Mode, quality: Double): QualityMarker {
            val step = MapModel.ringStep(quality)
            return cache.getOrPut(mode to step) { QualityMarker(context.applicationContext, mode, step) }
        }
    }
}
