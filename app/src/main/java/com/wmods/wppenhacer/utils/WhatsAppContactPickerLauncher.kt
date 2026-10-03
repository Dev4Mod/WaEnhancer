package com.wmods.wppenhacer.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

object WhatsAppContactPickerLauncher {
    const val EXTRA_PICKER_MODE = "picker_mode"
    const val EXTRA_CONTACT_MODE = "contact_mode"

    private val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
    private val aboutActivityCandidates = listOf(
        "com.whatsapp.settings.About",
        "com.whatsapp.settings.ui.About"
    )
    private val settingsNotificationsCandidates = listOf(
        "com.whatsapp.SettingsNotifications",
        "com.whatsapp.settings.SettingsNotifications",
        "com.whatsapp.settings.ui.SettingsNotifications"
    )

    @JvmStatic
    fun getInstalledWhatsAppPackages(context: Context): ArrayList<String> {
        val installedPackages = arrayListOf<String>()
        val packageManager = context.packageManager
        for (packageName in whatsappPackages) {
            try {
                packageManager.getPackageInfo(packageName, 0)
                installedPackages.add(packageName)
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        return installedPackages
    }

    @JvmStatic
    fun getPackageLabel(packageName: String): CharSequence =
        if (packageName == "com.whatsapp.w4b") "WhatsApp Business" else "WhatsApp"

    @JvmStatic
    @Throws(Exception::class)
    fun createPickerIntent(
        context: Context,
        packageName: String,
        key: String,
        selectedJids: ArrayList<String>?
    ): Intent = Intent().apply {
        setClassName(packageName, resolveSettingsNotificationsClassName(context, packageName))
        putExtra(EXTRA_CONTACT_MODE, true)
        putExtra("key", key)
        putStringArrayListExtra("contacts", selectedJids?.let(::ArrayList) ?: arrayListOf())
    }

    @JvmStatic
    @Throws(Exception::class)
    fun createAboutPickerIntent(
        context: Context,
        packageName: String,
        key: String,
        selectedJids: ArrayList<String>?
    ): Intent = Intent().apply {
        setClassName(packageName, resolveAboutActivityClassName(context, packageName))
        putExtra(EXTRA_PICKER_MODE, true)
        putExtra("key", key)
        putStringArrayListExtra("contacts", selectedJids?.let(::ArrayList) ?: arrayListOf())
    }

    @Throws(Exception::class)
    private fun resolveAboutActivityClassName(context: Context, packageName: String): String {
        val packageManager = context.packageManager
        for (candidate in aboutActivityCandidates) {
            try {
                packageManager.getActivityInfo(ComponentName(packageName, candidate), 0)
                return candidate
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }

        val packageInfo: PackageInfo =
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        packageInfo.activities?.forEach { activityInfo ->
            val name = activityInfo.name ?: return@forEach
            if (name.endsWith(".settings.About") || name.endsWith(".settings.ui.About") || name.endsWith(
                    ".About"
                )
            ) {
                return name
            }
        }
        throw Exception("Class About not found")
    }

    @Throws(Exception::class)
    private fun resolveSettingsNotificationsClassName(
        context: Context,
        packageName: String
    ): String {
        val packageManager = context.packageManager
        for (candidate in settingsNotificationsCandidates) {
            try {
                packageManager.getActivityInfo(ComponentName(packageName, candidate), 0)
                return candidate
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }

        val packageInfo: PackageInfo =
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        packageInfo.activities?.forEach { activityInfo ->
            val name = activityInfo.name
            if (name != null && name.endsWith("SettingsNotifications")) return name
        }
        throw Exception("Class SettingsNotifications not found")
    }
}
