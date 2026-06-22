package com.wmods.wppenhacer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wmods.wppenhacer.activities.CrashReportActivity
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XposedHelpers
import rikka.material.app.LocaleDelegate.Companion.defaultLocale
import java.io.File
import java.util.Locale

class App : Application() {
    @SuppressLint("ApplySharedPref")
    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashHandler()
        var sharedPreferences: SharedPreferences? = null

        try {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val mode = sharedPreferences.getString("thememode", "0")!!.toInt()
            setThemeMode(mode)
            changeLanguage(this)
        } catch (e: Exception) {
            Utils.showToast("[PREFS] Error accessing app data: ${e.message}")
        }
        if (sharedPreferences != null) {
            try {
                val file = XposedHelpers.getObjectField(sharedPreferences, "file") as File
                file.setReadable(true)
                file.setWritable(true)
                file.setExecutable(true)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun installCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val intent = Intent(this, CrashReportActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.putExtra(CrashReportActivity.EXTRA_CRASH_INFO, buildCrashInfo())
                intent.putExtra(
                    CrashReportActivity.EXTRA_CRASH_TRACE,
                    Log.getStackTraceString(throwable)
                )
                startActivity(intent)
            } catch (_: Throwable) {
            } finally {
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable)
                } else {
                    Runtime.getRuntime().exit(2)
                }
            }
        }
    }

    private fun buildCrashInfo(): String {
        val androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
        val deviceModel = (Build.MANUFACTURER + " " + Build.MODEL).trim { it <= ' ' }
        return "WAE version: " + BuildConfig.VERSION_NAME + "\n" +
                "WAE package: " + packageName + "\n" +
                getString(R.string.crash_android_version) + ": " + androidVersion + "\n" +
                getString(R.string.device_model) + ": " + deviceModel
    }

    fun restartApp(packageWpp: String) {
        val intent = Intent(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART").apply {
            putExtra("PKG", packageWpp)
        }
        sendBroadcast(intent)
    }

    companion object {

        @JvmField
        var instance: App? = null

        @JvmStatic
        fun showRequestStoragePermission(activity: Activity) {
            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(R.string.storage_permission)
            builder.setMessage(R.string.permission_storage)
            builder.setPositiveButton(R.string.allow) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.data = Uri.fromParts("package", activity.packageName, null)
                    activity.startActivity(intent)
                } else {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ),
                        0
                    )
                }
            }
            builder.setNegativeButton(R.string.deny) { dialog, _ -> dialog.dismiss() }
            builder.show()
        }

        @JvmStatic
        fun setThemeMode(mode: Int) {
            when (mode) {
                0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }


        @JvmStatic
        fun changeLanguage(context: Context) {
            val force = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("force_english", false)
            defaultLocale = if (force) Locale.ENGLISH else Locale.getDefault()
            val res = context.resources
            val config = res.configuration
            config.setLocale(defaultLocale)
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
        }

        @JvmStatic
        val waEnhancerFolder: File
            get() {
                val download =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val waEnhancerFolder = File(download, "WaEnhancer")
                if (!waEnhancerFolder.exists()) waEnhancerFolder.mkdirs()
                return waEnhancerFolder
            }

        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
        @JvmStatic
        val isOriginalPackage: Boolean
            get() = BuildConfig.APPLICATION_ID == "com.wmods.wppenhacer"
    }
}
