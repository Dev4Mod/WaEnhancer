package com.wmods.wppenhacer.xposed.features.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.utils.YukiLog

/**
 * Fires when a scheduled message is due.
 *
 * Runs inside the WhatsApp process, so the work happens on a background thread while
 * `goAsync()` keeps the receiver alive. If the process is killed mid-send the row stays
 * in SENDING and [ScheduledMessages] retries it the next time the process starts.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduledMessages.ACTION_FIRE) return
        val id = intent.getLongExtra(ScheduledMessages.EXTRA_ID, -1L)
        if (id <= 0L) return

        val appContext = context.applicationContext
        val pending = goAsync()

        Thread({
            try {
                deliver(appContext, id)
            } catch (t: Throwable) {
                YukiLog.log(t)
                runCatching {
                    ScheduledMessageStore.getInstance(appContext)
                        .updateState(id, DeliveryState.FAILED, t.message ?: "unexpected error")
                }
            } finally {
                pending.finish()
            }
        }, "WAE-schedule-$id").start()
    }

    private fun deliver(context: Context, id: Long) {
        val store = ScheduledMessageStore.getInstance(context)
        val message = store.get(id) ?: return

        // Atomic claim: only one of the alarm broadcast and the resume sweep can
        // win this, so a message that is due is never sent twice.
        if (!store.claim(id)) return

        val result = MessageSender.send(context, message)

        if (result.ok) {
            store.updateState(id, DeliveryState.SENT)
            notify(context, id, context.getString(R.string.schedule_sent_title),
                context.getString(R.string.schedule_sent_body, message.displayName), false)
        } else {
            store.updateState(id, DeliveryState.FAILED, result.error)
            notify(context, id, context.getString(R.string.schedule_failed_title),
                context.getString(R.string.schedule_failed_body, message.displayName, result.error ?: "unknown"),
                true)
        }
    }

    private fun notify(context: Context, id: Long, title: String, body: String, error: Boolean) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        ScheduledMessages.CHANNEL_ID,
                        context.getString(R.string.schedule_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            val icon = com.wmods.wppenhacer.xposed.utils.Utils.getID("ic_notification", "drawable")
                .takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
            val n = NotificationCompat.Builder(context, ScheduledMessages.CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(if (error) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .build()
            nm.notify(ScheduledMessages.NOTIFICATION_BASE + id.toInt(), n)
        }
    }
}
