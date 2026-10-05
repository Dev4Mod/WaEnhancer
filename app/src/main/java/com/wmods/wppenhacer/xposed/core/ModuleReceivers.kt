package com.wmods.wppenhacer.xposed.core

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.utils.Utils

internal object ModuleReceivers {
    private const val ACTION_RESTART = "${BuildConfig.APPLICATION_ID}.WHATSAPP.RESTART"
    private const val ACTION_CHECK_WPP = "${BuildConfig.APPLICATION_ID}.CHECK_WPP"
    private const val ACTION_MANUAL_RESTART = "${BuildConfig.APPLICATION_ID}.MANUAL_RESTART"
    private const val ACTION_RECEIVER_WPP = "${BuildConfig.APPLICATION_ID}.RECEIVER_WPP"

    @SuppressLint("WrongConstant")
    fun register(app: Application) {
        register(app, ACTION_RESTART) { context, intent ->
            if (context.packageName == intent.getStringExtra("PKG")) restart(context)
        }
        register(app, ACTION_CHECK_WPP) { context, _ -> sendEnabledBroadcast(context) }
        register(app, ACTION_MANUAL_RESTART) { _, _ -> WppCore.setPrivBoolean("need_restart", true) }
    }

    fun sendEnabledBroadcast(context: Context) {
        try {
            val intent = Intent(ACTION_RECEIVER_WPP).apply {
                putExtra(
                    "VERSION",
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                )
                putExtra("PKG", context.packageName)
                setPackage(BuildConfig.APPLICATION_ID)
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {
        }
    }

    private fun register(
        app: Application,
        action: String,
        handler: (Context, Intent) -> Unit
    ) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = handler(context, intent)
        }
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun restart(context: Context) {
        val appName = context.packageManager.getApplicationLabel(context.applicationInfo)
        Toast.makeText(
            context, "${context.getString(R.string.rebooting)} $appName...", Toast.LENGTH_SHORT
        ).show()
        if (!Utils.doRestart(context)) {
            Toast.makeText(context, "Unable to rebooting $appName", Toast.LENGTH_SHORT).show()
        }
    }
}
