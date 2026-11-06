package com.wmods.wppenhacer.xposed.features.scheduling

import android.content.Context
import android.net.Uri
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import org.luckypray.dexkit.query.enums.StringMatchType
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/** Delivery outcome, shared with [MessageSender] so a caller sees one type. */
private typealias SendResult = MessageSender.SendResult

/**
 * Delivers a scheduled attachment without opening a screen.
 *
 * Text has a trivially callable API — `send(jid, text)` — but media does not. WhatsApp
 * models an outgoing attachment as an `FMessage` carrying an `FMedia`, and it builds that
 * object in the UI layer before handing it to the send service. The share-intent route was
 * standing in for that construction, which is why it needed a visible activity.
 *
 * This class performs the construction directly. On WhatsApp 2.26.x the pieces are:
 *
 *  - `X.0qI` — builds the media message. One method takes a `Uri`, the target `Jid`, a
 *    media descriptor and the caption, attaches the descriptor to a fresh `FMessage`
 *    (`1Wp`) and returns it. It is the only method in the build with that shape.
 *  - `X.7qt` — `UserActionsMediaMessageSending`. One method takes that `FMessage` and
 *    transmits it; it is the only two-argument method on the class that returns a value.
 *  - `X.7mf` — makes the media descriptor (mime type + video flag) the builder fills in.
 *
 * None of those names survive an update, so nothing here is matched by name. The entry
 * point is found by the log string `userActionSendMediaMessages`, which WhatsApp keeps in
 * the send method's body, and everything else is derived from that method's signature.
 */
object MediaSender {

    /**
     * Stable across releases: it appears inside the send method's body as a log tag.
     */
    private const val ANCHOR = "userActionSendMediaMessages"

    /** Where picker attachments are copied before the send path can read them. */
    private const val STAGING_DIR = "scheduled-attachments"

    /** Every resolved piece, cached after the first successful lookup. */
    private class Api(
        val transmit: Method,
        val transmitInstance: Any,
        val build: Method,
        val buildInstance: Any,
        val descriptorFactory: Method,
        val descriptorInstance: Any,
        val setFile: Method,
        val quoteCtor: Constructor<*>
    )

    @Volatile
    private var api: Api? = null

    @Volatile
    private var resolutionFailed: String? = null

    @Volatile
    private var resolving = false

    /**
     * Resolves the API and writes the outcome to the log.
     *
     * Called from a background thread at startup so the answer is known before the first
     * schedule fires.
     */
    fun preResolve() {
        if (api != null) return
        if (resolve() == null) {
            YukiLog.log(
                "[ScheduledMessages] headless media send unavailable: " +
                    (resolutionFailed ?: "unknown reason")
            )
        }
    }

