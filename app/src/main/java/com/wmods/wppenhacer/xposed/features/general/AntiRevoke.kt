package com.wmods.wppenhacer.xposed.features.general

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.highcapable.yukihookapi.hook.param.HookParam
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.FStatusWpp
import com.wmods.wppenhacer.xposed.core.components.StatusItemWpp
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.db.DelMessageStore
import com.wmods.wppenhacer.xposed.core.db.MessageStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.text.DateFormat
import java.util.Collections
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class AntiRevoke(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private const val FIELD_ORIGINAL_DATE_TEXT = "originalMessage"
        private const val FIELD_DECORATED_DATE_TEXT = "decoratedMessage"

        private val messageRevokedMap = ConcurrentHashMap<String, MutableSet<String>>()

        private val dateFormatThreadLocal = ThreadLocal.withInitial {
            DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                Utils.application.resources.configuration.locales[0]
            )
        }

        private const val ORIGINAL_KEY_RECHECK_INTERVAL_MS = 30_000L
        private const val MISSING_ORIGINAL_KEY_CACHE_SIZE = 2048

        private val originalMessageKeyCache = ConcurrentHashMap<Long, String>()
        private val missingOriginalKeyCheckedAt =
            LruCache<Long, Long>(MISSING_ORIGINAL_KEY_CACHE_SIZE)
        private val revokedTimestampCache = ConcurrentHashMap<String, Long>()
        private val loadedRevokedJids = ConcurrentHashMap.newKeySet<String>()
        private val loadingRevokedJids = ConcurrentHashMap.newKeySet<String>()

        private fun blockedStatusRevokeResult(returnType: Class<*>): Any? {
            return when {
                Enum::class.java.isAssignableFrom(returnType) -> {
                    val constants = returnType.declaredFields
                        .filter { Modifier.isStatic(it.modifiers) && it.type == returnType }
                        .mapNotNull { field ->
                            field.isAccessible = true
                            field.get(null) as? Enum<*>
                        }
                        .sortedBy { it.ordinal }
                    (constants.firstOrNull { it.name == "DIDNT_STORE" }
                        ?: constants.getOrNull(3)
                        ?: constants.lastOrNull())
                }

                returnType == Int::class.javaPrimitiveType || returnType == Int::class.javaObjectType -> 0
                returnType == Boolean::class.javaPrimitiveType || returnType == Boolean::class.javaObjectType -> false
                else -> null
            }
        }

        private fun blockedMessageRevokeResult(returnType: Class<*>): Any? {
            val constructor = returnType.constructors[0]
            val params = ReflectionUtils.initArray(constructor.parameterTypes)
            constructor.parameterTypes.forEachIndexed { index, type ->
                if (type.isPrimitive) return@forEachIndexed
                try {
                    val noneClass = Unobfuscator.loadNoneIncomingAckClass(
                        returnType.classLoader ?: Utils.application.classLoader,
                        type
                    )
                    params[index] = noneClass.declaredFields
                        .first { it.type == noneClass && Modifier.isStatic(it.modifiers) }
                        .apply { isAccessible = true }
                        .get(null)
                } catch (_: Throwable) {
                }
            }
            return constructor.newInstance(*params)
        }

        private fun findObjectFMessage(param: HookParam): FMessageWpp? {
            val safeArgs = param.args?.filterNotNull() ?: return null
            safeArgs.firstOrNull { FMessageWpp.TYPE.isInstance(it) }?.let { return FMessageWpp(it) }
            val arg0 = param.args?.getOrNull(0) ?: return null
            val statusItem = StatusItemWpp.from(arg0) ?: return null
            return statusItem.fMessage
        }


        private fun getRevokedMessagesForJid(fMessage: FMessageWpp): MutableSet<String> {
            val stripJID =
                fMessage.key.remoteJid.phoneNumber ?: return Collections.synchronizedSet(HashSet())
            return messageRevokedMap.computeIfAbsent(stripJID) {
                Collections.synchronizedSet(HashSet())
            }
        }

        private fun loadRevokedMessagesForJid(fMessage: FMessageWpp) {
            val stripJID = fMessage.key.remoteJid.phoneNumber ?: return
            if (loadedRevokedJids.contains(stripJID)) return
            getRevokedMessagesForJid(fMessage).addAll(
                DelMessageStore.getInstance(Utils.application).getMessagesByJid(stripJID)
            )
            loadedRevokedJids.add(stripJID)
        }

        private fun isOriginalKeyLookupFresh(rowId: Long): Boolean {
            if (originalMessageKeyCache.containsKey(rowId)) return true
            val checkedAt = missingOriginalKeyCheckedAt.get(rowId) ?: return false
            return SystemClock.elapsedRealtime() - checkedAt < ORIGINAL_KEY_RECHECK_INTERVAL_MS
        }

        private fun lookupOriginalMessageKey(rowId: Long): String? {
            originalMessageKeyCache[rowId]?.let { return it }
            if (isOriginalKeyLookupFresh(rowId)) return null
            val originalKey = MessageStore.getInstance().getOriginalMessageKey(rowId)
            if (originalKey.isEmpty()) {
                missingOriginalKeyCheckedAt.put(rowId, SystemClock.elapsedRealtime())
                return null
            }
            originalMessageKeyCache[rowId] = originalKey
            missingOriginalKeyCheckedAt.remove(rowId)
            return originalKey
        }

        private fun ensureRevokedMessagesLoaded(fMessage: FMessageWpp) {
            val stripJID = fMessage.key.remoteJid.phoneNumber ?: return
            if (loadedRevokedJids.contains(stripJID) || !loadingRevokedJids.add(stripJID)) return
            Utils.databaseExecutor.execute {
                try {
                    loadRevokedMessagesForJid(fMessage)
                } catch (t: Throwable) {
                    YukiLog.log(t)
                } finally {
                    loadingRevokedJids.remove(stripJID)
                }
            }
        }

        private fun persistRevokedMessage(fMessage: FMessageWpp, messageID: String) {
            val stripJID = fMessage.key.remoteJid.phoneNumber!!
            val messages = getRevokedMessagesForJid(fMessage)
            messages.add(messageID)
            DelMessageStore.getInstance(Utils.application).insertMessage(
                stripJID,
                messageID,
                System.currentTimeMillis()
            )
        }
    }

    private class RevocationAlertData(
        val remoteJid: FMessageWpp.UserJid,
        val userJid: FMessageWpp.UserJid,
        val deletedAdminUser: Any?
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun doHook() {
        val antiRevokeMessageMethod = Unobfuscator.loadAntiRevokeMessageMethod(classLoader)
        val unknownStatusPlaybackMethod = Unobfuscator.loadUnknownStatusPlaybackMethod(classLoader)
        val statusPlaybackClass = Unobfuscator.loadStatusPlaybackViewClass(classLoader)
        val antiRevokeFStatusMethod = Unobfuscator.loadAntiRevokeFStatusMethod(classLoader)

        antiRevokeFStatusMethod.hook {
            before {
                val targetKey = args.lastOrNull { FStatusWpp.FStatusKey.TYPE.isInstance(it) }
                    ?: return@before
                val fStatusKey = FStatusWpp.FStatusKey(targetKey)
                val fstatus = fStatusKey.fStatus ?: return@before
                val fMessage = fstatus.fMessage ?: return@before
                if (!fStatusKey.isFromMe && handleRevocationAttempt(
                        fMessage,
                        fStatusKey.messageID
                    ) != 0
                ) {
                    result = blockedStatusRevokeResult((method as Method).returnType)
                }
            }
        }

        antiRevokeMessageMethod!!.hook {
            before {
                val args = args ?: return@before
                val fMessageObj = ReflectionUtils.getArg(args, FMessageWpp.TYPE, 0)
                if (fMessageObj == null) {
                    logDebug("FMessageObj is null in revoke!")
                    return@before
                }
                val fMessage = FMessageWpp(fMessageObj)
                val messageKey = fMessage.key
                val deviceJid = fMessage.deviceJid
                val messageId =
                    ReflectionUtils.getObjectField(fMessage.getObject(), "A01") as String


                val shouldIntercept = if (messageKey.remoteJid.isGroup) {
                    deviceJid != null && handleRevocationAttempt(fMessage, messageId) != 0
                } else {
                    !messageKey.isFromMe && handleRevocationAttempt(fMessage, messageId) != 0
                }
                if (shouldIntercept) {
                    val method = method as Method
                    result = if (method.returnType != Boolean::class.javaPrimitiveType && method.returnType != Boolean::class.javaObjectType) {
                        blockedMessageRevokeResult(method.returnType)
                    } else {
                        true
                    }
                }
            }
        }

        ConversationItemListener.conversationListeners.add(object :
            ConversationItemListener.OnConversationItemListener() {
            override fun onItemBind(
                fMessage: FMessageWpp,
                view: ViewGroup,
                position: Int,
                convertView: View?
            ) {
                val dateTextView = view.findViewById<TextView>(Utils.getID("date", "id"))
                bindRevokedMessageUI(fMessage, dateTextView, "antirevoke", view)
            }
        })

        unknownStatusPlaybackMethod.hook {
            after {
                val obj = ReflectionUtils.getArg(args, method.declaringClass, 0)
                val fMessage = findObjectFMessage(this)
                val field =
                    ReflectionUtils.getFieldByType(method.declaringClass, statusPlaybackClass)

                if (obj == null || field == null || fMessage == null) {
                    logDebug("Invalid parameters")
                    return@after
                }

                val objView = field.get(obj) ?: return@after
                val textViews =
                    ReflectionUtils.getFieldsByType(statusPlaybackClass, TextView::class.java)

                if (textViews.isEmpty()) {
                    logDebug("No text views found")
                    return@after
                }

                val dateId = Utils.getID("date", "id")
                for (textViewField in textViews) {
                    val textView = textViewField.get(objView) as? TextView
                    if (textView != null && textView.id == dateId) {
                        bindRevokedMessageUI(fMessage, textView, "antirevokestatus")
                        break
                    }
                }
            }
        }
    }

    private fun bindRevokedMessageUI(
        fMessage: FMessageWpp,
        dateTextView: TextView?,
        antirevokeType: String,
        boundView: View? = null
    ) {
        if (dateTextView == null) return
        val antirevokeValue = xprefs.getString(antirevokeType, "0")?.toIntOrNull() ?: 0
        if (antirevokeValue == 0) return

        val key = fMessage.key
        val boundMessageId = key.messageID

        dateTextView.paint.isUnderlineText = false
        dateTextView.setOnClickListener(null)
        dateTextView.setCompoundDrawables(null, null, null, null)
        restoreDateText(dateTextView)

        val stripJid = key.remoteJid.phoneNumber ?: return
        val rowId = fMessage.rowId
        val messageRevokedList = getRevokedMessagesForJid(fMessage)
        val cachedMessageId = if (messageRevokedList.contains(boundMessageId)) {
            boundMessageId
        } else {
            originalMessageKeyCache[rowId]?.takeIf { messageRevokedList.contains(it) }
        }

        if (cachedMessageId != null) {
            val cachedTimestamp = revokedTimestampCache[cachedMessageId]
            if (cachedTimestamp != null) {
                applyRevokedMessageUI(
                    dateTextView,
                    boundView,
                    boundMessageId,
                    antirevokeValue,
                    formatRevokedDate(cachedTimestamp)
                )
                return
            }
        } else if (loadedRevokedJids.contains(stripJid) && isOriginalKeyLookupFresh(rowId)) {
            return
        }

        Utils.databaseExecutor.execute {
            try {
                loadRevokedMessagesForJid(fMessage)
                val messageId = when {
                    messageRevokedList.contains(boundMessageId) -> boundMessageId
                    else -> lookupOriginalMessageKey(rowId)?.takeIf {
                        messageRevokedList.contains(it)
                    }
                } ?: return@execute

                val timestamp = revokedTimestampCache[messageId] ?: run {
                    val loadedTimestamp = DelMessageStore.getInstance(Utils.application)
                        .getTimestampByMessageId(messageId)
                    if (loadedTimestamp > 0) {
                        revokedTimestampCache[messageId] = loadedTimestamp
                    }
                    loadedTimestamp
                }
                val date = formatRevokedDate(timestamp)

                mainHandler.post {
                    if (boundView != null && !ConversationItemListener.isViewBoundToMessage(
                            boundView,
                            boundMessageId
                        )
                    ) {
                        return@post
                    }
                    applyRevokedMessageUI(
                        dateTextView,
                        boundView,
                        boundMessageId,
                        antirevokeValue,
                        date
                    )
                }
            } catch (t: Throwable) {
                logDebug(t)
            }
        }
    }

    private fun restoreDateText(dateTextView: TextView) {
        val originalText = ReflectionUtils.getAdditionalInstanceField(
            dateTextView,
            FIELD_ORIGINAL_DATE_TEXT
        ) as? String
        val decoratedText = ReflectionUtils.getAdditionalInstanceField(
            dateTextView,
            FIELD_DECORATED_DATE_TEXT
        ) as? String
        // WhatsApp rewrites the date when it binds a message, so any other text is already fresh.
        if (originalText != null && dateTextView.text?.toString() == decoratedText) {
            dateTextView.text = originalText
        }
        ReflectionUtils.removeAdditionalInstanceField(dateTextView, FIELD_ORIGINAL_DATE_TEXT)
        ReflectionUtils.removeAdditionalInstanceField(dateTextView, FIELD_DECORATED_DATE_TEXT)
    }

    private fun decorateDateText(dateTextView: TextView) {
        val currentText = dateTextView.text?.toString().orEmpty()
        val decoratedText = ReflectionUtils.getAdditionalInstanceField(
            dateTextView,
            FIELD_DECORATED_DATE_TEXT
        ) as? String
        if (currentText == decoratedText) return

        val newTextData = "${
            UnobfuscatorCache.getInstance().getString("messagedeleted")
        } | $currentText"
        dateTextView.text = newTextData
        ReflectionUtils.setAdditionalInstanceField(
            dateTextView,
            FIELD_ORIGINAL_DATE_TEXT,
            currentText
        )
        ReflectionUtils.setAdditionalInstanceField(
            dateTextView,
            FIELD_DECORATED_DATE_TEXT,
            newTextData
        )
    }

    private fun formatRevokedDate(timestamp: Long): String? {
        return if (timestamp > 0) {
            dateFormatThreadLocal.get()?.format(Date(timestamp))
        } else {
            null
        }
    }

    private fun applyRevokedMessageUI(
        dateTextView: TextView,
        boundView: View?,
        boundMessageId: String,
        antirevokeValue: Int,
        date: String?
    ) {
        if (date != null) {
            dateTextView.paint.isUnderlineText = true
            dateTextView.setOnClickListener {
                if (boundView != null && !ConversationItemListener.isViewBoundToMessage(
                        boundView,
                        boundMessageId
                    )
                ) return@setOnClickListener
                val toastMessage =
                    Utils.application.getString(R.string.message_removed_on)
                        .format(date)
                Utils.showToast(toastMessage, Toast.LENGTH_LONG)
            }
        }

        when (antirevokeValue) {
            1 -> decorateDateText(dateTextView)

            2 -> {
                val drawable = Utils.application.getDrawable(R.drawable.deleted)
                dateTextView.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    null,
                    drawable,
                    null
                )
                dateTextView.compoundDrawablePadding = 5
            }
        }
    }

    private fun handleRevocationAttempt(fMessage: FMessageWpp, messageId: String): Int {
        try {
            val alertData = captureRevocationAlertData(fMessage)
            Utils.databaseExecutor.execute {
                try {
                    handleRevocationAlert(alertData)
                } catch (t: Throwable) {
                    log(t)
                }
            }
        } catch (e: Exception) {
            log(e)
        }

        val revokeBoolean = xprefs.getString(
            if (fMessage.key.remoteJid.isStatus) "antirevokestatus" else "antirevoke",
            "0"
        )?.toIntOrNull() ?: 0

        if (revokeBoolean == 0) return 0

        ensureRevokedMessagesLoaded(fMessage)
        val messageRevokedList = getRevokedMessagesForJid(fMessage)
        if (!messageRevokedList.contains(messageId)) {
            messageRevokedList.add(messageId)
            missingOriginalKeyCheckedAt.evictAll()
            Utils.databaseExecutor.execute {
                try {
                    persistRevokedMessage(fMessage, messageId)
                    val mConversation = WppCore.getCurrentConversation()
                    if (mConversation != null && fMessage.key.remoteJid.phoneNumber == WppCore.getCurrentUserJid()?.phoneNumber) {
                        mConversation.runOnUiThread {
                            ConversationItemListener.notifyDataSetChanged()
                        }
                    }
                } catch (e: Exception) {
                    logDebug(e)
                }
            }
        }
        return revokeBoolean
    }

    private fun captureRevocationAlertData(fMessage: FMessageWpp): RevocationAlertData {
        val remoteJid = fMessage.key.remoteJid
        val userJid = fMessage.userJid
        val deletedAdminUser = if (remoteJid.isGroup && userJid.isNull) {
            ReflectionUtils.getObjectField(fMessage.getObject(), "A00")
        } else {
            null
        }
        return RevocationAlertData(remoteJid, userJid, deletedAdminUser)
    }

    private fun formatRevocationMessage(alertData: RevocationAlertData): String? {
        var jidAuthor = alertData.remoteJid
        var messageSuffix = Utils.application.getString(R.string.deleted_message)

        if (jidAuthor.isStatus) {
            messageSuffix = Utils.application.getString(R.string.deleted_status)
            jidAuthor = alertData.userJid
        }
        val waContact = WaContactWpp.getWaContactFromJid(jidAuthor)

        val name = waContact?.displayName
            ?: jidAuthor.phoneNumber

        return if (jidAuthor.isGroup) {
            var participantJid = alertData.userJid
            if (participantJid.isNull && alertData.deletedAdminUser != null) {
                participantJid = FMessageWpp.UserJid(alertData.deletedAdminUser)
            }
            val participantWaContact = WaContactWpp.getWaContactFromJid(participantJid)

            val participantName = participantWaContact?.displayName
                ?: participantJid.phoneNumber

            Utils.application
                .getString(R.string.deleted_a_message_in_group, participantName, name)
        } else {
            "$name $messageSuffix"
        }
    }

    private fun handleRevocationAlert(alertData: RevocationAlertData) {
        val message = formatRevocationMessage(alertData) ?: return

        val jidAuthor = alertData.remoteJid
        val actualAuthor = if (jidAuthor.isStatus) alertData.userJid else jidAuthor
        val waContact = WaContactWpp.getWaContactFromJid(actualAuthor)

        val name = waContact?.displayName ?: actualAuthor.phoneNumber

        val taskerAction = if (jidAuthor.isStatus) "deleted_status" else "deleted_message"

        if (xprefs.getBoolean("toastdeleted", false)) {
            Utils.showToast(message, Toast.LENGTH_LONG)
        }

        Tasker.sendTaskerEvent(name, jidAuthor.phoneNumber, taskerAction)
    }

    override fun getPluginName(): String = "Anti Revoke"
}
