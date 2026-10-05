package com.wmods.wppenhacer.xposed.core

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.util.Log
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.CrashReportActivity
import com.wmods.wppenhacer.xposed.utils.YukiLog

internal object CrashHandler {
    private var installed = false

    fun install(application: Application, whatsAppVersion: String) {
        if (installed) return
        installed = true

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                YukiLog.log(throwable)
                val isMainThread = Looper.getMainLooper().thread == thread
                if (isMainThread || throwable is Error) {
                    application.startActivity(buildReportIntent(application, whatsAppVersion, throwable))
                }
            } catch (e: Throwable) {
                YukiLog.log(e)
            } finally {
                previousHandler?.uncaughtException(thread, throwable) ?: Runtime.getRuntime().exit(2)
            }
        }
    }

    private fun buildReportIntent(
        application: Application,
        whatsAppVersion: String,
        throwable: Throwable
    ) = Intent().apply {
        component = ComponentName(BuildConfig.APPLICATION_ID, CrashReportActivity::class.java.name)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra(CrashReportActivity.EXTRA_CRASH_INFO, buildCrashInfo(application, whatsAppVersion))
        putExtra(CrashReportActivity.EXTRA_CRASH_TRACE, Log.getStackTraceString(throwable))
    }

    private fun buildCrashInfo(application: Application, whatsAppVersion: String): String {
        val androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        return listOf(
            "${application.getString(R.string.whatsapp_version)}: $whatsAppVersion",
            "${application.getString(R.string.whatsapp_package)}: ${application.packageName}",
            "${application.getString(R.string.wae_version)}: ${BuildConfig.VERSION_NAME}",
            "${application.getString(R.string.crash_android_version)}: $androidVersion",
            "${application.getString(R.string.device_model)}: $deviceModel"
        ).joinToString("\n")
    }
}
