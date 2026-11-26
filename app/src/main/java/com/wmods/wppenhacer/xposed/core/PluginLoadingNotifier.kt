package com.wmods.wppenhacer.xposed.core

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.concurrent.CompletableFuture

object PluginLoadingNotifier {

    private const val CHANNEL_ID = "wae_plugin_loading"
    private const val NOTIFICATION_ID = 0x57414501
    private const val MIN_UPDATE_INTERVAL_MS = 250L
    private const val TIMEOUT_MS = 3 * 60 * 1000L

    private val lock = Any()
    @Volatile
    private var active = false
    private var total = 0
    private var loaded = 0
    private var lastUpdateAt = 0L
    private var icon: IconCompat? = null

    fun start(): Boolean = synchronized(lock) {
        val manager = NotificationManagerCompat.from(Utils.application)
        if (!manager.areNotificationsEnabled()) return false
        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    Utils.getString(R.string.plugin_loading_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }.onFailure { return false }
        active = true
        total = 0
        loaded = 0
        post(null)
        true
    }

    fun setTotal(count: Int): Unit = synchronized(lock) {
        if (!active) return
        total = count
        post(null)
    }

    fun onPluginLoaded(name: String): Unit = synchronized(lock) {
        if (!active) return
        loaded++
        val now = SystemClock.elapsedRealtime()
        if (now - lastUpdateAt < MIN_UPDATE_INTERVAL_MS && loaded < total) return
        post(name)
    }

    fun finish(): Unit = synchronized(lock) {
        active = false
        runCatching { NotificationManagerCompat.from(Utils.application).cancel(NOTIFICATION_ID) }
    }

    fun dismissLeftover() {
        if (active) return
        CompletableFuture.runAsync {
            synchronized(lock) {
                if (active) return@runAsync
                runCatching {
                    NotificationManagerCompat.from(Utils.application).cancel(NOTIFICATION_ID)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(lastPlugin: String?) {
        lastUpdateAt = SystemClock.elapsedRealtime()
        runCatching {
            val context = Utils.application
            val text = if (total > 0) {
                context.getString(R.string.plugin_loading_progress, loaded.coerceAtMost(total), total)
            } else {
                context.getString(R.string.plugin_loading_starting)
            }
            val smallIcon = icon ?: IconCompat.createWithBitmap(
                DesignUtils.drawableToBitmap(DesignUtils.getDrawable(R.drawable.wae))
            ).also { icon = it }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle(context.getString(R.string.plugin_loading_title))
                .setContentText(text)
                .setSubText(lastPlugin)
                .setProgress(total, loaded.coerceAtMost(total), total == 0)
                .setOngoing(true)
                .setTimeoutAfter(TIMEOUT_MS)
                .setOnlyAlertOnce(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
