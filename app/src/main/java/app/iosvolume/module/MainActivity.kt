package app.iosvolume.module

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val tv = TextView(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            textSize = 16f
            setPadding(pad, pad * 2, pad, pad)
            text = "ماژول iOS Volume\n\n" +
                    "۱) در LSPosed این ماژول را فعال کن.\n" +
                    "۲) Scope را روی «System UI» بگذار.\n" +
                    "۳) System UI را ری‌استارت کن (یا گوشی را ریبوت کن).\n" +
                    "۴) دکمه‌ی صدا را بزن.\n\n" +
                    "اگر پنل قدیمی MIUI هنوز دیده شد، لاگ LSPosed را باز کن و " +
                    "خط‌هایی که با iOSVolume شروع می‌شوند را برای من بفرست."
        }
        setContentView(ScrollView(this).apply { addView(tv) })
    }
}
