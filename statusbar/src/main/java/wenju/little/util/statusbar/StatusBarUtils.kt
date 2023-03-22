package wenju.little.util.statusbar

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import wenju.little.util.statusbar.DeviceUtils.isEssentialPhone
import wenju.little.util.statusbar.DeviceUtils.isFlyme
import wenju.little.util.statusbar.DeviceUtils.isFlymeLowerThan
import wenju.little.util.statusbar.DeviceUtils.isMIUI
import wenju.little.util.statusbar.DeviceUtils.isMIUIV5
import wenju.little.util.statusbar.DeviceUtils.isMIUIV6
import wenju.little.util.statusbar.DeviceUtils.isMIUIV7
import wenju.little.util.statusbar.DeviceUtils.isMIUIV8
import wenju.little.util.statusbar.DeviceUtils.isMIUIV9
import wenju.little.util.statusbar.DeviceUtils.isMeizu
import wenju.little.util.statusbar.DisplayUtils.dp2px
import java.lang.reflect.Field

object StatusBarUtils {
    private const val STATUS_BAR_DEFAULT_HEIGHT_DP = 25 // 大部分状态栏都是25dp

    // 在某些机子上存在不同的density值，所以增加两个虚拟值
    var sVirtualDensity = -1f
    var sVirtualDensityDpi = -1f
    private var sStatusBarHeight = -1
    private var mStatusBarType = StatusBarType.Default
    private var sTransparentValue: Int? = null

    /**
     * 沉浸式状态栏。
     * 支持 4.4 以上版本的 MIUI 和 Flyme，以及 5.0 以上版本的其他 Android。
     *
     * @param activity 需要被设置沉浸式状态栏的 Activity。
     */
    fun translucent(activity: Activity) {
        translucent(activity.window)
    }

    fun translucent(window: Window) {
        translucent(window, 0x40000000)
    }

    private fun supportTranslucent(): Boolean {
        // Essential Phone 在 Android 8 之前沉浸式做得不全，系统不从状态栏顶部开始布局却会下发 WindowInsets
        return !(isEssentialPhone && Build.VERSION.SDK_INT < 26)
    }