    fun send(context: Context, message: ScheduledMessage): SendResult {
        if (message.mediaUris.isEmpty()) return SendResult(false, "no attachment to send")

        val resolved = api ?: resolve()
            ?: return SendResult(
                false,
                "media API not resolved (${resolutionFailed ?: "unknown reason"})"
            )

        val jid = runCatching { WppCore.createUserJid(message.jid) }.getOrNull()
            ?: return SendResult(false, "could not resolve JID ${message.jid}")

        val caption = message.text?.takeIf { it.isNotBlank() }
        val built = ArrayList<Any>(message.mediaUris.size)

        for ((index, raw) in message.mediaUris.withIndex()) {
            val uri = runCatching { Uri.parse(raw) }.getOrNull()
                ?: return SendResult(false, "unreadable attachment uri")

            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                ?: message.mime
                ?: "application/octet-stream"

            // WhatsApp's media object is built around a File: it reads the length and
            // hands the path to the upload job. A picker uri is staged first.
            val file = runCatching { stage(context, uri, index) }.getOrElse { t ->
                YukiLog.log(t)
                return SendResult(false, "could not read attachment: ${t.message}")
            }

            val prepared = runCatching {
                // The descriptor is filled in by the factory, so each attachment needs
                // its own — reusing one would leak the previous file's mime and size.
                val descriptor = resolved.descriptorFactory.invoke(
                    resolved.descriptorInstance,
                    mime,
                    mime.startsWith("video/")
                )
                resolved.setFile.invoke(descriptor, file)

                // A caption belongs to the message, not to each attachment, so it goes
                // on the first one only — the same rule WhatsApp applies to an album.
                resolved.build.invoke(
                    resolved.buildInstance,
                    Uri.fromFile(file),
                    jid,
                    descriptor,
                    resolved.quoteCtor.newInstance(),   // no quoted message
                    null,                               // upload options
                    null,                               // business metadata
                    null,                               // send options
                    null,                               // scheduled timestamp
                    if (index == 0) caption else null,
                    null,                               // file name: from the file
                    null,                               // extra text
                    null,                               // mentions
                    null,                               // extra list
                    // The message type decides which FMessage subclass is built; any
                    // other value produces a text message with no attachment.
                    messageTypeFor(mime),
                    0
                )
            }.getOrElse { t ->
                YukiLog.log(t)
                val cause = t.cause ?: t
                return SendResult(
                    false,
                    "could not prepare attachment: ${cause.message ?: cause.javaClass.simpleName}"
                )
            }

            built.add(prepared)
        }

        return runCatching {
            YukiLog.log(
                "[ScheduledMessages] media send: ${built.size} attachment(s) to $jid, " +
                    "caption=${if (caption == null) "none" else "${caption.length} chars"}"
            )

            // One call per message: WhatsApp's send service takes a single prepared
            // message, and an album is a sequence of them.
            built.forEach { prepared ->
                val result = resolved.transmit.invoke(resolved.transmitInstance, prepared, null)
                if (result == null) {
                    return SendResult(false, "WhatsApp did not accept the media message")
                }
            }
            SendResult(true)
        }.getOrElse { t ->
            YukiLog.log(t)
            val cause = t.cause ?: t
            SendResult(false, "media send failed: ${cause.message ?: cause.javaClass.simpleName}")
        }
    }

    // --------------------------------------------------------------- message type

    /**
     * WhatsApp's message type id for a mime type.
     *
     * The builder takes this as an int and uses it to pick the `FMessage` subclass, so a
     * wrong value silently produces a text message with the attachment dropped.
     */
    private fun messageTypeFor(mime: String): Int = when {
        mime == "image/webp" -> 20          // sticker
        mime.startsWith("image/") -> 1      // image
        mime.startsWith("video/") -> 3      // video
        mime.startsWith("audio/") -> 2      // audio
        else -> 9                           // document
    }

    // --------------------------------------------------------------- staging

