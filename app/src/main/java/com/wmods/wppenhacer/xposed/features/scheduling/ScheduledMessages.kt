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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
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
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
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

                    if (resultCode != Activity.RESULT_OK) return@after

                    val act = instance as? Activity ?: return@after

                    // A multi-select result arrives in clipData; a single pick
                    // arrives in data. Accept either, and de-duplicate.
                    val picked = ArrayList<Uri>()
                    val clip = data?.clipData
                    if (clip != null) {
                        for (i in 0 until clip.itemCount) {
                            clip.getItemAt(i)?.uri?.let { picked.add(it) }
                        }
                    }
                    data?.data?.let { single -> if (picked.none { it == single }) picked.add(single) }
                    if (picked.isEmpty()) return@after

                    picked.forEach { uri ->
                        runCatching {
                            act.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        val value = uri.toString()
                        if (value !in draft.mediaUris) draft.mediaUris.add(value)
                    }

                    if (draft.mime == null) {
                        draft.mime = act.contentResolver.getType(picked.first())
                    }

                    log("picked ${picked.size} file(s), ${draft.mediaUris.size} attached in total")
                    // Redraw the dialog so the attachments are visible and removable.
                    draft.onAttachmentChanged?.invoke()
                    Utils.showToast(
                        act.getString(
                            R.string.schedule_attached,
                            attachmentName(act, picked.first().toString())
                        ),
                        Toast.LENGTH_SHORT
                    )
                }
            }
        }.onFailure { log("onActivityResult hook failed: ${it.message}") }
    }

    // -------------------------------------------------------------------- UI

    private class Draft(val jid: String, val name: String) {
        /** Every attachment, in the order it was picked. */
        val mediaUris = ArrayList<String>()

        /** Mime of the first attachment; drives the share intent's type. */
        var mime: String? = null
        var triggerAt: Long = 0L

        /**
         * Invoked when the attachment list changes. The picker result arrives
         * through `onActivityResult`, long after the dialog was built, so the
         * dialog has to be told to redraw its preview row.
         */
        var onAttachmentChanged: (() -> Unit)? = null
    }

    /**
     * Creates a schedule, or edits [existing] when one is supplied.
     *
     * Both paths share one form so a schedule is validated in a single place, and
     * an edit re-arms the alarm because the trigger time may have moved.
     */
    /**
     * Shows a dialog wider than the framework default.
     *
     * [AlertDialogWpp.show] offers no control over the window, so the Dialog is
     * created directly and laid out against the screen. Falls back to a plain show
     * if the wrapper refuses to hand back a Dialog.
     */
    private fun showWideDialog(dialog: AlertDialogWpp, widthFraction: Float = 0.94f) {
        val created = runCatching { dialog.create() }.getOrNull()
        if (created == null) {
            dialog.show()
            return
        }

        runCatching { created.show() }.onFailure {
            log("could not show the dialog: ${it.message}")
            return
        }

        runCatching {
            val metrics = created.context.resources.displayMetrics
            created.window?.setLayout(
                (metrics.widthPixels * widthFraction).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }.onFailure { log("could not size the dialog: ${it.message}") }
    }

    /**
     * Friendly file name for a content URI.
     *
     * The path segment of a document URI is an opaque id like `image:1001135073`,
     * so ask the provider for the display name and fall back only if it has none.
     */
    private fun attachmentName(ctx: Context, uri: String): String {
        val parsed = Uri.parse(uri)
        val fromProvider = runCatching {
            ctx.contentResolver
                .query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()

        return fromProvider ?: parsed.lastPathSegment ?: uri
    }

    /**
     * Thumbnail with a close badge.
     *
     * The container is sized to the thumbnail rather than filling the row, which is
     * what keeps the badge on the image's own top-right corner instead of drifting
     * to the edge of the dialog.
     */
    private fun buildMediaPreview(
        ctx: Context,
        uri: Uri,
        isVideo: Boolean,
        onRemove: () -> Unit
    ): View {
        val size = Utils.dipToPixels(96)
        val gap = Utils.dipToPixels(8)

        val frame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = gap }
        }

        frame.addView(
            ImageView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(size, size)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(loadThumbnail(ctx, uri, isVideo, size))
            }
        )
        frame.addView(closeBadge(ctx, onRemove))
        return frame
    }

    /** Small circular ✕ pinned to the top-right corner of a preview. */
    private fun closeBadge(ctx: Context, onRemove: () -> Unit): View {
        val diameter = Utils.dipToPixels(26)
        val margin = Utils.dipToPixels(4)

        return TextView(ctx).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                diameter,
                diameter,
                Gravity.TOP or Gravity.END
            ).apply { setMargins(0, margin, margin, 0) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#B3000000"))
            }
            isClickable = true
            setOnClickListener { onRemove() }
        }
    }

    /** Row for a non-media attachment: the file name plus a close badge. */
    private fun buildFileChip(ctx: Context, name: String, onRemove: () -> Unit): View {
        val pad = Utils.dipToPixels(8)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad / 2, pad / 2, pad / 2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = Utils.dipToPixels(8) }
            background = GradientDrawable().apply {
                cornerRadius = Utils.dipToPixels(6).toFloat()
                setColor(Color.parseColor("#33888888"))
            }

            addView(TextView(ctx).apply {
                text = name
                textSize = 13f
                maxLines = 1
                maxWidth = Utils.dipToPixels(160)
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            })
            addView(closeBadge(ctx, onRemove))
        }
    }

    /**
     * Decodes a small bitmap for the preview.
     *
     * Images are sampled down rather than decoded at full size — a camera photo is
     * far larger than a 128dp thumbnail and would cost memory for no visible gain.
     */
    private fun loadThumbnail(ctx: Context, uri: Uri, isVideo: Boolean, targetPx: Int): Bitmap? =
        runCatching {
            if (isVideo) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(ctx, uri)
                    retriever.getFrameAtTime(0)
                } finally {
                    runCatching { retriever.release() }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }

                var sample = 1
                while (bounds.outWidth / sample > targetPx * 2) sample *= 2

                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }
        }.getOrNull()

    private fun showScheduleDialog(
        activity: Activity,
        jid: String,
        existing: ScheduledMessage? = null,
        onSaved: (() -> Unit)? = null
    ) {
        val store = ScheduledMessageStore.getInstance(activity)

        // Prefer the chat's display name; the JID's local part is only a fallback.
        val draft = Draft(jid, displayNameFor(jid))

        if (existing != null) {
            draft.triggerAt = existing.triggerAt
            draft.mediaUris.addAll(existing.mediaUris)
            draft.mime = existing.mime
        } else {
            // Default: one hour from now, on the minute.
            val start = System.currentTimeMillis() + 60 * 60 * 1000L
            draft.triggerAt = start - start % 60_000L
        }

        val ctx = activity
        val pad = Utils.dipToPixels(16)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val textInput = EditText(ctx).apply {
            hint = ctx.getString(R.string.schedule_hint_text)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            existing?.text?.let { setText(it) }
        }
        root.addView(textInput)

        val attach = Button(ctx).apply {
            text = ctx.getString(R.string.schedule_attach)
            setOnClickListener {
                awaitingPick = draft
                val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
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

        // One preview per attachment, in a horizontally scrolling row. Images and
        // videos get a thumbnail with a close badge on the thumbnail itself;
        // anything else gets a file chip.
        val attachmentRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val attachmentScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, pad / 2, 0, 0)
            addView(attachmentRow)
        }
        root.addView(attachmentScroll)

        var renderAttachment: () -> Unit = {}

        fun removeAttachmentAt(index: Int) {
            if (index !in draft.mediaUris.indices) return
            draft.mediaUris.removeAt(index)
            draft.mime = draft.mediaUris.firstOrNull()?.let { value ->
                runCatching { ctx.contentResolver.getType(Uri.parse(value)) }.getOrNull()
            }
            renderAttachment()
        }

        renderAttachment = {
            attachmentRow.removeAllViews()

            if (draft.mediaUris.isEmpty()) {
                attachmentRow.addView(TextView(ctx).apply {
                    text = ctx.getString(R.string.schedule_no_attachment)
                    textSize = 12f
                })
            } else {
                draft.mediaUris.forEachIndexed { index, uriString ->
                    val uri = Uri.parse(uriString)
                    val mime = runCatching { ctx.contentResolver.getType(uri) }.getOrNull()

                    attachmentRow.addView(
                        when {
                            mime?.startsWith("image/") == true ->
                                buildMediaPreview(ctx, uri, isVideo = false) {
                                    removeAttachmentAt(index)
                                }

                            mime?.startsWith("video/") == true ->
                                buildMediaPreview(ctx, uri, isVideo = true) {
                                    removeAttachmentAt(index)
                                }

                            else -> buildFileChip(ctx, attachmentName(ctx, uriString)) {
                                removeAttachmentAt(index)
                            }
                        }
                    )
                }
            }
        }

        draft.onAttachmentChanged = { renderAttachment() }
        renderAttachment()

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
        dialog.setTitle(
            ctx.getString(
                if (existing == null) R.string.schedule_message else R.string.schedule_edit_title
            )
        )
        dialog.setView(root)
        dialog.setPositiveButton(
            ctx.getString(
                if (existing == null) R.string.schedule_save else R.string.schedule_save_changes
            )
        ) { _, _ ->
            val text = textInput.text?.toString().orEmpty()
            val uris = draft.mediaUris.toList()

            if (text.isBlank() && uris.isEmpty()) {
                Utils.showToast(Utils.getString(R.string.schedule_empty), Toast.LENGTH_SHORT)
                return@setPositiveButton
            }
            if (draft.triggerAt <= System.currentTimeMillis()) {
                Utils.showToast(Utils.getString(R.string.schedule_past), Toast.LENGTH_SHORT)
                return@setPositiveButton
            }

            val kind = when {
                uris.isEmpty() -> MessageKind.TEXT
                uris.size == 1 && draft.mime?.startsWith("image/webp") == true -> MessageKind.STICKER
                else -> MessageKind.MEDIA
            }

            val message = ScheduledMessage(
                id = existing?.id ?: 0L,
                jid = draft.jid,
                displayName = draft.name,
                kind = kind,
                text = if (kind == MessageKind.TEXT) text else text.ifBlank { null },
                mediaUris = uris,
                mime = draft.mime,
                triggerAt = draft.triggerAt
            )

            try {
                if (existing != null) {
                    store.update(message)
                    // The trigger time may have moved, so replace the armed alarm.
                    cancelAlarm(ctx, existing.id)
                    arm(ctx, message)
                    log("updated id=${existing.id} at=${message.triggerAt}")
                    Utils.showToast(
                        Utils.getString(R.string.schedule_updated),
                        Toast.LENGTH_SHORT
                    )
                    onSaved?.invoke()
                } else {
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
                    log(
                        "saved id=$id kind=$kind jid=${message.jid} " +
                            "name=${message.displayName} at=${message.triggerAt}"
                    )
                    arm(ctx, message.copy(id = id))
                    Utils.showToast(
                        ctx.getString(R.string.schedule_saved, message.preview()),
                        Toast.LENGTH_SHORT
                    )
                    onSaved?.invoke()
                }
            } catch (t: Throwable) {
                log("save failed: ${t.message}")
                YukiLog.log(t)
                Utils.showToast("Could not save: ${t.message}", Toast.LENGTH_LONG)
            }
        }
        dialog.setNegativeButton(ctx.getString(R.string.cancel), null)
        showWideDialog(dialog)
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
                        onScheduleTapped(ctx, message) { refresh() }
                    }
                )
            }
        }

        refresh()

        val scroll = ScrollView(ctx).apply { addView(container) }
        // Taller than the old fixed 340dp so more of the list is visible at once.
        val listHeight = (ctx.resources.displayMetrics.heightPixels * 0.62f).toInt()
        val root = FrameLayout(ctx).apply {
            addView(
                scroll,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, listHeight)
            )
        }

        val dialog = AlertDialogWpp(ctx)
        dialog.setTitle(ctx.getString(R.string.schedule_manage))
        dialog.setView(root)
        dialog.setNegativeButton(ctx.getString(R.string.schedule_close), null)
        showWideDialog(dialog)
    }

    /**
     * Tapping a row opens the actions that make sense for its state.
     *
     * A PENDING schedule can be edited or deleted. Anything already sent or failed
     * can only be removed from the list — editing it would resurrect a delivery
     * that already happened.
     */
    private fun onScheduleTapped(ctx: Activity, message: ScheduledMessage, refresh: () -> Unit) {
        if (message.state != DeliveryState.PENDING) {
            confirmDelete(ctx, message) {
                deleteSchedule(ctx, message)
                refresh()
            }
            return
        }

        val dialog = AlertDialogWpp(ctx)
        dialog.setTitle(ctx.getString(R.string.schedule_edit_title))
        dialog.setItems(
            arrayOf<CharSequence?>(
                ctx.getString(R.string.schedule_edit),
                ctx.getString(R.string.schedule_delete)
            )
        ) { _, which ->
            if (which == 0) {
                showScheduleDialog(ctx, message.jid, message) { refresh() }
            } else {
                confirmDelete(ctx, message) {
                    deleteSchedule(ctx, message)
                    refresh()
                }
            }
        }
        dialog.setNegativeButton(ctx.getString(R.string.cancel), null)
        dialog.show()
    }

    /**
     * Resolves a chat's display name for a new schedule.
     *
     * The open chat's title is the best source. Failing that, a schedule addressed
     * to our own JID takes our profile name. Only when neither yields anything does
     * it fall back to the JID's local part, which for a LID chat is a bare number.
     */
    private fun displayNameFor(jid: String): String {
        WppCore.getCurrentChatTitle()?.takeIf { it.isNotBlank() }?.let { return it }
        myNameIfSelf(jid)?.let { return it }
        return jid.substringBefore('@')
    }

    private fun myNameIfSelf(jid: String): String? = runCatching {
        if (WppCore.getMyUserJid()?.userRawString == jid) {
            WppCore.getMyName().takeIf { it.isNotBlank() }
        } else {
            null
        }
    }.getOrNull()

    /**
     * Label for a list row.
     *
     * Rows written before the name was resolved stored the JID's local part, so a
     * stored value equal to it is treated as "no name recorded" and re-resolved
     * instead of being shown as a bare number. Our own chat resolves to our profile
     * name, any other chat to its contact name.
     */
    private fun rowLabel(message: ScheduledMessage): String {
        val local = message.jid.substringBefore('@')
        val stored = message.displayName
        if (stored.isNotBlank() && stored != local) return stored

        myNameIfSelf(message.jid)?.let { return it }
        contactName(message.jid)?.let { return it }
        return local
    }

    /** Looks the chat up in WhatsApp's contact database. */
    private fun contactName(jid: String): String? = runCatching {
        val name = WppCore.getContactName(FMessageWpp.UserJid(jid))
        name.takeIf { it.isNotBlank() && name != "Whatsapp Contact" }
    }.getOrNull()

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
                text = rowLabel(message)
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
