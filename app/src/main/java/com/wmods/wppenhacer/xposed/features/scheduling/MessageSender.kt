package com.wmods.wppenhacer.xposed.features.scheduling

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Delivers a scheduled message.
 *
 * Text has two routes, tried in order:
 *
 *  1. **Headless** - call WhatsApp's own send API on `ActionUser`. No window, so it works
 *     with the screen off and the device locked.
 *  2. **Share intent + `performClick()`** - the route media and stickers use. Needs
 *     WhatsApp to be able to put a screen up, but depends only on the public intent
 *     surface and on view ids, which survive WhatsApp updates.
 *
 * The fallback matters: route 1 leans on an obfuscated method that moves between releases.
 * On 2.26.42.74 the signature the module's own `WppCore.sendMessage` looks for is no longer
 * present, so without a fallback every schedule fails with "no text send method found".
 */
object MessageSender {

    data class SendResult(val ok: Boolean, val error: String? = null)

    /** Cached so the signature scan happens once, not on every send. */
    @Volatile
    private var textSendMethod: Method? = null

    @Volatile
    private var textSendMethodResolved = false

    fun send(context: Context, message: ScheduledMessage): SendResult {
        if (message.kind != MessageKind.TEXT) {
            return sendViaShareIntent(context, message)
        }

        val headless = sendTextHeadless(message.jid, message.text.orEmpty())
        if (headless.ok) return headless

        YukiLog.log(
            "[ScheduledMessages] headless text send unavailable (${headless.error}); " +
                "falling back to the share intent"
        )
        val viaUi = sendViaShareIntent(context, message)
        return if (viaUi.ok) viaUi else SendResult(false, "${headless.error} / ${viaUi.error}")
    }

    // ------------------------------------------------------------------ text

