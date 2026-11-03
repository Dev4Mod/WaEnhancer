package com.wmods.wppenhacer.xposed.features.scheduling

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
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
 * Text has three routes, tried in order:
 *
 *  1. **Headless via string reference** - finds WhatsApp's `userActionSendTextMessages`
 *     method by searching for that string in the DEX. This is the same method WhatsApp's
 *     own UI calls when you press "send", but invoked directly — no window, no screen,
 *     works with the app backgrounded and the device locked.
 *  2. **Headless via signature scan** - walks ActionUser's class hierarchy looking for any
 *     method that takes a UserJid (or List of UserJid) and a String. Broader than the old
 *     search (which required returnType==List), this catches methods that return void.
 *  3. **Share intent + `performClick()`** - the fallback for media/stickers and for text
 *     when both headless paths fail. Needs WhatsApp to be able to show a screen.
 *
 * Route 1 is the one that matters: it is the only path that reliably sends in true
 * background without opening a conversation. The string "userActionSendTextMessages"
 * survives obfuscation because it appears as a log/debug string inside the method body.
 */
object MessageSender {

    data class SendResult(val ok: Boolean, val error: String? = null)

    // ---- cached lookups (resolved once, reused for every send) ----

    /** Found via [Unobfuscator.loadTextSendMethod] — the primary headless path. */
    @Volatile
    private var textSendMethod: Method? = null

    @Volatile
    private var textSendMethodResolved = false

    /** Instance of the class that owns the send methods, resolved lazily. */
    @Volatile
    private var cachedHandlerInstance: Any? = null

    /**
     * Captured parameter values from a real send. When the user sends a message
     * manually, the hook in [captureSendParams] stores the arguments. On the next
     * scheduled send, those arguments are replayed with only the JID and text
     * overridden — every other parameter (message builder, chat info, timestamps,
     * etc.) is exactly what WhatsApp itself used.
     */
    @Volatile
    private var capturedArgs: Array<Any?>? = null

    @Volatile
    private var capturedMethod: Method? = null

    /**
     * The live object WhatsApp was called on when the capture happened. Replaying
     * against this exact instance is far safer than constructing a new one: the
     * handler holds references to the message queue, transaction manager and
     * caches that a fresh `newInstance()` would not have.
     */
    @Volatile
    private var capturedInstance: Any? = null

    fun send(context: Context, message: ScheduledMessage): SendResult {
        if (message.kind != MessageKind.TEXT) {
            return sendViaShareIntent(context, message)
        }

        // Route 1: string-based lookup — the dependable headless path
        val headless = sendTextHeadlessStringRef(message.jid, message.text.orEmpty())
        if (headless.ok) return headless

        YukiLog.log(
            "[ScheduledMessages] primary headless path unavailable (${headless.error}); " +
                "trying signature scan"
        )

        // Route 2: broadened signature scan
        val signature = sendTextHeadlessSignature(message.jid, message.text.orEmpty())
        if (signature.ok) return signature

        YukiLog.log(
            "[ScheduledMessages] signature scan also unavailable (${signature.error}); " +
                "falling back to the share intent"
        )

        // Route 3: UI fallback (last resort)
        val viaUi = sendViaShareIntent(context, message)
        return if (viaUi.ok) viaUi
        else SendResult(false, "headless=(${headless.error}) / sig=(${signature.error}) / ui=(${viaUi.error})")
    }

    // ----------------------------------------------------- Route 1: string ref

