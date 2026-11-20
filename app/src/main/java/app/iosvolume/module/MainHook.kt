package app.iosvolume.module

import android.app.Application
import android.app.Dialog
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class MainHook : IXposedHookLoadPackage {

    private val seen = HashSet<String>()

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        // 1) Start the pill inside SystemUI once the app is created.
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.SystemUIApplication",
                lpparam.classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        VolumeInjector.init(param.thisObject as Application)
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("iOSVolume: SystemUIApplication hook failed, using Application.onCreate: $t")
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val a = param.thisObject as Application
                        if (a.packageName == "com.android.systemui") VolumeInjector.init(a)
                    }
                })
        }

        // 2) Block the original volume dialog (AOSP or MIUI) and show the pill instead.
        //    Matches any Dialog class whose name contains "olume", so it does not
        //    depend on the exact MIUI 14 class name.
        XposedHelpers.findAndHookMethod(
            Dialog::class.java,
            "show",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val name = param.thisObject.javaClass.name
                    if (!name.contains("olume")) return
                    if (seen.add(name)) XposedBridge.log("iOSVolume: intercepted dialog $name")
                    if (VolumeInjector.ready) {
                        param.result = null          // skip the original show()
                        VolumeInjector.requestShow()
                    }
                }
            })
    }
}