    /**
     * Mirrors the call the module already makes for its Tasker integration, but keeps the
     * returned list so a rejection can be reported instead of silently swallowed.
     */
    private fun sendTextHeadless(jid: String, text: String): SendResult {
        if (text.isBlank()) return SendResult(false, "empty message")

        val userJid = runCatching { WppCore.createUserJid(jid) }.getOrNull()
            ?: return SendResult(false, "could not resolve JID $jid")

        val actionUser = WppCore.getActionUser()
            ?: return SendResult(false, "WhatsApp is not ready yet (ActionUser null)")

        val method = resolveTextSendMethod(actionUser.javaClass)
            ?: return SendResult(false, "no text send method found on ActionUser")

        return try {
            val types = method.parameterTypes
            val args = arrayOfNulls<Any>(types.size)
            for (i in types.indices) args[i] = ReflectionUtils.getDefaultValue(types[i])

            val textIdx = ReflectionUtils.findIndexOfType(types, String::class.java)
            val listIdx = ReflectionUtils.findIndexOfType(types, List::class.java)
            if (textIdx < 0 || listIdx < 0) return SendResult(false, "unexpected send signature")

            args[textIdx] = text
            args[listIdx] = Collections.singletonList(userJid)

            val result = method.invoke(actionUser, *args)
            if (result is List<*> && result.isNotEmpty()) {
                SendResult(true)
            } else {
                SendResult(false, "WhatsApp rejected the message")
            }
        } catch (t: Throwable) {
            YukiLog.log(t)
            SendResult(false, t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * Walks the whole hierarchy, not just `declaredMethods` on the concrete class. The send
     * entry point sits on a superclass in some builds, which is exactly why a declared-only
     * scan comes back empty and reports "no text send method found".
     */
    private fun resolveTextSendMethod(cls: Class<*>): Method? {
        if (textSendMethodResolved) return textSendMethod

        synchronized(this) {
            if (textSendMethodResolved) return textSendMethod

            var current: Class<*>? = cls
            while (current != null && current != Any::class.java) {
                val owner: Class<*> = current
                val hit = runCatching {
                    owner.declaredMethods.firstOrNull { m ->
                        List::class.java.isAssignableFrom(m.returnType) &&
                            ReflectionUtils.findIndexOfType(m.parameterTypes, String::class.java) != -1 &&
                            ReflectionUtils.findIndexOfType(m.parameterTypes, List::class.java) != -1
                    }
                }.getOrNull()

                if (hit != null) {
                    runCatching { hit.isAccessible = true }
                    textSendMethod = hit
                    textSendMethodResolved = true
                    YukiLog.log("[ScheduledMessages] text send method: ${owner.name}#${hit.name}")
                    return hit
                }
                current = owner.superclass
            }

            textSendMethodResolved = true
            YukiLog.log("[ScheduledMessages] no headless text send method in ${cls.name} hierarchy")
            return null
        }
    }

    // ------------------------------------------------------- media / sticker

    private fun sendViaShareIntent(context: Context, message: ScheduledMessage): SendResult {
        val intent = buildShareIntent(context, message)
            ?: return SendResult(false, "nothing to send (missing media URI)")

        val latch = CountDownLatch(1)
        PendingUiSend.begin(message, latch)

        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            PendingUiSend.clear()
            YukiLog.log(t)
            return SendResult(false, "could not open WhatsApp: ${t.message}")
        }

        val finished = latch.await(UI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val success = PendingUiSend.wasSuccessful()
        PendingUiSend.clear()

        return when {
            finished && success -> SendResult(true)
            finished -> SendResult(false, "send button did not commit")
            else -> SendResult(false, "timed out waiting for the send screen")
        }
    }

    private fun buildShareIntent(context: Context, m: ScheduledMessage): Intent? {
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage(context.packageName)
            putExtra("jid", m.jid)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (m.kind == MessageKind.TEXT) {
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, m.text.orEmpty())
            return intent
        }

        val uri = m.mediaUri?.let(Uri::parse) ?: return null
        intent.type = m.mime ?: if (m.kind == MessageKind.STICKER) "image/webp" else "*/*"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.clipData = ClipData.newRawUri("", uri)
        return intent
    }

    /**
     * Kept inside the window `goAsync()` gives a broadcast receiver. The click loop
     * normally resolves in ~13 s, so this only matters when the screen never appears.
     */
    private const val UI_TIMEOUT_SECONDS = 25L

    /**
     * Holds the in-flight UI send and commits it as soon as WhatsApp's screen is up.
     *
     * The retry loop exists because the media preview screen inflates in stages: the send
     * button is often present but disabled for a few hundred milliseconds while the
     * thumbnail is decoded.
     */
    object PendingUiSend {

        private const val MAX_ATTEMPTS = 24
        private const val FIRST_DELAY_MS = 700L
        private const val RETRY_DELAY_MS = 500L

        private val SEND_LABELS = setOf(
            "send", "send message", "enviar", "envoyer", "senden", "invia",
            "送信", "发送", "إرسال", "gửi", "kirim", "отправить", "gönder"
        )

        @Volatile
        private var pending: ScheduledMessage? = null

        @Volatile
        private var latch: CountDownLatch? = null

        @Volatile
        private var success = false

        @Volatile
        private var finished = false

        fun begin(message: ScheduledMessage, l: CountDownLatch) {
            pending = message
            latch = l
            success = false
            finished = false
            attempts = 0
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, FIRST_DELAY_MS)
        }

        fun clear() {
            handler.removeCallbacks(ticker)
            pending = null
            latch = null
        }

        fun wasSuccessful(): Boolean = success

        fun isActive(): Boolean = pending != null && !finished

        /**
         * Nudges the loop when an activity resumes. Not relied on: if WhatsApp is already
         * in the foreground there may be no new resume event at all, which is why [ticker]
         * polls rather than waiting to be called.
         */
        fun onActivityResumed(activity: Activity) {
            if (!isActive()) return
            allowOverKeyguard(activity)
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }

        private val handler = Handler(Looper.getMainLooper())

        private var attempts = 0

        /**
         * Re-reads the current activity on every pass instead of holding a reference to the
         * one that happened to be on screen when the send started - the share intent often
         * replaces it.
         */
        private val ticker = object : Runnable {
            override fun run() {
                if (!isActive()) return

                attempts++
                val activity = runCatching { WppCore.getCurrentActivity() }.getOrNull()
                if (activity != null) {
                    allowOverKeyguard(activity)
                    val decor = activity.window.decorView
                    if (clickSend(decor)) {
                        success = true
                        finished = true
                        latch?.countDown()
                        YukiLog.log("[ScheduledMessages] committed the send after $attempts attempt(s)")
                        return
                    }
                }

                if (attempts >= MAX_ATTEMPTS) {
                    success = false
                    finished = true
                    latch?.countDown()
                    YukiLog.log("[ScheduledMessages] gave up after $attempts attempt(s)")
                    return
                }
                handler.postDelayed(this, RETRY_DELAY_MS)
            }
        }

        private fun allowOverKeyguard(activity: Activity) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    activity.setShowWhenLocked(true)
                    activity.setTurnScreenOn(true)
                } else {
                    @Suppress("DEPRECATION")
                    activity.window.addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    )
                }
            }
        }

        private fun clickSend(root: View): Boolean {
            val id = runCatching { Utils.getID("send", "id") }.getOrDefault(0)
            if (id != 0) {
                val direct = runCatching { root.findViewById<View>(id) }.getOrNull()
                if (direct != null && direct.isShown && direct.isEnabled && direct.performClick()) {
                    return true
                }
            }
            val found = findSendView(root, 0) ?: return false
            return found.performClick()
        }

        private fun findSendView(v: View, depth: Int): View? {
            if (depth > 40) return null

            if (v.isShown && v.isEnabled) {
                val desc = v.contentDescription?.toString()?.trim()?.lowercase()
                if (desc != null && desc in SEND_LABELS) return v

                val name = runCatching {
                    if (v.id == View.NO_ID) null
                    else v.resources.getResourceEntryName(v.id)
                }.getOrNull()
                if (name == "send" || name == "send_btn" || name == "send_button") return v
            }

            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    val child = v.getChildAt(i) ?: continue
                    findSendView(child, depth + 1)?.let { return it }
                }
            }
            return null
        }
    }
}
