package com.optios.launcher.status

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * USAGE widget donuts (tokyo-night style): RAM ring (violet) + CPU ring (blue),
 * with temperature labels below — mirrors the reference card aesthetic.
 */
class StatusDonutsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Data(val ramPct: Int, val cpuPct: Int, val cpuTemp: Int, val gpuTemp: Int)

    private var data = Data(0, 0, 0, 0)

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x22FFFFFF.toInt()
    }
    private val ramRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFBB9AF7.toInt()      // violet
        strokeCap = Paint.Cap.ROUND
    }
    private val cpuRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF7AA2F7.toInt()      // neon blue
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFF4F7FB.toInt()
        isFakeBoldText = true
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xB3F4F7FB.toInt()
    }

    private val rect = RectF()

    fun update(d: Data) {
        data = d
        invalidate()
    }

    /** Theme accent drives both rings: CPU = accent, RAM = accent softened toward white. */
    fun setAccent(accent: Int) {
        cpuRing.color = accent
        fun soften(c: Int): Int = c * 7 / 10 + 255 * 3 / 10
        val r = soften(accent shr 16 and 0xFF)
        val g = soften(accent shr 8 and 0xFF)
        val b = soften(accent and 0xFF)
        ramRing.color = (accent and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = min(w, h) * 0.085f
        val r = (min(w, h) * 0.5f) - stroke * 1.9f
        val cy = h * 0.42f

        fun drawRing(cx: Float, pct: Int, paint: Paint) {
            track.strokeWidth = stroke
            paint.strokeWidth = stroke
            rect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, 0f, 360f, false, track)
            canvas.drawArc(rect, -90f, (pct.coerceIn(0, 100) * 3.6f), false, paint)
        }

        val gap = w * 0.545f
        val c1 = w * 0.27f
        val c2 = c1 + gap

        drawRing(c1, data.ramPct, ramRing)
        drawRing(c2, data.cpuPct, cpuRing)

        label.textSize = h * 0.17f
        sub.textSize = h * 0.115f

        fun centerText(paint: Paint, text: String, cx: Float, cyText: Float) {
            val tw = paint.measureText(text)
            canvas.drawText(text, cx - tw / 2f, cyText, paint)
        }

        centerText(label, "${data.ramPct}%", c1, cy + label.textSize * 0.36f)
        centerText(label, "${data.cpuPct}%", c2, cy + label.textSize * 0.36f)

        // Temperature sublabels under each ring.
        val subY = cy + r + stroke * 2.1f
        centerText(sub, "RAM", c1, subY)
        centerText(sub, "CPU ${data.cpuTemp}°C", c2, subY)
        // GPU temp under RAM ring's label row (small, dimmer).
        sub.textSize = h * 0.1f
        centerText(sub, "GPU ${data.gpuTemp}°C", c1, subY + sub.textSize * 1.5f)
    }
}
