package app.iosvolume.module

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.View

/**
 * iOS-style vertical pill: Focus (moon) button, speaker icon, vertical slider.
 * All proportions are derived from the width, measured from the iOS 18 reference.
 */
class PillView(ctx: Context) : View(ctx) {

    /** Target level. Animated smoothly unless the user is dragging. */
    var level = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            if (dragging) {
                levelAnim?.cancel()
                shownLevel = field
                invalidate()
            } else {
                animateLevelTo(field)
            }
        }
    var dndOn = false
        set(v) { field = v; invalidate() }
    var dragging = false
        private set

    var onLevelChanged: ((Float) -> Unit)? = null
    var onMoonClick: (() -> Unit)? = null
    var onActivity: (() -> Unit)? = null

    private var shownLevel = 0f
    private var levelAnim: ValueAnimator? = null

    private fun animateLevelTo(target: Float) {
        levelAnim?.cancel()
        levelAnim = ValueAnimator.ofFloat(shownLevel, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                shownLevel = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private val d = resources.displayMetrics.density
    private var moonDown = false
    private val purple = Color.rgb(160, 92, 214)

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
        color = Color.argb(60, 255, 255, 255)
    }
    private val paintTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 190, 198, 208) }
    private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintMoon = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintWave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * d
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    private val rect = RectF()
    private val tmpPath = Path()
    private val speaker = Path()
    private val moonA = Path()
    private val moonB = Path()

    private fun circleR() = 0.355f * width
    private fun circleCy() = 0.535f * width
    private fun speakerCy() = 1.53f * width
    private fun trackTop() = 2.0f * width
    private fun trackBottom() = height - 0.32f * width
    private fun trackW() = 0.375f * width

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        paintBg.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.argb(228, 46, 62, 84), Color.argb(228, 88, 94, 102),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f

        // Glass-like pill with a faint light edge
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, w / 2f, w / 2f, paintBg)
        rect.set(0.5f * d, 0.5f * d, w - 0.5f * d, h - 0.5f * d)
        canvas.drawRoundRect(rect, w / 2f, w / 2f, paintEdge)

        // Focus (moon) button: white circle + purple moon; inverted when DND is on
        val r = circleR()
        val my = circleCy()
        paintCircle.color = if (dndOn) purple else Color.WHITE
        canvas.drawCircle(cx, my, r, paintCircle)
        paintMoon.color = if (dndOn) Color.WHITE else purple
        val rm = r * 0.5f
        moonA.reset(); moonA.addCircle(cx, my, rm, Path.Direction.CW)
        moonB.reset(); moonB.addCircle(cx + 0.55f * rm, my - 0.45f * rm, 0.88f * rm, Path.Direction.CW)
        moonA.op(moonB, Path.Op.DIFFERENCE)
        canvas.drawPath(moonA, paintMoon)

        // Speaker icon (centered)
        val k = w / 52f * 1.15f
        val sx = cx - 1.5f * k
        val sy = speakerCy()
        speaker.reset()
        speaker.moveTo(sx - 10f * k, sy - 4f * k)
        speaker.lineTo(sx - 5f * k, sy - 4f * k)
        speaker.lineTo(sx + 1f * k, sy - 9f * k)
        speaker.lineTo(sx + 1f * k, sy + 9f * k)
        speaker.lineTo(sx - 5f * k, sy + 4f * k)
        speaker.lineTo(sx - 10f * k, sy + 4f * k)
        speaker.close()
        canvas.drawPath(speaker, paintWhite)
        if (shownLevel > 0.02f) {
            rect.set(sx + 2f * k - 6f * k, sy - 6f * k, sx + 2f * k + 6f * k, sy + 6f * k)
            canvas.drawArc(rect, -45f, 90f, false, paintWave)
        }
        if (shownLevel > 0.5f) {
            rect.set(sx + 2f * k - 11f * k, sy - 11f * k, sx + 2f * k + 11f * k, sy + 11f * k)
            canvas.drawArc(rect, -45f, 90f, false, paintWave)
        }

        // Slider
        val tw = trackW()
        val tl = cx - tw / 2f
        val tr = cx + tw / 2f
        val tt = trackTop()
        val tb = trackBottom()
        val rr = tw / 2f
        rect.set(tl, tt, tr, tb)
        canvas.drawRoundRect(rect, rr, rr, paintTrack)

        canvas.save()
        tmpPath.reset()
        tmpPath.addRoundRect(rect, rr, rr, Path.Direction.CW)
        canvas.clipPath(tmpPath)
        val top = tb - (tb - tt) * shownLevel
        // rounded top like iOS; the bottom is extended below the track so the clip rounds it
        rect.set(tl, top, tr, tb + rr)
        canvas.drawRoundRect(rect, rr, rr, paintWhite)
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        onActivity?.invoke()
        val moonZone = circleCy() + circleR() + 8f * d
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (e.y < moonZone) {
                    moonDown = true
                } else if (e.y >= trackTop() - 24f * d) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    applyY(e.y)
                }
            }
            MotionEvent.ACTION_MOVE -> if (dragging) applyY(e.y)
            MotionEvent.ACTION_UP -> {
                if (moonDown && e.y < moonZone) onMoonClick?.invoke()
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
