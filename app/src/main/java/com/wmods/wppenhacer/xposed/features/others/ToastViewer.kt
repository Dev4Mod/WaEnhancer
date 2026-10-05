package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import android.widget.Toast
import com.highcapable.yukihookapi.hook.param.HookParam
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.getContactName
import com.wmods.wppenhacer.xposed.core.WppCore.getCurrentUserJid
import com.wmods.wppenhacer.xposed.core.WppCore.stripJID
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp.UserJid
import com.wmods.wppenhacer.xposed.core.components.FStatusWpp
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp.Companion.getWaContactFromJid
import com.wmods.wppenhacer.xposed.core.db.MessageStore.Companion.getInstance
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.findFirstClassUsingName
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadOnInsertReceipt
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadSeenReceiptForStatus
import com.wmods.wppenhacer.xposed.features.general.Tasker
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ToastViewer(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        val toastViewedMessage = xprefs.getBoolean("toast_viewed_message", false)
        val toastViewedStatus = xprefs.getBoolean("toast_viewed_status", false)
        if (!toastViewedMessage && !toastViewedStatus) {
            return
        }

        startCleanupTask()

        val onInsertReceipt = loadOnInsertReceipt(classLoader)

        onInsertReceipt.hook {
            before {
                processNewWA(this, toastViewedMessage, toastViewedStatus)
            }
        }
        val onSeenReceiptForStatus = loadSeenReceiptForStatus(classLoader)
        onSeenReceiptForStatus.hook {
            before {
                val receiptType = args.filterIsInstance<Int>().first()
                if (receiptType != 13) return@before
                val fStatusObject = args.firstOrNull { FStatusWpp.TYPE.isInstance(it) }
                    ?: runCatching {
                        val fStatusField =
                            ReflectionUtils.findFieldUsingFilter(instance.javaClass) { f ->
                                FStatusWpp.TYPE.isAssignableFrom(f.type)
                            }
                        fStatusField.get(instance)
                    }.getOrNull()
                    ?: return@before
                val fStatus = FStatusWpp(fStatusObject)
                if (!fStatus.fStatusKey.isFromMe) return@before
                val userjid = UserJid(args[0])
                val contactName = getWaContactFromJid(userjid)?.displayName
                    ?: getContactName(userjid)
                if (toastViewedStatus) {
                    Utils.showToast(
                        Utils.application.getString(R.string.viewed_your_status, contactName),
                        Toast.LENGTH_LONG
                    )
                }
                Tasker.sendTaskerEvent(contactName, userjid.phoneNumber, "viewed_status")
            }
        }
    }

    @Throws(Exception::class)
    private fun processNewWA(
        param: HookParam,
        toastViewedMessage: Boolean,
        toastViewedStatus: Boolean
    ) {
        val collection = if (param.args[0] !is MutableCollection<*>) {
            mutableSetOf<Any?>(param.args[0])
        } else {
            param.args[0] as MutableCollection<*>
        }
        val jidClass = findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.Jid")
        for (messageStatusUpdateReceipt in collection) {
            val fieldByType = ReflectionUtils.getFieldByType(
                messageStatusUpdateReceipt!!.javaClass,
                Int::class.javaPrimitiveType
            )
            val fieldId = ReflectionUtils.getFieldByType(
                messageStatusUpdateReceipt.javaClass,
                Long::class.javaPrimitiveType
            )
            val fieldByUserJid = ReflectionUtils.getFieldByExtendType(
                messageStatusUpdateReceipt.javaClass,
                jidClass
            )
            val fieldMessage = ReflectionUtils.getFieldByExtendType(
                messageStatusUpdateReceipt.javaClass,
                FMessageWpp.TYPE
            )
            val type = fieldByType!!.getInt(messageStatusUpdateReceipt)
            val id = fieldId!!.getLong(messageStatusUpdateReceipt)
            if (type != 13) continue
            val userJid = UserJid(fieldByUserJid!!.get(messageStatusUpdateReceipt))
            val fmessage = AtomicReference<Any?>()
            try {
                fmessage.set(fieldMessage!!.get(messageStatusUpdateReceipt))
            } catch (_: Exception) {
            }
            Utils.databaseExecutor.execute {
                var contactName: String? = getContactName(userJid)
                var rowId = id

                if (TextUtils.isEmpty(contactName)) contactName = userJid.phoneNumber

                val sql = getInstance().getDatabase()

                if (fmessage.get() != null) {
                    rowId = FMessageWpp(fmessage.get()).rowId
                }
                checkDataBase(
                    sql!!,
                    rowId,
                    contactName,
                    userJid.phoneRawString,
                    toastViewedMessage,
                    toastViewedStatus
                )
            }
        }
    }


    override fun getPluginName(): String {
        return "Toast Viewer"
    }

    private fun checkDataBase(
        sql: SQLiteDatabase,
        id: Long,
        contactName: String?,
        rawJid: String?,
        toastViewedMessage: Boolean,
        toastViewedStatus: Boolean
    ) {
        sql.query(
            "message",
            arrayOf("participant_hash", "chat_row_id"),
            "_id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
            .use { result2 ->
                if (!result2.moveToNext()) return
                val participantHash =
                    result2.getString(result2.getColumnIndexOrThrow("participant_hash"))
                if (participantHash != null) {
                    if (toastViewedStatus) {
                        Utils.showToast(
                            Utils.application
                                .getString(R.string.viewed_your_status, contactName),
                            Toast.LENGTH_LONG
                        )
                    }
                    Tasker.sendTaskerEvent(contactName, stripJID(rawJid), "viewed_status")
                    return
                }

                val userJid = getCurrentUserJid()

                if (rawJid != null && userJid != null && userJid.phoneRawString == rawJid) return

                val chatId = result2.getLong(result2.getColumnIndexOrThrow("chat_row_id"))
                try {
                    sql.query(
                        "chat",
                        arrayOf("_id"),
                        "_id = ? AND subject IS NULL",
                        arrayOf(chatId.toString()),
                        null,
                        null,
                        null
                    ).use { result3 ->
                        if (!result3.moveToNext()) return
                        val key = rawJid + "_" + "viewed_message"
                        val currentTime = System.currentTimeMillis()
                        val shouldEmit = synchronized(lastEventTimeMap) {
                            val lastEventTime = lastEventTimeMap[key]
                            if (lastEventTime == null || (currentTime - lastEventTime) >= MIN_INTERVAL) {
                                lastEventTimeMap[key] = currentTime
                                true
                            } else {
                                false
                            }
                        }
                        if (shouldEmit) {
                            Tasker.sendTaskerEvent(contactName, stripJID(rawJid), "viewed_message")
                            if (toastViewedMessage) {
                                Utils.showToast(
                                    Utils.application
                                        .getString(R.string.viewed_your_message, contactName),
                                    Toast.LENGTH_LONG
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    YukiLog.log(e)
                }
            }
    }

    private fun startCleanupTask() {
        if (!cleanupStarted.compareAndSet(false, true)) return
        scheduler.scheduleWithFixedDelay({
            val currentTime = System.currentTimeMillis()
            synchronized(lastEventTimeMap) {
                lastEventTimeMap.entries.removeIf { entry ->
                    currentTime - entry.value >= MIN_INTERVAL
                }
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL, TimeUnit.SECONDS)
    }

    companion object {
        private const val MIN_INTERVAL: Long = 1000
        private const val CLEANUP_INTERVAL: Long = 30
        private val cleanupStarted = AtomicBoolean(false)
        private val lastEventTimeMap = ConcurrentHashMap<String, Long>()
        private val scheduler: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "WaEnhancer-ToastViewerCleanup").apply {
                    isDaemon = true
                }
            }
    }
}
