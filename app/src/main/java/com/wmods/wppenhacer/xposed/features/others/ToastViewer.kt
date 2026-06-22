package com.wmods.wppenhacer.xposed.features.others

import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import android.widget.Toast
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
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ToastViewer(classLoader: ClassLoader, preferences: XSharedPreferences) :
    Feature(classLoader, preferences) {
    init {
        startCleanupTask()
    }

    override fun doHook() {
        val toastViewedStatus = prefs.getBoolean("toast_viewed_status", false)
        val toastViewedMessage = prefs.getBoolean("toast_viewed_message", false)

        val onInsertReceipt = loadOnInsertReceipt(classLoader)

        XposedBridge.hookMethod(onInsertReceipt, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                processNewWA(param, toastViewedMessage, toastViewedStatus)
            }
        })
        val onSeenReceiptForStatus = loadSeenReceiptForStatus(classLoader)
        XposedBridge.hookMethod(onSeenReceiptForStatus, object : XC_MethodHook() {

            override fun beforeHookedMethod(param: MethodHookParam) {
                val receiptType = param.args[1] as Int
                if (receiptType != 13) return
                val fStatusField = ReflectionUtils.findFieldUsingFilter(param.thisObject.javaClass) {
                    f -> FStatusWpp.TYPE.isAssignableFrom(f.type)
                }
                val fStatus = FStatusWpp(fStatusField.get(param.thisObject))
                if (!fStatus.fStatusKey.isFromMe) return
                val userjid = UserJid(param.args[0])
                val waContactWpp = getWaContactFromJid(userjid)
                val contactName = waContactWpp!!.displayName
                if (toastViewedStatus) {
                    Utils.showToast(
                        Utils.application.getString(R.string.viewed_your_status, contactName),
                        Toast.LENGTH_LONG
                    )
                }
                Tasker.sendTaskerEvent(contactName, userjid.phoneNumber, "viewed_status")
            }
        })
    }

    @Throws(Exception::class)
    private fun processNewWA(
        param: MethodHookParam,
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
            if (type != 13) return
            val userJid = UserJid(fieldByUserJid!!.get(messageStatusUpdateReceipt))
            val fmessage = AtomicReference<Any?>()
            try {
                fmessage.set(fieldMessage!!.get(messageStatusUpdateReceipt))
            } catch (_: Exception) {
            }
            CompletableFuture.runAsync {
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

    @Synchronized
    private fun checkDataBase(
        sql: SQLiteDatabase,
        id: Long,
        contactName: String?,
        rawJid: String?,
        toastViewedMessage: Boolean,
        toastViewedStatus: Boolean
    ) {
        sql.query("message", null, "_id = ?", arrayOf(id.toString()), null, null, null)
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
                        null,
                        "_id = ? AND subject IS NULL",
                        arrayOf(chatId.toString()),
                        null,
                        null,
                        null
                    ).use { result3 ->
                        if (!result3.moveToNext()) return
                        val key = rawJid + "_" + "viewed_message"
                        val currentTime = System.currentTimeMillis()
                        val lastEventTime: Long? = lastEventTimeMap[key]
                        if (lastEventTime == null || (currentTime - lastEventTime) >= MIN_INTERVAL) {
                            lastEventTimeMap[key] = currentTime
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
                    XposedBridge.log(e)
                }
            }
    }

    private fun startCleanupTask() {
        scheduler.scheduleWithFixedDelay({
            val currentTime = System.currentTimeMillis()
            synchronized(lastEventTimeMap) {
                lastEventTimeMap.entries.removeIf { entry: MutableMap.MutableEntry<String?, Long?>? -> (currentTime - entry!!.value!!) >= MIN_INTERVAL }
            }
        }, MIN_INTERVAL, MIN_INTERVAL, TimeUnit.MILLISECONDS)
    }

    companion object {
        private const val MIN_INTERVAL: Long = 1000
        private val lastEventTimeMap: MutableMap<String?, Long?> = HashMap()
        private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    }
}
