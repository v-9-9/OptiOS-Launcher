package com.optios.launcher.clock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * Circular day/night dial.
 *
 * The ring is split into a warm (day, 06:00-18:00) and cool (night, 18:00-06:00)
 * arc. A glowing marker travels around the ring with the current time:
 * a small sun during the day, a crescent moon at night. The digital clock
 * lives in the center of the ring (separate TextView in the layout).
 *
 * Self-refreshes every 30 s so it always matches the system time.
 */
class DayNightClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x1EFFFFFF
    }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xB3FFB35C.toInt()  // warm amber
        strokeCap = Paint.Cap.ROUND
    }
    private val nightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xA67B6BFF.toInt()  // soft violet
        strokeCap = Paint.Cap.ROUND
    }
    private val markerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x66FFFFFF
    }
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFD97A.toInt()
    }
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFD97A.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFCFE1FF.toInt()
    }
    private val moonCut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF000000.toInt()
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
    }

    private val arcRect = RectF()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Kick off the recurring self-refresh (each draw schedules the next).
        postInvalidateDelayed(30_000L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Recurring self-refresh: schedule the next paint 30 s ahead.
        postInvalidateDelayed(30_000L)
        val c = Calendar.getInstance()
        val hours = c.get(Calendar.HOUR_OF_DAY)
        val minutes = c.get(Calendar.MINUTE)
        val seconds = c.get(Calendar.SECOND)
        val hourFrac = hours + minutes / 60f + seconds / 3600f

        val stroke = width * 0.055f
        val half = minOf(width, height) / 2f
        val cx = width / 2f
        val cy = height / 2f
        val radius = half - stroke * 1.6f

        trackPaint.strokeWidth = stroke
        dayPaint.strokeWidth = stroke
        nightPaint.strokeWidth = stroke
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Track + day/night halves (midnight at top, each hour = 15 deg).
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)
        canvas.drawArc(arcRect, -90f + 6f * DEG_PER_HOUR, 180f, false, dayPaint)
        canvas.drawArc(arcRect, -90f + 18f * DEG_PER_HOUR, 180f, false, nightPaint)

        // Marker position on the ring.
        val angleDeg = -90f + hourFrac * DEG_PER_HOUR
        val rad = Math.toRadians(angleDeg.toDouble())
        val mx = cx + (radius * cos(rad)).toFloat()
        val my = cy + (radius * sin(rad)).toFloat()

        val isDay = hours in 6..17
        if (isDay) {
            // Glow + sun dot + rays.
            markerGlow.color = 0x66FFD97A.toInt()
            canvas.drawCircle(mx, my, stroke * 1.35f, markerGlow)
            canvas.drawCircle(mx, my, stroke * 0.62f, sunPaint)
            rayPaint.strokeWidth = stroke * 0.14f
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val r1 = stroke * 0.85f
                val r2 = stroke * 1.15f
                canvas.drawLine(
                    mx + (r1 * cos(a)).toFloat(), my + (r1 * sin(a)).toFloat(),
                    mx + (r2 * cos(a)).toFloat(), my + (r2 * sin(a)).toFloat(),
                    rayPaint
                )
            }
        } else {
            // Glow + crescent moon (cut an offset circle out of a save-layer).
            markerGlow.color = 0x66CFE1FF.toInt()
            canvas.drawCircle(mx, my, stroke * 1.35f, markerGlow)
            val d = stroke * 0.62f
            val save = canvas.saveLayer(
                mx - d * 2f, my - d * 2f, mx + d * 2f, my + d * 2f, moonPaint
            )
            canvas.drawCircle(mx, my, d, moonPaint)
            canvas.translate(d * 0.45f, -d * 0.3f)
            canvas.drawCircle(mx, my, d * 0.85f, moonCut)
            canvas.restoreToCount(save)
        }
    }

    private companion object {
        const val DEG_PER_HOUR = 15f
    }
}
