package app.iosvolume.module

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** The iOS-style vertical pill: moon button, speaker icon, vertical slider. */
class PillView(ctx: Context) : View(ctx) {

    var level = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var dndOn = false
        set(v) { field = v; invalidate() }
    var dragging = false
        private set

    var onLevelChanged: ((Float) -> Unit)? = null
    var onMoonClick: (() -> Unit)? = null
    var onActivity: (() -> Unit)? = null

    private val d = resources.displayMetrics.density
    private var moonDown = false

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 44, 58, 74) }
    private val paintTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255) }
    private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val paintMoon = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintWave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val rect = RectF()
    private val tmpPath = Path()
    private val speaker = Path()

    private fun trackTop() = 108f * d
    private fun trackBottom() = height - 16f * d

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f

        // Background pill
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, w / 2f, w / 2f, paintBg)

        // Moon button (Do Not Disturb)
        val my = 32f * d
        canvas.drawCircle(cx, my, 20f * d, paintWhite)
        paintMoon.color = if (dndOn) Color.rgb(155, 89, 208) else Color.rgb(150, 150, 160)
        val moonA = Path().apply { addCircle(cx, my, 9f * d, Path.Direction.CW) }
        val moonB = Path().apply { addCircle(cx + 5f * d, my - 4f * d, 8f * d, Path.Direction.CW) }
        moonA.op(moonB, Path.Op.DIFFERENCE)
        canvas.drawPath(moonA, paintMoon)

        // Speaker icon
        val sy = 84f * d
        val sx = cx - 3f * d
        speaker.reset()
        speaker.moveTo(sx - 10f * d, sy - 4f * d)
        speaker.lineTo(sx - 5f * d, sy - 4f * d)
        speaker.lineTo(sx + 1f * d, sy - 9f * d)
        speaker.lineTo(sx + 1f * d, sy + 9f * d)
        speaker.lineTo(sx - 5f * d, sy + 4f * d)
        speaker.lineTo(sx - 10f * d, sy + 4f * d)
        speaker.close()
        canvas.drawPath(speaker, paintWhite)
        if (level > 0.02f) {
            rect.set(sx + 2f * d - 6f * d, sy - 6f * d, sx + 2f * d + 6f * d, sy + 6f * d)
            canvas.drawArc(rect, -45f, 90f, false, paintWave)
        }
        if (level > 0.5f) {
            rect.set(sx + 2f * d - 11f * d, sy - 11f * d, sx + 2f * d + 11f * d, sy + 11f * d)
            canvas.drawArc(rect, -45f, 90f, false, paintWave)
        }

        // Slider track + fill
        val tl = 19f * d
        val tr = w - 19f * d
        val tt = trackTop()
        val tb = trackBottom()
        val r = (tr - tl) / 2f
        rect.set(tl, tt, tr, tb)
        canvas.drawRoundRect(rect, r, r, paintTrack)

        canvas.save()
        tmpPath.reset()
        tmpPath.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.clipPath(tmpPath)
        val top = tb - (tb - tt) * level
        canvas.drawRect(tl, top, tr, tb, paintWhite)
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        onActivity?.invoke()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (e.y < 60f * d) {
                    moonDown = true
                } else if (e.y >= 100f * d) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    applyY(e.y)
                }
            }
            MotionEvent.ACTION_MOVE -> if (dragging) applyY(e.y)
            MotionEvent.ACTION_UP -> {
                if (moonDown && e.y < 60f * d) onMoonClick?.invoke()
                moonDown = false
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                moonDown = false
                dragging = false
            }
        }
        return true
    }

    private fun applyY(y: Float) {
        val tt = trackTop()
        val tb = trackBottom()
        level = (tb - y) / (tb - tt)
        onLevelChanged?.invoke(level)
    }
}