    /**
     * Headless text send, driven by the string "userActionSendTextMessages".
     *
     * That string sits inside the class that owns WhatsApp's message-send entry
     * points, so searching for it identifies the right class even though the name
     * is obfuscated. On WhatsApp 2.26.x that class is `X.6ZW` and it exposes:
     *
     *   - `A07(Jid, String) -> void`    — a plain send-text-to-jid call
     *   - `A04(?, Jid, String) -> List` — the same, returning created messages
     *   - `A01(...22 params...)`        — the full-fat UI entry point
     *
     * We call `A07` (or `A04`) directly: the recipient and the text are explicit
     * parameters, so nothing has to be guessed and nothing has to be captured
     * first. Only when neither is present do we fall back to replaying a
     * captured 22-parameter call.
     */
    private fun sendTextHeadlessStringRef(jid: String, text: String): SendResult {
        if (text.isBlank()) return SendResult(false, "empty message")

        val anchor = resolveTextSendMethodByString()
            ?: return SendResult(false, "could not find the send class via string reference")

        val userJid = runCatching { WppCore.createUserJid(jid) }.getOrNull()
            ?: return SendResult(false, "could not resolve JID $jid")

        val handlerClass = anchor.declaringClass

        // Preferred: a dedicated low-arity send API on the same class.
        val candidates = resolveSendCandidates(handlerClass, userJid)
        if (candidates.isNotEmpty()) {
            val instance = resolveHandlerInstance(handlerClass)
            if (instance != null) {
                var lastError: String? = null
                for (candidate in candidates) {
                    val attempt = invokeSimpleSend(candidate, instance, userJid, text)
                    if (attempt.ok) return attempt
                    lastError = attempt.error
                    YukiLog.log(
                        "[ScheduledMessages] ${candidate.name} failed (${attempt.error}); " +
                            "trying the next candidate"
                    )
                }
                YukiLog.log(
                    "[ScheduledMessages] every low-arity send candidate failed: $lastError"
                )
            } else {
                YukiLog.log(
                    "[ScheduledMessages] send candidates found but no handler instance available"
                )
            }
        }

        // Fallback: replay a captured 22-param call as a template.
        val captured = capturedArgs
        val instance = capturedInstance ?: resolveHandlerInstance(handlerClass)
        if (captured != null && instance != null) {
            return replayCapturedSend(anchor, instance, captured, userJid, text)
        }

        return SendResult(
            false,
            "no low-arity send API on ${handlerClass.name} and no captured call to replay"
        )
    }

    /**
     * Resolves the send class and reports whether headless sending is ready.
     * Runs on a background thread at startup so the answer is in the log before
     * the first schedule ever fires.
     */
    fun preResolve() {
        val anchor = resolveTextSendMethodByString() ?: return
        val probe = runCatching { WppCore.createUserJid("1234567890@s.whatsapp.net") }
            .getOrNull() ?: return

        val candidates = resolveSendCandidates(anchor.declaringClass, probe)
        if (candidates.isEmpty()) {
            YukiLog.log(
                "[ScheduledMessages] no low-arity send API on ${anchor.declaringClass.name}; " +
                    "capture-and-replay will be required"
            )
            return
        }

        val instance = resolveHandlerInstance(anchor.declaringClass)
        if (instance != null) {
            YukiLog.log(
                "[ScheduledMessages] headless send is READY: " +
                    candidates.joinToString(" / ") { "${anchor.declaringClass.name}#${it.name}" } +
                    " on ${instance.javaClass.name}"
            )
            dumpHandlerState(instance)
        } else {
            YukiLog.log(
                "[ScheduledMessages] headless send needs a handler instance - " +
                    "one manual message in WhatsApp will provide it"
            )
        }
    }

    /**
     * Logs the handler's instance fields and whether each one is populated.
     *
     * `A07(Jid, String)` takes only a recipient and a body, so everything else it
     * needs — the message queue, transaction manager, caches — has to come from
     * the handler's own fields. If a no-arg-constructed instance has them all
     * null, it is a hollow shell and only the captured instance will work.
     */
    private fun dumpHandlerState(instance: Any) {
        val fields = runCatching { instance.javaClass.declaredFields }
            .getOrNull().orEmpty()
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }

        val populated = fields.count { f ->
            runCatching { f.isAccessible = true; f.get(instance) }.getOrNull() != null
        }

        YukiLog.log(
            "[ScheduledMessages] handler state: ${fields.size} field(s), $populated populated"
        )

