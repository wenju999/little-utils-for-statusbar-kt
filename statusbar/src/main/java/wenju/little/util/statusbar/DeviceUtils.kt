package wenju.little.util.statusbar

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.res.Configuration
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.reflect.Method
import java.util.*
import java.util.regex.Pattern

@SuppressLint("PrivateApi")
object DeviceUtils {
    private const val TAG = "QMUIDeviceHelper"
    private const val KEY_MIUI_VERSION_NAME = "ro.miui.ui.version.name"
    private const val KEY_FLYME_VERSION_NAME = "ro.build.display.id"
    private const val FLYME = "flyme"
    private val MEIZUBOARD = arrayOf("m9", "M9", "mx", "MX")
    private const val POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile"
    private var sMiuiVersionName: String? = null
    private var sFlymeVersionName: String? = null
    private var sIsTabletChecked = false
    private var sIsTabletValue = false
    private val BRAND = Build.BRAND.lowercase(Locale.getDefault())
    private var sTotalMemory: Long = -1
    private var sInnerStorageSize: Long = -1
    private var sExtraStorageSize: Long = -1
    private var sBatteryCapacity = -1.0
    private var isInfoReaded = false
    private fun checkReadInfo() {
        if (isInfoReaded) {
            return
        }
        isInfoReaded = true
        val properties = Properties()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // android 8.0，读取 /system/uild.prop 会报 permission denied
            var fileInputStream: FileInputStream? = null
            try {
                fileInputStream =
                    FileInputStream(File(Environment.getRootDirectory(), "build.prop"))
                properties.load(fileInputStream)
            } catch (e: Exception) {
                Log.e(TAG, "read file error")
            } finally {
                close(fileInputStream)
            }
        }
        var clzSystemProperties: Class<*>? = null
        try {
            clzSystemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = clzSystemProperties.getDeclaredMethod("get", String::class.java)
            // miui
            sMiuiVersionName = getLowerCaseName(properties, getMethod, KEY_MIUI_VERSION_NAME)
            //flyme
            sFlymeVersionName = getLowerCaseName(properties, getMethod, KEY_FLYME_VERSION_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "read SystemProperties error")
        }
    }

    private fun _isTablet(context: Context): Boolean {
        return context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >=
                Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    /**
     * 判断是否为平板设备
     */
    fun isTablet(context: Context): Boolean {
        if (sIsTabletChecked) {
            return sIsTabletValue
        }
        sIsTabletValue = _isTablet(context)
        sIsTabletChecked = true
        return sIsTabletValue
    }

    /**
     * 判断是否是flyme系统
     */
    private val isFlymeValue: OnceReadValue<Void?, Boolean> =
        object : OnceReadValue<Void?, Boolean>() {
            override fun read(param: Void?): Boolean {
                checkReadInfo()
                return !TextUtils.isEmpty(sFlymeVersionName) && sFlymeVersionName!!.contains(FLYME)
            }
        }
    val isFlyme: Boolean
        get() = isFlymeValue[null]!!

    /**
     * 判断是否是MIUI系统
     */
    val isMIUI: Boolean
        get() {
            checkReadInfo()
            return !TextUtils.isEmpty(sMiuiVersionName)
        }
    val isMIUIV5: Boolean
        get() {
            checkReadInfo()
            return "v5" == sMiuiVersionName
        }
    val isMIUIV6: Boolean
        get() {
            checkReadInfo()
            return "v6" == sMiuiVersionName
        }
    val isMIUIV7: Boolean
        get() {
            checkReadInfo()
            return "v7" == sMiuiVersionName
        }
    val isMIUIV8: Boolean
        get() {
            checkReadInfo()
            return "v8" == sMiuiVersionName
        }
    val isMIUIV9: Boolean
        get() {
            checkReadInfo()
            return "v9" == sMiuiVersionName
        }

    fun isFlymeLowerThan(majorVersion: Int): Boolean {
        return isFlymeLowerThan(majorVersion, 0, 0)
    }

    fun isFlymeLowerThan(majorVersion: Int, minorVersion: Int, patchVersion: Int): Boolean {
        checkReadInfo()
        var isLower = false
        if (sFlymeVersionName != null && sFlymeVersionName != "") {
            try {
                val pattern = Pattern.compile("(\\d+\\.){2}\\d")
                val matcher = pattern.matcher(sFlymeVersionName)
                if (matcher.find()) {
                    val versionString = matcher.group()
                    if (versionString.length > 0) {
                        val version = versionString.split("\\.").toTypedArray()
                        if (version.size >= 1) {
                            if (version[0].toInt() < majorVersion) {
                                isLower = true
                            }
                        }
                        if (version.size >= 2 && minorVersion > 0) {
                            if (version[1].toInt() < majorVersion) {
                                isLower = true
                            }
                        }
                        if (version.size >= 3 && patchVersion > 0) {
                            if (version[2].toInt() < majorVersion) {
                                isLower = true
                            }
                        }
                    }
                }
            } catch (ignore: Throwable) {
            }
        }
        return isMeizu && isLower
    }

    private val isMeizuValue: OnceReadValue<Void?, Boolean> =
        object : OnceReadValue<Void?, Boolean>() {
            override fun read(param: Void?): Boolean {
                checkReadInfo()
                return isPhone(MEIZUBOARD) || isFlyme
            }
        }
    val isMeizu: Boolean
        get() = isMeizuValue[null]!!

    /**
     * 判断是否为小米
     */
    private val isXiaomiValue: OnceReadValue<Void?, Boolean> =
        object : OnceReadValue<Void?, Boolean>() {
            override fun read(param: Void?): Boolean {
                return Build.MANUFACTURER.lowercase(Locale.getDefault()) == "xiaomi"
            }
        }
    val isXiaomi: Boolean
        get() = isXiaomiValue[null]!!
    private val isVivoValue: OnceReadValue<Void?, Boolean> =
        object : OnceReadValue<Void?, Boolean>() {
            override fun read(param: Void?): Boolean {
                return BRAND.contains("vivo") || BRAND.contains("bbk")
            }
        }
    val isVivo: Boolean
        get() = isVivoValue[null]!!
    private val isOppoValue: OnceReadValue<Void?, Boolean> =
        object : OnceReadValue<Void?, Boolean>() {
            override fun read(param: Void?): Boolean {
                return BRAND.contains("oppo")
            }
        }
    val isOppo: Boolean
        get() = isOppoValue[null]!!
    private val isHuaweiValue: OnceReadValue<Void?, Boolean> =
        object : OnceReadValue<Void?, Boolean>() {
            override fun read(param: Void?): Boolean {
                return BRAND.contains("huawei") || BRAND.contains("honor")
            }
        }
    val isHuawei: Boolean
        get() = isHuaweiValue[null]!!
    private val isEssentialPhoneValue: OnceReadValue<Void?, Boolean> =
        object : OnceReadValue<Void?, Boolean>() {
            override fun read(param: Void?): Boolean {
                return BRAND.contains("essential")
            }
        }
    val isEssentialPhone: Boolean
        get() = isEssentialPhoneValue[null]!!
    private val isMiuiFullDisplayValue: OnceReadValue<Context, Boolean> =
        object : OnceReadValue<Context, Boolean>() {
            override fun read(param: Context): Boolean {
                return isMIUI && Settings.Global.getInt(
                    param.contentResolver,
                    "force_fsg_nav_bar",
                    0
                ) != 0
            }
        }

    fun isMiuiFullDisplay(context: Context): Boolean {
        return isMiuiFullDisplayValue[context]!!
    }

    private fun isPhone(boards: Array<String>): Boolean {
        checkReadInfo()
        val board = Build.BOARD ?: return false
        for (board1 in boards) {
            if (board == board1) {
                return true
            }
        }
        return false
    }

    fun getTotalMemory(context: Context): Long {
        if (sTotalMemory != -1L) {
            return sTotalMemory
        }
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo)
            sTotalMemory = memoryInfo.totalMem
        }
        return sTotalMemory
    }

    val innerStorageSize: Long
        get() {
            if (sInnerStorageSize != -1L) {
                return sInnerStorageSize
            }
            val dataDir = Environment.getDataDirectory() ?: return 0
            sInnerStorageSize = dataDir.totalSpace
            return sInnerStorageSize
        }

    fun hasExtraStorage(): Boolean {
        return Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
    }

    val extraStorageSize: Long
        get() {
            if (sExtraStorageSize != -1L) {
                return sExtraStorageSize
            }
            if (!hasExtraStorage()) {
                return 0
            }
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.blockCountLong
            sExtraStorageSize = blockSize * availableBlocks
            return sExtraStorageSize
        }

    val totalStorageSize: Long
        get() = innerStorageSize + extraStorageSize

    /**
     * 判断悬浮窗权限（目前主要用户魅族与小米的检测）。
     */
    fun isFloatWindowOpAllowed(context: Context): Boolean {
        val version = Build.VERSION.SDK_INT
        return checkOp(context, 24) // 24 是AppOpsManager.OP_SYSTEM_ALERT_WINDOW 的值，该值无法直接访问
    }

    fun getBatteryCapacity(context: Context?): Double {
        if (sBatteryCapacity != -1.0) {
            return sBatteryCapacity
        }
        val ret: Double
        ret = try {
            val cls = Class.forName(POWER_PROFILE_CLASS)
            val instance = cls.getConstructor(Context::class.java).newInstance(context)
            val method = cls.getMethod("getBatteryCapacity")
            method.invoke(instance) as Double
        } catch (ignore: Exception) {
            -1.0
        }
        sBatteryCapacity = ret
        return sBatteryCapacity
    }

    private fun checkOp(context: Context, op: Int): Boolean {
        val manager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        try {
            val method = manager.javaClass.getDeclaredMethod(
                "checkOp",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val property =
                method.invoke(manager, op, Binder.getCallingUid(), context.packageName) as Int
            return AppOpsManager.MODE_ALLOWED == property
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun getLowerCaseName(p: Properties, get: Method, key: String): String? {
        var name = p.getProperty(key)
        if (name == null) {
            try {
                name = get.invoke(null, key) as String
            } catch (ignored: Exception) {
            }
        }
        if (name != null) name = name.lowercase(Locale.getDefault())
        return name
    }

    fun close(c: Closeable?) {
        if (c != null) {
            try {
                c.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    abstract class OnceReadValue<P, T> {
        @Volatile
        private var isRead = false
        private var cacheValue: T? = null
        operator fun get(param: P): T? {
            if (isRead) {
                return cacheValue
            }
            synchronized(this) {
                if (!isRead) {
                    cacheValue = read(param)
                    isRead = true
                }
            }
            return cacheValue
        }

        protected abstract fun read(param: P): T
    }
}