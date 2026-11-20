package app.iosvolume.module

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import de.robv.android.xposed.XposedBridge
import kotlin.math.roundToInt

/** Runs inside the SystemUI process and shows the iOS-style pill. */
object VolumeInjector {

    @Volatile
    var ready = false
        private set

    private lateinit var app: Application
    private lateinit var wm: WindowManager
    private lateinit var audio: AudioManager
    private lateinit var pill: PillView
    private val handler = Handler(Looper.getMainLooper())

    private var stream = AudioManager.STREAM_MUSIC
    private var shown = false
    private val hideRunnable = Runnable { hide() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val s = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            val allowed = intArrayOf(
                AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING,
                AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_ALARM
            )
            if (s !in allowed) return
            stream = s
            if (!pill.dragging) updateLevel()
            show()
        }
    }

    private fun dp(v: Int) = (v * app.resources.displayMetrics.density).toInt()

    fun init(application: Application) {
        if (ready) return
        try {
            app = application
            wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            pill = PillView(app).apply {
                onLevelChanged = { l ->
                    val max = audio.getStreamMaxVolume(stream)
                    audio.setStreamVolume(stream, (l * max).roundToInt(), 0)
                }
                onMoonClick = { toggleDnd() }
                onActivity = { scheduleHide() }
            }

            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                app.registerReceiver(receiver, filter)
            }
            ready = true
            XposedBridge.log("iOSVolume: injector ready")
        } catch (t: Throwable) {
            XposedBridge.log("iOSVolume: init failed: $t")
        }
    }

    /** Called when the system tried to open its own volume dialog. */
    fun requestShow() {
        handler.post {
            if (!ready) return@post
            if (!pill.dragging) updateLevel()
            show()
        }
    }

    private fun updateLevel() {
        val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
        pill.level = audio.getStreamVolume(stream).toFloat() / max
        try {
            val nm = app.getSystemService(NotificationManager::class.java)
            val f = nm.currentInterruptionFilter
            pill.dndOn = f != NotificationManager.INTERRUPTION_FILTER_ALL &&
                    f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        } catch (_: Throwable) {
        }
    }

    private fun toggleDnd() {
        try {
            val nm = app.getSystemService(NotificationManager::class.java)
            val isOn = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            nm.setInterruptionFilter(
                if (isOn) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
            pill.dndOn = !isOn
        } catch (t: Throwable) {
            try {
                val cur = Settings.Global.getInt(app.contentResolver, "zen_mode", 0)
                Settings.Global.putInt(app.contentResolver, "zen_mode", if (cur == 0) 1 else 0)
                pill.dndOn = cur == 0
            } catch (t2: Throwable) {
                XposedBridge.log("iOSVolume: DND toggle failed: $t2")
            }
        }
    }

    private fun params(type: Int) = WindowManager.LayoutParams(
        dp(64), dp(250), type,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL  // use Gravity.RIGHT for the right edge
        x = dp(10)
        title = "iOSPill"
    }

    private fun show() {
        pill.animate().cancel()
        if (!shown) {
            pill.alpha = 0f
            pill.translationX = -dp(30).toFloat()
            try {
                // 2020 = TYPE_VOLUME_OVERLAY (also shows above the lock screen)
                wm.addView(pill, params(2020))
                shown = true
            } catch (t: Throwable) {
                try {
                    wm.addView(pill, params(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY))
                    shown = true
                } catch (t2: Throwable) {
                    XposedBridge.log("iOSVolume: addView failed: $t2")
                    return
                }
            }
        }
        pill.animate().alpha(1f).translationX(0f).setDuration(180).start()
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 2500)
    }

    private fun hide() {
        if (!shown) return
        pill.animate().alpha(0f).translationX(-dp(30).toFloat()).setDuration(200)
            .withEndAction {
                try {
                    wm.removeView(pill)
                } catch (_: Throwable) {
                }
                shown = false
            }.start()
    }
}