        fields.take(40).forEach { f ->
            val value = runCatching { f.isAccessible = true; f.get(instance) }.getOrNull()
            val described = if (value == null) "null" else value.javaClass.simpleName
            YukiLog.log("[ScheduledMessages]   ${f.name}: ${f.type.simpleName} = $described")
        }
    }

    /**
     * Debug helper: sends one message to our own chat through the headless path.
     *
     * Kept for local diagnosis only. It is deliberately not reachable from the
     * shipped build: an earlier version triggered it from a marker file, which
     * meant any app able to write to shared storage could make WhatsApp send a
     * message. Call it from a debugger or a temporary hook instead.
     */
    @Suppress("unused")
    private fun sendSelfTest(): SendResult {
        val me = runCatching { WppCore.getMyUserJid()?.userRawString }.getOrNull()
            ?: return SendResult(false, "could not resolve own JID")

        YukiLog.log("[ScheduledMessages] self-test sending to $me")
        return sendTextHeadlessStringRef(
            me,
            "WaEnhancer self-test ${System.currentTimeMillis()}"
        )
    }

    /**
     * Uses DexKit's string search to locate the method that contains
     * "userActionSendTextMessages" in its body. Cached after first resolution.
     */
    private fun resolveTextSendMethodByString(): Method? {
        if (textSendMethodResolved) return textSendMethod

        synchronized(this) {
            if (textSendMethodResolved) return textSendMethod

            val loader = Utils.appClassLoader
            val result = runCatching {
                Unobfuscator.loadTextSendMethod(loader)
            }

            result.onSuccess { m ->
                runCatching { m.isAccessible = true }
                textSendMethod = m
                YukiLog.log("[ScheduledMessages] text send method (string ref): ${m.declaringClass.name}#${m.name}")

                // Install the capture hook so we can learn the params from a real send.
                // This calls back into ScheduledMessages.captureHookInstaller which
                // uses the YukiHook DSL (needs PackageParam context).
                tryInstallCaptureHook()
            }.onFailure { e ->
                YukiLog.log("[ScheduledMessages] string-ref method search failed: ${e.message}")
            }

            textSendMethodResolved = true
            return textSendMethod
        }
    }

    /**
     * Collects the send entry points on the handler class that take an explicit
     * recipient and body, ordered by preference:
     *
     *  1. `(Jid, String) -> void`     — recipient and text are the only parameters
     *  2. `(?, Jid, String) -> List`  — the same, with a context first; the List it
     *     returns holds the created messages, so it doubles as a success signal
     *
     * Returning both lets the caller try the next one if the first throws.
     */
    private fun resolveSendCandidates(cls: Class<*>, userJid: Any): List<Method> {
        val methods = runCatching { cls.declaredMethods }.getOrNull().orEmpty()
        val candidates = ArrayList<Method>(2)

        methods.firstOrNull { m ->
            m.parameterCount == 2 &&
                m.parameterTypes[0].isAssignableFrom(userJid.javaClass) &&
                m.parameterTypes[1] == String::class.java &&
                m.returnType == Void.TYPE
        }?.let { m ->
            runCatching { m.isAccessible = true }
            candidates.add(m)
            YukiLog.log(
                "[ScheduledMessages] send candidate: ${cls.name}#${m.name}(Jid, String) -> void"
            )
        }

        methods.firstOrNull { m ->
            m.parameterCount == 3 &&
                m.parameterTypes[1].isAssignableFrom(userJid.javaClass) &&
                m.parameterTypes[2] == String::class.java &&
                List::class.java.isAssignableFrom(m.returnType)
        }?.let { m ->
            runCatching { m.isAccessible = true }
            candidates.add(m)
            YukiLog.log(
                "[ScheduledMessages] send candidate: " +
                    "${cls.name}#${m.name}(?, Jid, String) -> List"
            )
        }

        return candidates
    }

    /**
     * Obtains an instance of the handler class.
     *
     * Order:
     *  1. the instance captured from a real send — the genuine object, with its
     *     queues and caches already wired; upgrades the cache as soon as one exists
     *  2. a static field whose type is the class itself — a Kotlin `object`
     *     singleton keeps itself in `INSTANCE`, and that is the live handler
     *  3. a no-arg constructor — fine for stateless facades, but may be a hollow
     *     shell if the class expects its dependencies to be injected
     */
    private fun resolveHandlerInstance(cls: Class<*>): Any? {
        val captured = capturedInstance
        if (captured != null && cls.isInstance(captured)) {
            if (cachedHandlerInstance !== captured) {
                YukiLog.log("[ScheduledMessages] handler instance upgraded to the captured one")
            }
            cachedHandlerInstance = captured
            return captured
        }

        cachedHandlerInstance?.let { if (cls.isInstance(it)) return it }

        val selfField = runCatching {
            cls.declaredFields.firstOrNull { f ->
                java.lang.reflect.Modifier.isStatic(f.modifiers) && cls.isAssignableFrom(f.type)
            }
        }.getOrNull()

        if (selfField != null) {
            val value = runCatching {
                selfField.isAccessible = true
                selfField.get(null)
            }.getOrNull()
            if (value != null) {
                cachedHandlerInstance = value
                YukiLog.log(
                    "[ScheduledMessages] handler instance via static field ${selfField.name}"
                )
                return value
            }
        }

        val constructed = runCatching {
            cls.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
        }.getOrNull()
        if (constructed != null) {
            cachedHandlerInstance = constructed
            YukiLog.log("[ScheduledMessages] handler instance via no-arg constructor")
            return constructed
        }

        YukiLog.log("[ScheduledMessages] no handler instance available for ${cls.name}")
        return null
    }

    /**
     * Invokes the low-arity send method.
     *
     * For the 3-parameter shape the leading argument is filled with the
     * application context when the type accepts it, otherwise its default.
     */
    private fun invokeSimpleSend(
        method: Method,
        instance: Any,
        userJid: Any,
        text: String
    ): SendResult {
        return try {
            val types = method.parameterTypes
            val args = arrayOfNulls<Any>(types.size)

            if (types.size == 2) {
                args[0] = userJid
                args[1] = text
            } else {
                val leading = types[0]
                val app = runCatching { Utils.application }.getOrNull()
                args[0] = if (app != null && leading.isAssignableFrom(app.javaClass)) {
                    app
                } else {
                    ReflectionUtils.getDefaultValue(leading)
                }
                args[1] = userJid
                args[2] = text
            }

            // Log the shape of the call, never the body: the message text is
            // private and this log is readable from outside the app.
            YukiLog.log(
                "[ScheduledMessages] sending via ${method.declaringClass.name}#${method.name} " +
                    "to $userJid (${text.length} chars)"
            )

            val result = method.invoke(instance, *args)

            when {
                method.returnType == Void.TYPE || result == null -> SendResult(true)
                result is List<*> ->
                    if (result.isNotEmpty()) SendResult(true)
                    else SendResult(false, "WhatsApp rejected the message (empty result)")
                result is Boolean ->
                    if (result) SendResult(true)
                    else SendResult(false, "WhatsApp rejected the message (returned false)")
                else -> SendResult(true)
            }
        } catch (t: Throwable) {
            YukiLog.log(t)
            val cause = t.cause ?: t
            SendResult(false, "send failed: ${cause.message ?: cause.javaClass.simpleName}")
        }
    }

    // --------------------------------------------------- Route 2: signature scan

    /**
     * Walks the ActionUser hierarchy looking for any method that takes a UserJid (or
     * List) and a String parameter. Broader than the old search:
     * - Any return type (not just List)
     * - Accepts UserJid OR List as the recipient parameter
     * - Accepts the text as any String parameter
     */
    private fun sendTextHeadlessSignature(jid: String, text: String): SendResult {
        if (text.isBlank()) return SendResult(false, "empty message")

        val userJid = runCatching { WppCore.createUserJid(jid) }.getOrNull()
            ?: return SendResult(false, "could not resolve JID $jid")

        val actionUser = WppCore.getActionUser()
            ?: return SendResult(false, "WhatsApp is not ready yet (ActionUser null)")

        val method = resolveTextSendMethodBySignature(actionUser.javaClass, userJid)
            ?: return SendResult(false, "no matching method in ActionUser hierarchy")

        return invokeSendMethod(method, actionUser, userJid, text)
    }

    /**
     * Scans the class hierarchy for a method that:
     * - Takes at least one parameter the userJid is assignable to (the recipient)
     *   OR a List parameter (the old shape)
     * - Takes at least one String parameter (the message text)
     * - Has at most 6 parameters
     *
     * Uses the actual userJid instance for type-checking, because on obfuscated
     * WhatsApp builds the parameter type name is something like "1OK" — it won't
     * match a string search for "UserJid", but `isAssignableFrom` works because
     * the userJid from `WppCore.createUserJid` is an instance of the right type.
     *
     * Prefers methods that return void (the modern send shape) or List (the old
     * shape); avoids methods returning Boolean (those are usually queries).
     */
    private fun resolveTextSendMethodBySignature(cls: Class<*>, userJid: Any?): Method? {
        if (userJid == null) return null

        var current: Class<*>? = cls
        while (current != null && current != Any::class.java) {
            val owner: Class<*> = current

            val candidates = runCatching {
                owner.declaredMethods.filter { m ->
                    val types = m.parameterTypes
                    types.size in 2..6 &&
                        ReflectionUtils.findIndexOfType(types, String::class.java) != -1 &&
                        (
                            // The userJid instance is assignable to one of the params
                            types.any { it.isAssignableFrom(userJid.javaClass) } ||
                            // Or there's a List param (old shape — singleton list of jids)
                            ReflectionUtils.findIndexOfType(types, List::class.java) != -1
                        )
                }.sortedByDescending { m ->
                    // Prefer void (modern send), then List (old send), then others
                    when {
                        m.returnType == Void.TYPE -> 3
                        List::class.java.isAssignableFrom(m.returnType) -> 2
                        m.returnType == Boolean::class.javaPrimitiveType -> 0  // likely a query, deprioritize
                        else -> 1
                    }
                }
            }.getOrNull().orEmpty()

            if (candidates.isNotEmpty()) {
                val hit = candidates.first()
                runCatching { hit.isAccessible = true }
                YukiLog.log(
                    "[ScheduledMessages] signature scan found: ${owner.name}#${hit.name}(" +
                        hit.parameterTypes.joinToString(",") { it.simpleName } +
                        ") -> ${hit.returnType.simpleName}"
                )
                return hit
            }

            current = owner.superclass
        }

        return null
    }

    // ----------------------------------------------- shared invocation logic

    /**
     * Invokes the resolved send method with the given instance, userJid, and text.
     *
     * Handles parameter mapping:
     * - The String parameter gets the message text
     * - The List parameter gets a singleton list containing the userJid
     * - A UserJid-typed parameter gets the userJid directly
     * - All other parameters get their default values
     */
    private fun invokeSendMethod(method: Method, instance: Any, userJid: Any, text: String): SendResult {
        return try {
            val types = method.parameterTypes
            val args = arrayOfNulls<Any>(types.size)

            // Fill defaults first
            for (i in types.indices) {
                args[i] = ReflectionUtils.getDefaultValue(types[i])
            }

            // Set the text on every String parameter (there's usually exactly one)
            for (i in types.indices) {
                if (types[i] == String::class.java) {
                    args[i] = text
                }
            }

            // Set the recipient on List and/or Jid-compatible parameters
            for (i in types.indices) {
                if (List::class.java.isAssignableFrom(types[i])) {
                    args[i] = Collections.singletonList(userJid)
                } else if (types[i].isAssignableFrom(userJid.javaClass)) {
                    // The userJid instance fits this parameter — use it directly.
                    // This works for obfuscated types like "1OK" where the name
                    // doesn't contain "UserJid" but the instance is assignable.
                    args[i] = userJid
                }
            }

            YukiLog.log(
                "[ScheduledMessages] invoking ${method.declaringClass.name}#${method.name}(" +
                    types.joinToString(",") { it.simpleName } + ")"
            )

            val result = method.invoke(instance, *args)

            // For void methods, assume success if no exception was thrown
            if (result == null || method.returnType == Void.TYPE) {
                SendResult(true)
            } else if (result is List<*>) {
                if (result.isNotEmpty()) SendResult(true)
                else SendResult(false, "WhatsApp rejected the message (empty result)")
            } else if (result is Boolean) {
                if (result) SendResult(true)
                else SendResult(false, "WhatsApp rejected the message (returned false)")
            } else {
                // Non-void, non-List, non-Boolean — assume success
                SendResult(true)
            }
        } catch (t: Throwable) {
            YukiLog.log(t)
            SendResult(false, t.message ?: t.javaClass.simpleName)
        }
    }

    // ------------------------------------------------------- capture / replay

    /**
     * Replays captured arguments from a real send, acting as a **template**.
     *
     * The captured args are the exact objects WhatsApp built for its own send
     * (message builder, chat info, JID list, flags...). We copy the array and
     * substitute only the parts that describe *what* and *to whom*:
     *
     *  - the recipient slot (any parameter the target JID is assignable to)
     *  - a List parameter that holds JIDs (singleton list of the target JID)
     *  - the message text slot (String parameters)
     *
     * Everything else — transaction ids, ephemeral settings, forwarding flags,
     * the handler instance itself — stays exactly as WhatsApp prepared it.
     *
     * Retargeting the JID is essential. Without it the replay would deliver to
     * whichever chat happened to be open when the capture was taken.
     */
    private fun replayCapturedSend(
        method: Method,
        fallbackInstance: Any,
        captured: Array<Any?>,
        userJid: Any,
        text: String
    ): SendResult {
        return try {
            val types = method.parameterTypes
            val args = captured.copyOf()

            var jidSlots = 0
            var textSlots = 0

            for (i in types.indices) {
                val type = types[i]
                when {
                    // Message text
                    type == String::class.java -> {
                        args[i] = text
                        textSlots++
                    }

                    // A parameter that the target JID fits directly
                    type.isAssignableFrom(userJid.javaClass) -> {
                        args[i] = userJid
                        jidSlots++
                    }

                    // A List of JIDs. Only treat it as the recipient slot when the
                    // captured contents are not strings — a List<String> is more
                    // likely mentions or a hashtag list.
                    List::class.java.isAssignableFrom(type) -> {
                        val capturedValue = captured.getOrNull(i) as? List<*>
                        val looksLikeJids = capturedValue == null ||
                            capturedValue.isEmpty() ||
                            capturedValue.firstOrNull() !is CharSequence
                        if (looksLikeJids) {
                            args[i] = Collections.singletonList(userJid)
                            jidSlots++
                        }
                    }
                }
            }

            // Prefer the live object from the capture; it owns the real queues.
            val target = capturedInstance
                ?.takeIf { method.declaringClass.isInstance(it) }
                ?: fallbackInstance

            YukiLog.log(
                "[ScheduledMessages] replay: ${args.size} params, " +
                    "retargeted=$jidSlots slot(s), text=$textSlots slot(s), " +
                    "instance=${target.javaClass.name}"
            )

            if (jidSlots == 0) {
                YukiLog.log(
                    "[ScheduledMessages] WARNING: no recipient slot matched in the captured " +
                        "signature - the message may be delivered to the captured chat"
                )
            }

            val result = method.invoke(target, *args)

            if (result == null || method.returnType == Void.TYPE) {
                SendResult(true)
            } else if (result is Boolean) {
                if (result) SendResult(true)
                else SendResult(false, "WhatsApp returned false")
            } else {
                SendResult(true)
            }
        } catch (t: Throwable) {
            YukiLog.log(t)
            val cause = t.cause ?: t
            SendResult(false, "replay failed: ${cause.message ?: cause.javaClass.simpleName}")
        }
    }

    @Volatile
    private var captureHookInstalled = false

    /**
     * Attempts to install the capture hook on the resolved text send method.
     * Called both from [preResolve] (background thread, after resolution) and
     * from [ScheduledMessages.doHook] (inline, where PackageParam context is
     * available).
     *
     * The actual DSL hook installation is delegated to
     * [ScheduledMessages.captureHookInstaller], which is a lambda set during
     * [ScheduledMessages.doHook]. This indirection exists because the YukiHook
     * `.hook { }` DSL requires a PackageParam context that MessageSender
     * (a plain object) doesn't have.
     *
     * If the method hasn't been resolved yet, or the installer hasn't been set
     * yet, this is a no-op. It will be retried: [preResolve] calls this after
     * resolution completes, and [ScheduledMessages.doHook] calls it after
     * setting the installer. Whichever runs second does the install.
     */
    fun tryInstallCaptureHook() {
        if (captureHookInstalled) return
        val method = textSendMethod ?: return
        val installer = ScheduledMessages.captureHookInstaller ?: return

        runCatching {
            installer(method)
            captureHookInstalled = true
            YukiLog.log("[ScheduledMessages] send capture hook installed on ${method.declaringClass.name}#${method.name}")
        }.onFailure { e ->
            YukiLog.log("[ScheduledMessages] send capture hook failed: ${e.message}")
        }
    }

    /**
     * Called by the capture hook's `before` callback. Stores the args and the
     * live handler instance from a real user send, so a later scheduled send can
     * replay them as a template.
     *
     * Each argument is logged with its runtime type (and value where that is
     * small and safe to print) so the recipient and text slots can be identified
     * from the log without guesswork.
     */
    fun onSendCaptured(argArray: Array<Any?>, instance: Any?) {
        capturedArgs = argArray
        capturedInstance = instance

        YukiLog.log(
            "[ScheduledMessages] captured ${argArray.size} send params " +
                "from a real message send"
        )
        argArray.forEachIndexed { i, value ->
            // Strings are reported by length only — this hook sees every message
            // the user sends, so their contents must never reach the log.
            val described = when (value) {
                null -> "null"
                is String -> "String(${value.length} chars)"
                is Number, is Boolean, is Char -> "${value.javaClass.simpleName}($value)"
                is Collection<*> ->
                    "Collection(${value.size}) first=${value.firstOrNull()?.javaClass?.name}"
                is Array<*> -> "Array(${value.size})"
                else -> value.javaClass.name
            }
            YukiLog.log("[ScheduledMessages]   arg[$i] = $described")
        }
        YukiLog.log(
            "[ScheduledMessages] captured handler instance: ${instance?.javaClass?.name}"
        )
    }

    // ------------------------------------------------------- diagnostics

    /**
     * Dumps every method on ActionUser (and its superclasses) to the log.
     * Costs one log dump at startup; helps diagnose send failures.
     *
     * Also logs full parameter type names (not just simple names) and tests
     * whether the userJid from `WppCore.createUserJid` is assignable to each
     * parameter — this is how we identify the send method on obfuscated builds
     * where parameter type names like "1OK" don't contain "UserJid".
     */
    fun logActionUserApi() {
        val actionUser = runCatching { WppCore.getActionUser() }.getOrNull()
        if (actionUser == null) {
            YukiLog.log("[ScheduledMessages] ActionUser is null - cannot dump the API")
            return
        }

        // Create a test userJid to check assignability
        val testJid = runCatching { WppCore.createUserJid("1234567890@s.whatsapp.net") }.getOrNull()

        var cls: Class<*>? = actionUser.javaClass
        while (cls != null && cls != Any::class.java) {
            YukiLog.log("[ScheduledMessages] ActionUser class ${cls.name}")
            runCatching {
                cls.declaredMethods.sortedBy { it.name }.forEach { m ->
                    val types = m.parameterTypes
                    val params = types.joinToString(", ") { t ->
                        val assignable = if (testJid != null && t.isAssignableFrom(testJid.javaClass)) {
                            " <== JID fits"
                        } else ""
                        "${t.name}$assignable"
                    }
                    YukiLog.log("[ScheduledMessages]   ${m.name}($params) -> ${m.returnType.name}")
                }
            }
            cls = cls.superclass
        }

        if (testJid != null) {
            YukiLog.log("[ScheduledMessages] test JID class: ${testJid.javaClass.name}")
        } else {
            YukiLog.log("[ScheduledMessages] test JID is null — createUserJid failed")
        }

        // Also log whether the string-ref method was found
        val m = textSendMethod
        if (m != null) {
            YukiLog.log("[ScheduledMessages] resolved text send: ${m.declaringClass.name}#${m.name} (${m.parameterTypes.size} params)")
            if (capturedArgs != null) {
                YukiLog.log("[ScheduledMessages] captured params available — headless replay is ready")
            } else {
                YukiLog.log("[ScheduledMessages] no captured params yet — send a message manually to enable headless replay")
            }
        } else {
            YukiLog.log("[ScheduledMessages] text send method NOT resolved via string ref")
        }
    }

    /**
     * Dumps every method of the class that owns the text send method, tagged with
     * the roles we care about (String / List / JID-compatible / FMessage).
     *
     * Purpose: the resolved `userActionSendTextMessages` method takes 22 params,
     * which is impractical to fill in by hand. If the same class exposes a
     * low-arity send (e.g. one taking an FMessage), we can call that directly and
     * drop the capture-and-replay bootstrap altogether.
     */
    fun dumpSendHandlerApi() {
        val sendMethod = textSendMethod ?: run {
            YukiLog.log("[ScheduledMessages] cannot dump send handler - method not resolved")
            return
        }

        val cls = sendMethod.declaringClass
        val testJid = runCatching { WppCore.createUserJid("1234567890@s.whatsapp.net") }.getOrNull()
        val fMessageType = runCatching {
            com.wmods.wppenhacer.xposed.core.components.FMessageWpp.TYPE
        }.getOrNull()

        val methods = runCatching { cls.declaredMethods }.getOrNull().orEmpty()
        YukiLog.log(
            "[ScheduledMessages] === ${cls.name}: ${methods.size} declared method(s) ==="
        )

        methods
            .sortedWith(compareBy({ it.parameterCount }, { it.name }))
            .forEach { m ->
                val signature = m.parameterTypes.joinToString(", ") { t ->
                    val role = buildString {
                        if (t == String::class.java) append(" STRING")
                        if (List::class.java.isAssignableFrom(t)) append(" LIST")
                        if (testJid != null && t.isAssignableFrom(testJid.javaClass)) append(" JID")
                        if (fMessageType != null && t == fMessageType) append(" FMSG")
                    }
                    "${t.simpleName}$role"
                }
                YukiLog.log(
                    "[ScheduledMessages]   ${m.name}($signature) -> ${m.returnType.simpleName}"
                )
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
        val reason = PendingUiSend.failureReason()
        PendingUiSend.clear()

        return when {
            finished && success -> SendResult(true)
            finished -> SendResult(false, reason.ifBlank { "send did not commit" })
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

    private const val UI_TIMEOUT_SECONDS = 25L

    object PendingUiSend {

        private const val MAX_ATTEMPTS = 24
        private const val MAX_CLICKS = 3
        private const val FIRST_DELAY_MS = 700L
        private const val RETRY_DELAY_MS = 500L
        private const val VERIFY_MS = 900L
        private const val PICKER_GRACE_MS = 2500L

        private val SEND_LABELS = setOf(
            "send", "send message", "enviar", "envoyer", "senden", "invia",
            "送信", "发送", "إرسال", "gửi", "kirim", "отправить", "gönder"
        )

        private val PICKER_HINTS = listOf("contactpicker", "contact_picker")

        @Volatile
        private var pending: ScheduledMessage? = null

        @Volatile
        private var latch: CountDownLatch? = null

        @Volatile
        private var success = false

        @Volatile
        private var finished = false

        @Volatile
        private var reason: String = ""

        private var attempts = 0
        private var clicks = 0
        private var clickedAt = 0L
        private var pickerSeenAt = 0L

        private val handler = Handler(Looper.getMainLooper())

        fun begin(message: ScheduledMessage, l: CountDownLatch) {
            if (pending != null && !finished) {
                finished = true
                success = false
                reason = "superseded by a newer schedule"
                latch?.countDown()
            }

            handler.removeCallbacks(ticker)
            pending = message
            latch = l
            success = false
            finished = false
            reason = ""
            attempts = 0
            clicks = 0
            clickedAt = 0L
            pickerSeenAt = 0L
            handler.postDelayed(ticker, FIRST_DELAY_MS)
        }

        fun clear() {
            handler.removeCallbacks(ticker)
            pending = null
            latch = null
        }

        fun wasSuccessful(): Boolean = success

        fun failureReason(): String = reason

        fun isActive(): Boolean = pending != null && !finished

        fun onActivityResumed(activity: Activity) {
            if (!isActive()) return
            allowOverKeyguard(activity)
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }

        private val ticker = object : Runnable {
            override fun run() {
                if (!isActive()) return
                attempts++

                val message = pending
                val activity = runCatching { WppCore.getCurrentActivity() }.getOrNull()
                val decor = activity?.window?.decorView

                if (activity != null && decor != null) {
                    allowOverKeyguard(activity)
                    val now = SystemClock.uptimeMillis()

                    if (clickedAt != 0L) {
                        if (now - clickedAt >= VERIFY_MS) {
                            val stillTyped = message?.kind == MessageKind.TEXT && composerHasText(decor)
                            if (!stillTyped) {
                                finishOk()
                                return
                            }
                            if (clicks >= MAX_CLICKS) {
                                finishFail("the send button did not clear the composer")
                                return
                            }
                            clickedAt = 0L
                        }
                    } else {
                        if (message?.kind == MessageKind.TEXT && looksLikeChatPicker(decor)) {
                            if (pickerSeenAt == 0L) {
                                pickerSeenAt = now
                            } else if (now - pickerSeenAt > PICKER_GRACE_MS) {
                                finishFail("WhatsApp asked to pick a chat - the jid was not honoured")
                                return
                            }
                        } else {
                            pickerSeenAt = 0L
                        }

                        if (clickSend(decor)) {
                            clicks++
                            clickedAt = SystemClock.uptimeMillis()
                            YukiLog.log("[ScheduledMessages] clicked send (attempt $clicks)")
                        }
                    }
                }

                if (attempts >= MAX_ATTEMPTS) {
                    finishFail(
                        if (clicks == 0) "never found an enabled send button"
                        else "clicked $clicks time(s) but nothing changed"
                    )
                    return
                }
                handler.postDelayed(this, RETRY_DELAY_MS)
            }
        }

        private fun finishOk() {
            success = true
            finished = true
            reason = ""
            latch?.countDown()
            YukiLog.log("[ScheduledMessages] send committed after $attempts attempt(s)")
        }

        private fun finishFail(why: String) {
            success = false
            finished = true
            reason = why
            latch?.countDown()
            YukiLog.log("[ScheduledMessages] send failed: $why (after $attempts attempt(s))")
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

        private fun composerHasText(root: View): Boolean {
            val boxes = ArrayList<EditText>(2)
            collectEditTexts(root, 0, boxes)
            return boxes.any { it.isShown && !it.text.isNullOrEmpty() }
        }

        private fun collectEditTexts(v: View, depth: Int, out: MutableList<EditText>) {
            if (depth > 40 || out.size >= 4) return
            if (v is EditText && v.isShown) out.add(v)
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    val child = v.getChildAt(i) ?: continue
                    collectEditTexts(child, depth + 1, out)
                }
            }
        }

        private fun looksLikeChatPicker(root: View): Boolean {
            var found = false
            fun scan(v: View, depth: Int) {
                if (found || depth > 30) return
                val name = runCatching {
                    if (v.id == View.NO_ID) null else v.resources.getResourceEntryName(v.id)
                }.getOrNull()
                if (name != null && PICKER_HINTS.any { name.contains(it) }) {
                    found = true
                    return
                }
                if (v is ViewGroup) {
                    for (i in 0 until v.childCount) {
                        val child = v.getChildAt(i) ?: continue
                        scan(child, depth + 1)
                    }
                }
            }
            scan(root, 0)
            return found
        }
    }
}