    @TargetApi(19)
    fun translucent(window: Window, @ColorInt colorOn5x: Int) {
        if (!supportTranslucent()) {
            // 版本小于4.4，绝对不考虑沉浸式
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            handleDisplayCutoutMode(window)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // 小米 Android 6.0 ，开发版 7.7.13 及以后版本设置黑色字体又需要 clear FLAG_TRANSLUCENT_STATUS, 因此还原为官方模式
            if (isFlymeLowerThan(8) || isMIUI && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                )
                return
            }
        }
        var systemUiVisibility = window.decorView.systemUiVisibility
        systemUiVisibility =
            systemUiVisibility or (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        window.decorView.systemUiVisibility = systemUiVisibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // android 6以后可以改状态栏字体颜色，因此可以自行设置为透明
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
        } else {
            // android 5不能修改状态栏字体颜色，因此直接用FLAG_TRANSLUCENT_STATUS，nexus表现为半透明
            // 魅族和小米的表现如何？
            // update: 部分手机运用FLAG_TRANSLUCENT_STATUS时背景不是半透明而是没有背景了。。。。。
//                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

            // 采取setStatusBarColor的方式，部分机型不支持，那就纯黑了，保证状态栏图标可见
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = colorOn5x
        }
    }

    /**
     * 如果原本存在某一个flag， 就将它迁移到 out
     * @param window
     * @param out
     * @param type
     * @return
     */
    fun retainSystemUiFlag(window: Window, out: Int, type: Int): Int {
        var out = out
        val now = window.decorView.systemUiVisibility
        if (now and type == type) {
            out = out or type
        }
        return out
    }

    @TargetApi(28)
    private fun handleDisplayCutoutMode(window: Window) {
        val decorView = window.decorView
        if (decorView != null) {
            if (ViewCompat.isAttachedToWindow(decorView)) {
                realHandleDisplayCutoutMode(window, decorView)
            } else {
                decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.removeOnAttachStateChangeListener(this)
                        realHandleDisplayCutoutMode(window, v)
                    }

                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }
        }
    }

    @TargetApi(28)
    private fun realHandleDisplayCutoutMode(window: Window, decorView: View) {
        if (decorView.rootWindowInsets != null &&
            decorView.rootWindowInsets.displayCutout != null
        ) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }
    }

    /**
     * 设置状态栏黑色字体图标，
     * 支持 4.4 以上版本 MIUI 和 Flyme，以及 6.0 以上版本的其他 Android
     *
     * @param activity 需要被处理的 Activity
     */
    fun setStatusBarLightMode(activity: Activity?): Boolean {
        if (activity == null) return false
        if (mStatusBarType != StatusBarType.Default) {
            return setStatusBarLightMode(activity, mStatusBarType)
        }
        if (isMIUICustomStatusBarLightModeImpl && MIUISetStatusBarLightMode(
                activity.window,
                true
            )
        ) {
            mStatusBarType = StatusBarType.Miui
            return true
        } else if (FlymeSetStatusBarLightMode(activity.window, true)) {
            mStatusBarType = StatusBarType.Flyme
            return true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Android6SetStatusBarLightMode(activity.window, true)
            mStatusBarType = StatusBarType.Android6
            return true
        }
        return false
    }

    /**
     * 已知系统类型时，设置状态栏黑色字体图标。
     * 支持 4.4 以上版本 MIUI 和 Flyme，以及 6.0 以上版本的其他 Android
     *
     * @param activity 需要被处理的 Activity
     * @param type     StatusBar 类型，对应不同的系统
     */
    private fun setStatusBarLightMode(activity: Activity, type: StatusBarType): Boolean {
        if (type == StatusBarType.Miui) {
            return MIUISetStatusBarLightMode(activity.window, true)
        } else if (type == StatusBarType.Flyme) {
            return FlymeSetStatusBarLightMode(activity.window, true)
        } else if (type == StatusBarType.Android6) {
            return Android6SetStatusBarLightMode(activity.window, true)
        }
        return false
    }

    /**
     * 设置状态栏白色字体图标
     * 支持 4.4 以上版本 MIUI 和 Flyme，以及 6.0 以上版本的其他 Android
     */
    fun setStatusBarDarkMode(activity: Activity?): Boolean {
        if (activity == null) return false
        if (mStatusBarType == StatusBarType.Default) {
            // 默认状态，不需要处理
            return true
        }
        if (mStatusBarType == StatusBarType.Miui) {
            return MIUISetStatusBarLightMode(activity.window, false)
        } else if (mStatusBarType == StatusBarType.Flyme) {
            return FlymeSetStatusBarLightMode(activity.window, false)
        } else if (mStatusBarType == StatusBarType.Android6) {
            return Android6SetStatusBarLightMode(activity.window, false)
        }
        return true
    }

    /**
     * 设置状态栏字体图标为深色，Android 6
     *
     * @param window 需要设置的窗口
     * @param light  是否把状态栏字体及图标颜色设置为深色
     * @return boolean 成功执行返回true
     */
    @TargetApi(23)
    private fun Android6SetStatusBarLightMode(window: Window, light: Boolean): Boolean {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (insetsController != null) {
                insetsController.isAppearanceLightStatusBars = light
            }
        } else {
            // 经过测试，小米 Android 11 用  WindowInsetsControllerCompat 不起作用， 我还能说什么呢。。。
            val decorView = window.decorView
            var systemUi = decorView.systemUiVisibility
            systemUi = if (light) {
                systemUi or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                systemUi and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            decorView.systemUiVisibility = systemUi
        }
        if (isMIUIV9) {
            // MIUI 9 低于 6.0 版本依旧只能回退到以前的方案
            // https://github.com/Tencent/QMUI_Android/issues/160
            MIUISetStatusBarLightMode(window, light)
        }
        return true
    }

    /**
     * 设置状态栏字体图标为深色，需要 MIUIV6 以上
     *
     * @param window 需要设置的窗口
     * @param light  是否把状态栏字体及图标颜色设置为深色
     * @return boolean 成功执行返回 true
     */
    fun MIUISetStatusBarLightMode(window: Window?, light: Boolean): Boolean {
        var result = false
        if (window != null) {
            val clazz: Class<*> = window.javaClass
            try {
                val darkModeFlag: Int
                val layoutParams = Class.forName("android.view.MiuiWindowManager\$LayoutParams")
                val field = layoutParams.getField("EXTRA_FLAG_STATUS_BAR_DARK_MODE")
                darkModeFlag = field.getInt(layoutParams)
                val extraFlagField = clazz.getMethod(
                    "setExtraFlags",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                if (light) {
                    extraFlagField.invoke(window, darkModeFlag, darkModeFlag) //状态栏透明且黑色字体
                } else {
                    extraFlagField.invoke(window, 0, darkModeFlag) //清除黑色字体
                }
                result = true
            } catch (ignored: Exception) {
            }
        }
        return result
    }

    /**
     * 更改状态栏图标、文字颜色的方案是否是MIUI自家的， MIUI9 && Android 6 之后用回Android原生实现
     * 见小米开发文档说明：https://dev.mi.com/console/doc/detail?pId=1159
     */
    private val isMIUICustomStatusBarLightModeImpl: Boolean
        private get() = if (isMIUIV9 && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            true
        } else isMIUIV5 || isMIUIV6 ||
                isMIUIV7 || isMIUIV8

    /**
     * 设置状态栏图标为深色和魅族特定的文字风格
     * 可以用来判断是否为 Flyme 用户
     *
     * @param window 需要设置的窗口
     * @param light  是否把状态栏字体及图标颜色设置为深色
     * @return boolean 成功执行返回true
     */
    fun FlymeSetStatusBarLightMode(window: Window?, light: Boolean): Boolean {
        var result = false
        if (window != null) {
            Android6SetStatusBarLightMode(window, light)

            // flyme 在 6.2.0.0A 支持了 Android 官方的实现方案，旧的方案失效
            // 高版本调用这个出现不可预期的 Bug,官方文档也没有给出完整的高低版本兼容方案
            if (isFlymeLowerThan(7)) {
                try {
                    val lp = window.attributes
                    val darkFlag = WindowManager.LayoutParams::class.java
                        .getDeclaredField("MEIZU_FLAG_DARK_STATUS_BAR_ICON")
                    val meizuFlags = WindowManager.LayoutParams::class.java
                        .getDeclaredField("meizuFlags")
                    darkFlag.isAccessible = true
                    meizuFlags.isAccessible = true
                    val bit = darkFlag.getInt(null)
                    var value = meizuFlags.getInt(lp)
                    value = if (light) {
                        value or bit
                    } else {
                        value and bit.inv()
                    }
                    meizuFlags.setInt(lp, value)
                    window.attributes = lp
                    result = true
                } catch (ignored: Exception) {
                }
            } else if (isFlyme) {
                result = true
            }
        }
        return result
    }

    /**
     * 获取是否全屏
     *
     * @return 是否全屏
     */
    fun isFullScreen(activity: Activity): Boolean {
        var ret = false
        try {
            val attrs = activity.window.attributes
            ret = attrs.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ret
    }

    /**
     * API19之前透明状态栏：获取设置透明状态栏的system ui visibility的值，这是部分有提供接口的rom使用的
     * http://stackoverflow.com/questions/21865621/transparent-status-bar-before-4-4-kitkat
     */
    fun getStatusBarAPITransparentValue(context: Context): Int? {
        if (sTransparentValue != null) {
            return sTransparentValue
        }
        val systemSharedLibraryNames = context.packageManager
            .systemSharedLibraryNames
        var fieldName: String? = null
        for (lib in systemSharedLibraryNames!!) {
            if ("touchwiz" == lib) {
                fieldName = "SYSTEM_UI_FLAG_TRANSPARENT_BACKGROUND"
            } else if (lib.startsWith("com.sonyericsson.navigationbar")) {
                fieldName = "SYSTEM_UI_FLAG_TRANSPARENT"
            }
        }
        if (fieldName != null) {
            try {
                val field = View::class.java.getField(fieldName)
                if (field != null) {
                    val type = field.type
                    if (type == Int::class.javaPrimitiveType) {
                        sTransparentValue = field.getInt(null)
                    }
                }
            } catch (ignored: Exception) {
            }
        }
        return sTransparentValue
    }

    /**
     * 获取状态栏的高度。
     */
    fun getStatusbarHeight(context: Context): Int {
        if (sStatusBarHeight == -1) {
            initStatusBarHeight(context)
        }
        return sStatusBarHeight
    }

    private fun initStatusBarHeight(context: Context) {
        val clazz: Class<*>
        var obj: Any? = null
        var field: Field? = null
        try {
            clazz = Class.forName("com.android.internal.R\$dimen")
            obj = clazz.newInstance()
            if (isMeizu) {
                try {
                    field = clazz.getField("status_bar_height_large")
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            if (field == null) {
                field = clazz.getField("status_bar_height")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        if (field != null && obj != null) {
            try {
                val id = field[obj].toString().toInt()
                sStatusBarHeight = context.resources.getDimensionPixelSize(id)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        if (sStatusBarHeight <= 0) {
            if (sVirtualDensity == -1f) {
                sStatusBarHeight = dp2px(context, STATUS_BAR_DEFAULT_HEIGHT_DP)
            } else {
                sStatusBarHeight = (STATUS_BAR_DEFAULT_HEIGHT_DP * sVirtualDensity + 0.5f).toInt()
            }
        }
    }

    fun setVirtualDensity(density: Float) {
        sVirtualDensity = density
    }

    fun setVirtualDensityDpi(densityDpi: Float) {
        sVirtualDensityDpi = densityDpi
    }

    private enum class StatusBarType {
        Default, Miui, Flyme, Android6
    }
}