    /**
     * Returns a readable [File] for an attachment.
     *
     * A `file:` uri, or a bare path, already names one. Anything else — a picker's
     * `content:` uri — is copied out first, because the send path needs a real path to
     * measure and upload.
     *
     * The copy must not land in the app's private data directory. WhatsApp's
     * `ExternalFileValidator` compares the file's device against its own and refuses to
     * upload anything living on the same filesystem — "file is owned by our application"
     * — which is what a cache-dir copy looks like. External storage is a different
     * device, so the upload is permitted. The copy is left in place: the upload job reads
     * it after this call returns.
     */
    @Suppress("DEPRECATION") // getExternalMediaDirs is the only API for the media tree
    private fun stage(context: Context, uri: Uri, index: Int): File {
        if (uri.scheme == null || uri.scheme == "file") {
            val existing = File(uri.path ?: throw IllegalArgumentException("empty file uri"))
            if (existing.isFile) return existing
        }

        val root = context.getExternalMediaDirs()?.firstOrNull()
            ?: throw IllegalStateException("external storage is not available")

        val dir = File(root, STAGING_DIR).apply { mkdirs() }
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow("_display_name"))
                } else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "attachment"

        val target = File(dir, "${System.currentTimeMillis()}-$index-${name.substringAfterLast('/')}")

        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("cannot open $uri")

        if (target.length() == 0L) throw IllegalStateException("attachment is empty")
        return target
    }

    // --------------------------------------------------------------- resolution

    private fun resolve(): Api? {
        api?.let { return it }

        synchronized(this) {
            api?.let { return it }
            if (resolving) return null
            resolving = true
            try {
                return doResolve().also { api = it }
            } catch (t: Throwable) {
                YukiLog.log(t)
                val cause = t.cause ?: t
                resolutionFailed = cause.message ?: cause.javaClass.simpleName
                return null
            } finally {
                resolving = false
            }
        }
    }

    private fun doResolve(): Api {
        val loader = Utils.appClassLoader

        val anchor = Unobfuscator.findFirstMethodUsingStrings(loader, StringMatchType.Contains, ANCHOR)
            ?: throw IllegalStateException("no method references $ANCHOR")

        val sendClass = anchor.declaringClass
        YukiLog.log("[ScheduledMessages] media send class: ${sendClass.name}")

        // The transmit call is the only two-argument method on the class that returns a
        // value; the rest take a string body or a target set.
        val transmit = runCatching { sendClass.declaredMethods }.getOrNull().orEmpty()
            .firstOrNull { m ->
                m.parameterCount == 2 &&
                    m.returnType != Void.TYPE &&
                    !m.returnType.isPrimitive &&
                    !m.returnType.name.startsWith("java.")
            }
            ?: throw IllegalStateException("no two-argument send method on ${sendClass.name}")

        val messageType = transmit.parameterTypes[0]

        val probe = WppCore.createUserJid("1234567890@s.whatsapp.net")
            ?: throw IllegalStateException("could not build a probe JID")

        // The builder is the only method that turns a Uri into that message type.
        val build = Unobfuscator.withDexKitBridge { bridge ->
            bridge.findMethod {
                matcher {
                    paramCount(15)
                    returnType = messageType.name
                }
            }.mapNotNull { data -> runCatching { data.getMethodInstance(loader) }.getOrNull() }
        }.firstOrNull { m ->
            m.parameterTypes[0] == Uri::class.java &&
                m.parameterTypes[1].isAssignableFrom(probe.javaClass)
        } ?: throw IllegalStateException("no Uri -> ${messageType.name} builder")

        val descriptorType = build.parameterTypes[2]
        val descriptorFactory = Unobfuscator.withDexKitBridge { bridge ->
            bridge.findMethod {
                matcher {
                    paramCount(2)
                    returnType = descriptorType.name
                }
            }.mapNotNull { data -> runCatching { data.getMethodInstance(loader) }.getOrNull() }
        }.firstOrNull { m ->
            m.parameterTypes[0] == String::class.java && m.parameterTypes[1].name == "boolean"
        } ?: throw IllegalStateException("no descriptor factory for ${descriptorType.name}")

        val setFile = runCatching { descriptorType.declaredMethods }.getOrNull().orEmpty()
            .firstOrNull { m ->
                m.parameterCount == 1 &&
                    m.parameterTypes[0] == File::class.java &&
                    m.returnType == Void.TYPE
            }
            ?: throw IllegalStateException("no setFile(File) on ${descriptorType.name}")

        // A placeholder for "no quoted message" — the builder dereferences it.
        val quoteCtor = runCatching { build.parameterTypes[3].getDeclaredConstructor() }
            .getOrElse {
                throw IllegalStateException("no default ${build.parameterTypes[3].name}")
            }

        YukiLog.log(
            "[ScheduledMessages] media send: ${sendClass.name}#${transmit.name}, " +
                "builder=${build.declaringClass.name}#${build.name}, " +
                "descriptor=${descriptorFactory.declaringClass.name}#${descriptorFactory.name}"
        )

        return Api(
            transmit = transmit,
            transmitInstance = instantiate(sendClass),
            build = build,
            buildInstance = instantiate(build.declaringClass),
            descriptorFactory = descriptorFactory,
            descriptorInstance = instantiate(descriptorFactory.declaringClass),
            setFile = setFile,
            quoteCtor = quoteCtor.also { runCatching { it.isAccessible = true } }
        )
    }

    private fun instantiate(cls: Class<*>): Any =
        cls.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
}
