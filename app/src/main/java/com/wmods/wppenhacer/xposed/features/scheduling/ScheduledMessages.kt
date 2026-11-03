package com.wmods.wppenhacer.xposed.features.scheduling

import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import java.lang.reflect.Method
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Scheduled sending for WhatsApp: text, media and stickers.
 *
 * Text is delivered by calling WhatsApp's own send API from inside its process, so it
 * needs no window and works with the screen off and the device locked. Media and stickers
 * go through the share intent and are committed with `View.performClick()`, which is a
 * method call rather than a synthetic touch event and therefore is not blocked by the
 * keyguard. See [MessageSender].
 *
 * Timing uses two independent paths, because neither alone is dependable here:
 *
 *  1. `AlarmManager` for precision while the process is alive. The alarm has to be an
 *     **implicit** broadcast: [ScheduleAlarmReceiver] is registered at runtime, and a
 *     `PendingIntent` carrying an explicit component resolves against the manifest, where
 *     that receiver does not exist. An explicit intent silently fires into nothing.
 *  2. A sweep on every activity resume, which catches anything the alarm missed - the
 *     process being dead at fire time, the broadcast being dropped, or a ROM ignoring
 *     exact alarms.
 */
class ScheduledMessages(
    classLoader: ClassLoader,
    preferences: SharedPreferences
) : Feature(classLoader, preferences) {

    override fun getPluginName(): String = "Scheduled Messages"

    override fun doHook() {
        val app = FeatureLoader.mApp ?: return
        if (!xprefs.getBoolean("schedule_messages", true)) {
            log("disabled by preference")
            return
        }

        ensureChannel(app)
        registerAlarmReceiver(app)
        catchUpMissed(app)
        hookConversationMenu()
        hookActivityResume()
        hookActivityResult()

        // Resolve the headless send method and install the capture hook.
        // The capture hook must be in place before the user sends any message,
        // so we can learn the 19+ parameter values WhatsApp builds.
        // The hook DSL requires the YukiHook PackageParam context, so we
        // install it here (inline in doHook). The DexKit resolution is
        // synchronous but fast enough (~1s).
        Thread({
            MessageSender.preResolve()
            MessageSender.dumpSendHandlerApi()
            MessageSender.logActionUserApi()
        }, "WAE-resolve-send").start()

        // Install the capture hook inline — the .hook {} DSL needs PackageParam context.
        // The method may not be resolved yet (background thread is still running),
        // so we set a lambda that MessageSender will call once resolution completes.
        // Whichever finishes second (the resolution or the installer setup)
        // triggers the actual hook installation.
        captureHookInstaller = { method: Method ->
            method.hook {
                before {
                    val argsArray = arrayOfNulls<Any>(args.size)
                    for (i in args.indices) {
                        argsArray[i] = args.getOrNull(i)
                    }
                    MessageSender.onSendCaptured(argsArray, instance)
                }
            }
        }
        MessageSender.tryInstallCaptureHook()
    }

    // ------------------------------------------------------------- lifecycle

    private fun ensureChannel(app: Application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val nm = app.getSystemService(android.app.NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID,
                    app.getString(R.string.schedule_channel_name),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    /**
     * Runtime registration. The receiver lives only as long as the WhatsApp process, which
     * is why [sweepDue] exists as a fallback.
     */
    private fun registerAlarmReceiver(app: Application) {
        val filter = IntentFilter(ACTION_FIRE)
        val viaCompat = runCatching {
            ContextCompat.registerReceiver(
                app,
                ScheduleAlarmReceiver(),
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        if (viaCompat.isFailure) {
            log("registerReceiver(compat) failed: ${viaCompat.exceptionOrNull()?.message}")
            // Never fall back to a flag-less registration on 33+: that receiver
            // would be exported, and any app could fire ACTION_FIRE to force a
            // scheduled message out early.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching {
                    app.registerReceiver(
                        ScheduleAlarmReceiver(),
                        filter,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                }.onFailure { log("registerReceiver(not-exported) failed: ${it.message}") }
            } else {
                runCatching { app.registerReceiver(ScheduleAlarmReceiver(), filter) }
                    .onFailure { log("registerReceiver(plain) failed: ${it.message}") }
            }
        }
    }

    /**
     * Anything that came due while the process was dead is sent now; anything still ahead
     * is re-armed.
     */
    private fun catchUpMissed(app: Application) {
        val store = ScheduledMessageStore.getInstance(app)
        val now = System.currentTimeMillis()

        val stale = store.interrupted()
        if (stale.isNotEmpty()) log("retrying ${stale.size} interrupted schedule(s)")
        stale.forEach { interrupted ->
            store.updateState(interrupted.id, DeliveryState.PENDING)
            fire(app, interrupted.id)
        }

        val pending = store.pending()
        if (pending.isNotEmpty()) log("re-arming ${pending.size} pending schedule(s)")
        pending.forEach { message ->
            if (message.triggerAt <= now) fire(app, message.id) else arm(app, message)
        }
    }

    /** Safety net: deliver anything overdue, called from the activity-resume listener. */
    private fun sweepDue(app: Application) {
        val now = System.currentTimeMillis()
        val throttle = now - lastSweepAt
        if (throttle < SWEEP_INTERVAL_MS) return
        lastSweepAt = now

        runCatching {
            val store = ScheduledMessageStore.getInstance(app)
            val due = store.pending().filter { it.triggerAt <= now }
            if (due.isNotEmpty()) log("sweep firing ${due.size} overdue schedule(s)")
            due.forEach { fire(app, it.id) }
        }.onFailure { log("sweep failed: ${it.message}") }
    }

    private fun fire(app: Application, id: Long) {
        // Implicit broadcast: matches the runtime-registered receiver's IntentFilter.
        val intent = Intent(ACTION_FIRE)
            .setPackage(app.packageName)
            .putExtra(EXTRA_ID, id)
        app.sendBroadcast(intent)
    }

    private fun arm(context: Context, message: ScheduledMessage) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = firePendingIntent(context, message.id)

        runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(message.triggerAt, null), pi)
            log("armed id=${message.id} at=${message.triggerAt} (alarmClock)")
        }.onFailure { clockError ->
            log("setAlarmClock failed (${clockError.message}), trying inexact")
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, message.triggerAt, pi)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, message.triggerAt, pi)
                }
                log("armed id=${message.id} at=${message.triggerAt} (inexact)")
            }.onFailure { log("arm failed for id=${message.id}: ${it.message}") }
        }
    }

    /**
     * Implicit, deliberately: no component class. See the class comment.
     */
    private fun firePendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(ACTION_FIRE)
            .setPackage(context.packageName)
            .setData(Uri.parse("wae-schedule://fire/$id"))
            .putExtra(EXTRA_ID, id)

        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ----------------------------------------------------------------- hooks

    private fun hookConversationMenu() {
        val menuMethod = runCatching {
            Unobfuscator.loadOnCreatedMenuConversation(classLoader)
        }.getOrNull()

        if (menuMethod == null) {
            log("!! could not resolve the conversation options menu - the menu entry will not appear")
            return
        }

        menuMethod.hook {
            after {
                val menu = args.getOrNull(0) as? Menu ?: return@after

                if (menu.findItem(MENU_ID) == null) {
                    menu.add(0, MENU_ID, 0, R.string.schedule_message).apply {
                        setOnMenuItemClickListener {
                            val jid = WppCore.getCurrentUserJid()?.userRawString
                            val activity = WppCore.getCurrentActivity()
                            if (jid == null || activity == null) {
                                log("menu clicked but jid/activity was null (jid=$jid)")
                                Utils.showToast(
                                    Utils.getString(R.string.schedule_no_chat),
                                    Toast.LENGTH_SHORT
                                )
                                false
                            } else {
                                log("menu clicked for $jid")
                                showScheduleDialog(activity, jid)
                                true
                            }
                        }
                    }
                }

                if (menu.findItem(MENU_ID_MANAGE) == null) {
                    menu.add(0, MENU_ID_MANAGE, 0, R.string.schedule_manage).apply {
                        setOnMenuItemClickListener {
                            val activity = WppCore.getCurrentActivity()
                            if (activity == null) {
                                Utils.showToast(
                                    Utils.getString(R.string.schedule_no_chat),
                                    Toast.LENGTH_SHORT
                                )
                                false
                            } else {
                                log("opening the schedule list")
                                showScheduledList(activity)
                                true
                            }
                        }
                    }
                }

                log("conversation menu entries installed")
            }
        }
    }

    private fun hookActivityResume() {
        val app = FeatureLoader.mApp ?: return
        WppCore.addListenerActivity { activity, type ->
            if (type == WppCore.ActivityChangeState.ChangeType.RESUMED) {
                MessageSender.PendingUiSend.onActivityResumed(activity)
                sweepDue(app)
            }
        }
    }

    /**
     * Catches the document-picker result for the attach button. WhatsApp's activities call
     * through to `super.onActivityResult`, which is what makes a single global hook enough.
     */
    private fun hookActivityResult() {
        runCatching {
            Activity::class.java.resolve().firstMethod {
                name = "onActivityResult"
                parameters(
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                    Intent::class.java
                )
            }.hook {
                after {
                    val requestCode = args.getOrNull(0) as? Int ?: return@after
                    if (requestCode != REQ_PICK_MEDIA) return@after

                    val resultCode = args.getOrNull(1) as? Int ?: return@after
                    val data = args.getOrNull(2) as? Intent
                    val draft = awaitingPick ?: return@after
                    awaitingPick = null

                    if (resultCode != Activity.RESULT_OK || data?.data == null) return@after

                    val act = instance as? Activity ?: return@after
                    val uri = data.data!!
                    runCatching {
                        act.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    draft.mediaUri = uri.toString()
                    draft.mime = act.contentResolver.getType(uri)
                    log("attached ${draft.mediaUri} (${draft.mime})")
                    Utils.showToast(
                        act.getString(R.string.schedule_attached, uri.lastPathSegment ?: ""),
                        Toast.LENGTH_SHORT
                    )
                }
            }
        }.onFailure { log("onActivityResult hook failed: ${it.message}") }
    }

    // -------------------------------------------------------------------- UI

    private class Draft(val jid: String, val name: String) {
        var mediaUri: String? = null
        var mime: String? = null
        var triggerAt: Long = 0L
    }

    private fun showScheduleDialog(activity: Activity, jid: String) {
        val store = ScheduledMessageStore.getInstance(activity)
        // Chats can be addressed by LID (...@lid) rather than a phone number; strip the
        // server part so notifications read as a number instead of a raw JID.
        val fallbackName = jid.substringBefore('@')
        val draft = Draft(jid, WppCore.getCurrentChatTitle() ?: fallbackName)

        // Default: one hour from now, on the minute.
        val start = System.currentTimeMillis() + 60 * 60 * 1000L
        draft.triggerAt = start - start % 60_000L

        val ctx = activity
        val pad = Utils.dipToPixels(16)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val textInput = EditText(ctx).apply {
            hint = ctx.getString(R.string.schedule_hint_text)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
        }
        root.addView(textInput)

        val attach = Button(ctx).apply {
            text = ctx.getString(R.string.schedule_attach)
            setOnClickListener {
                awaitingPick = draft
                val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }
                runCatching { activity.startActivityForResult(pick, REQ_PICK_MEDIA) }
                    .onFailure { log("could not open the file picker: ${it.message}") }
            }
        }
        root.addView(attach)

        val whenLabel = TextView(ctx).apply {
            setPadding(0, pad, 0, 0)
            text = ctx.getString(R.string.schedule_when)
        }
        root.addView(whenLabel)

        val timeLabel = TextView(ctx)
        root.addView(timeLabel)

        fun renderTime() {
            timeLabel.text = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT
            ).format(draft.triggerAt)
        }
        renderTime()

        val buttons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, pad / 2, 0, 0)
        }

        buttons.addView(
            Button(ctx).apply {
                text = ctx.getString(R.string.schedule_date)
                setOnClickListener {
                    val c = Calendar.getInstance().apply { timeInMillis = draft.triggerAt }
                    DatePickerDialog(
                        ctx,
                        { _, y, m, d ->
                            c.set(Calendar.YEAR, y)
                            c.set(Calendar.MONTH, m)
                            c.set(Calendar.DAY_OF_MONTH, d)
                            draft.triggerAt = c.timeInMillis
                            renderTime()
                        },
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        buttons.addView(
            Button(ctx).apply {
                text = ctx.getString(R.string.schedule_time)
                setOnClickListener {
                    val c = Calendar.getInstance().apply { timeInMillis = draft.triggerAt }
                    TimePickerDialog(
                        ctx,
                        { _, h, min ->
                            c.set(Calendar.HOUR_OF_DAY, h)
                            c.set(Calendar.MINUTE, min)
                            c.set(Calendar.SECOND, 0)
                            draft.triggerAt = c.timeInMillis
                            renderTime()
                        },
                        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true
                    ).show()
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        root.addView(buttons)

        val dialog = AlertDialogWpp(ctx)
        dialog.setTitle(ctx.getString(R.string.schedule_message))
        dialog.setView(root)
        dialog.setPositiveButton(ctx.getString(R.string.schedule_save)) { _, _ ->
            val text = textInput.text?.toString().orEmpty()
            val uri = draft.mediaUri

            if (text.isBlank() && uri == null) {
                Utils.showToast(Utils.getString(R.string.schedule_empty), Toast.LENGTH_SHORT)
                return@setPositiveButton
            }
            if (draft.triggerAt <= System.currentTimeMillis()) {
                Utils.showToast(Utils.getString(R.string.schedule_past), Toast.LENGTH_SHORT)
                return@setPositiveButton
            }

            val kind = when {
                uri == null -> MessageKind.TEXT
                draft.mime?.startsWith("image/webp") == true -> MessageKind.STICKER
                else -> MessageKind.MEDIA
            }

            val message = ScheduledMessage(
                jid = draft.jid,
                displayName = draft.name,
                kind = kind,
                text = if (kind == MessageKind.TEXT) text else text.ifBlank { null },
                mediaUri = uri,
                mime = draft.mime,
                triggerAt = draft.triggerAt
            )

            try {
                val id = store.insert(message)
                if (id <= 0L) {
                    // insert() returns -1 on failure; arming an id of -1 would
                    // schedule an alarm that can never resolve to a row.
                    log("save failed: insert returned $id")
                    Utils.showToast(
                        Utils.getString(R.string.schedule_save_failed),
                        Toast.LENGTH_LONG
                    )
                    return@setPositiveButton
                }
                log("saved id=$id kind=$kind jid=${message.jid} at=${message.triggerAt}")
                arm(ctx, message.copy(id = id))
                Utils.showToast(
                    ctx.getString(R.string.schedule_saved, message.preview()),
                    Toast.LENGTH_SHORT
                )
            } catch (t: Throwable) {
                log("save failed: ${t.message}")
                YukiLog.log(t)
                Utils.showToast("Could not save: ${t.message}", Toast.LENGTH_LONG)
            }
        }
        dialog.setNegativeButton(ctx.getString(R.string.cancel), null)
        dialog.show()
    }

    // ------------------------------------------------------- list and delete

    /**
     * Lists every schedule — any chat, any state — and lets each one be deleted.
     *
     * Deleting removes the row *and* cancels the pending alarm. Cancelling only the
     * row would leave an alarm armed for an id that no longer resolves, which is
     * harmless but leaves dead work behind; cancelling only the alarm would let the
     * next process start re-arm the row.
     */
    private fun showScheduledList(activity: Activity) {
        val ctx = activity
        val store = ScheduledMessageStore.getInstance(ctx)
        val pad = Utils.dipToPixels(12)

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun refresh() {
            container.removeAllViews()
            val rows = runCatching { store.all() }.getOrDefault(emptyList())

            if (rows.isEmpty()) {
                container.addView(TextView(ctx).apply {
                    text = ctx.getString(R.string.schedule_list_empty)
                })
                return
            }

            container.addView(TextView(ctx).apply {
                text = ctx.getString(R.string.schedule_list_hint)
                textSize = 12f
                setPadding(0, 0, 0, pad)
            })

            rows.forEach { message ->
                container.addView(
                    buildScheduleRow(ctx, message) {
                        confirmDelete(ctx, message) {
                            deleteSchedule(ctx, message)
                            refresh()
                        }
                    }
                )
            }
        }

        refresh()

        val scroll = ScrollView(ctx).apply { addView(container) }
        val root = FrameLayout(ctx).apply {
            addView(
                scroll,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Utils.dipToPixels(340)
                )
            )
        }

        val dialog = AlertDialogWpp(ctx)
        dialog.setTitle(ctx.getString(R.string.schedule_manage))
        dialog.setView(root)
        dialog.setNegativeButton(ctx.getString(R.string.schedule_close), null)
        dialog.show()
    }

    private fun buildScheduleRow(
        ctx: Context,
        message: ScheduledMessage,
        onTap: () -> Unit
    ): View {
        val pad = Utils.dipToPixels(8)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, pad, 0, pad)
            isClickable = true
            setOnClickListener { onTap() }

            addView(TextView(ctx).apply {
                text = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT
                ).format(Date(message.triggerAt))
                setTypeface(typeface, Typeface.BOLD)
            })

            addView(TextView(ctx).apply {
                text = message.displayName
                textSize = 13f
            })

            addView(TextView(ctx).apply {
                text = message.preview()
                textSize = 12f
            })

            addView(TextView(ctx).apply {
                text = stateLabel(ctx, message)
                textSize = 11f
                setPadding(0, pad / 2, 0, 0)
            })
        }
    }

    private fun stateLabel(ctx: Context, message: ScheduledMessage): String {
        val label = ctx.getString(
            when (message.state) {
                DeliveryState.PENDING -> R.string.schedule_state_pending
                DeliveryState.SENDING -> R.string.schedule_state_sending
                DeliveryState.SENT -> R.string.schedule_state_sent
                DeliveryState.FAILED -> R.string.schedule_state_failed
            }
        )

        val error = message.lastError
        return if (message.state == DeliveryState.FAILED && !error.isNullOrBlank()) {
            "$label · $error"
        } else {
            label
        }
    }

    private fun confirmDelete(ctx: Activity, message: ScheduledMessage, onConfirm: () -> Unit) {
        val dialog = AlertDialogWpp(ctx)
        dialog.setTitle(ctx.getString(R.string.schedule_delete))
        dialog.setMessage(ctx.getString(R.string.schedule_delete_confirm))
        dialog.setPositiveButton(ctx.getString(R.string.schedule_delete)) { _, _ -> onConfirm() }
        dialog.setNegativeButton(ctx.getString(R.string.cancel), null)
        dialog.show()
    }

    private fun deleteSchedule(context: Context, message: ScheduledMessage) {
        runCatching { ScheduledMessageStore.getInstance(context).delete(message.id) }
            .onFailure { log("delete failed for id=${message.id}: ${it.message}") }

        cancelAlarm(context, message.id)
        log("deleted schedule id=${message.id}")
        Utils.showToast(Utils.getString(R.string.schedule_deleted), Toast.LENGTH_SHORT)
    }

    /** Cancels the pending alarm so a deleted row cannot still fire. */
    private fun cancelAlarm(context: Context, id: Long) {
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return@runCatching
            am.cancel(firePendingIntent(context, id))
        }.onFailure { log("cancel alarm failed for id=$id: ${it.message}") }
    }

    companion object {
        const val ACTION_FIRE = "com.wmods.wae.SCHEDULE_FIRE"
        const val EXTRA_ID = "schedule_id"
        const val CHANNEL_ID = "wae_scheduled_messages"
        const val NOTIFICATION_BASE = 7300

        private const val MENU_ID = 0x5741
        private const val MENU_ID_MANAGE = 0x5743
        private const val REQ_PICK_MEDIA = 0x5742
        private const val SWEEP_INTERVAL_MS = 20_000L

        @Volatile
        private var lastSweepAt = 0L

        /** The draft waiting for a document-picker result. */
        @Volatile
        private var awaitingPick: Draft? = null

        /**
         * Lambda that installs a hook on the given Method using the YukiHook DSL.
         * Set in [doHook] where PackageParam context is available, called by
         * [MessageSender] after the send method has been resolved.
         */
        @Volatile
        var captureHookInstaller: ((Method) -> Unit)? = null
    }
}
