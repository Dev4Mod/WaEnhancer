package com.wmods.wppenhacer.xposed.features.general

import android.content.ContentValues
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteTransactionListener
import android.os.Bundle
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.hook.core.YukiHookCreator
import com.highcapable.yukihookapi.hook.param.HookParam
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.homeActivityClass
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadChatLimitDelete2Method
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadChatLimitDeleteMethod
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadEphemeralInsertdb
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadFmessageTimestampField
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadSeeMoreConstructor
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import java.lang.ref.WeakReference

class ChatLimit(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    // WhatsApp's own msgstore connection; writing through it queues behind WhatsApp's
    // transactions instead of racing them for the file lock like a second connection does
    @Volatile
    private var msgStoreDb: WeakReference<SQLiteDatabase>? = null

    override fun doHook() {
        val antiDisappearing = xprefs.getBoolean("antidisappearing", false)
        val revokeallmessages = xprefs.getBoolean("revokeallmessages", false)

        val chatLimitDeleteMethod = loadChatLimitDeleteMethod(classLoader)
        val chatLimitDelete2Method = loadChatLimitDelete2Method(classLoader)
        val fmessageTimestampMethod = loadFmessageTimestampField(classLoader)

        val epUpdateMethod = loadEphemeralInsertdb(classLoader)

        if (antiDisappearing) {
            captureMsgStoreDatabase()
            homeActivityClass.resolve().firstMethod {
                name = "onCreate"
                superclass()
                parameters(Bundle::class.java)
            }.hook {
                after { keepEphemeralMessages() }
            }
        }

        epUpdateMethod.hook {
            after {
                if (antiDisappearing) {
                    val contentValues = result as ContentValues
                    contentValues.put("expire_timestamp", KEEP_EXPIRE_TIMESTAMP)
                }
            }
        }

        if (revokeallmessages) {
            var unhooked: YukiHookCreator.MemberHooker<HookParam>.Handle? = null

            chatLimitDelete2Method.hook {
                before {
                    val list = ReflectionUtils.findInstancesOfType(
                        args,
                        MutableSet::class.java
                    )
                    if (list.isEmpty()) return@before
                    val listMessages = list[0]!!.second
                    var isExpired = false
                    for (fmessageObj in listMessages) {
                        val timestamp = fmessageTimestampMethod.getLong(fmessageObj)
                        // verify message is expired (max: 3 days)
                        if (System.currentTimeMillis() - timestamp > 3 * 24 * 60 * 60 * 1000) {
                            isExpired = true
                            break
                        }
                    }
                    if (!isExpired) {
                        unhooked = chatLimitDeleteMethod.hook {
                            after {
                                if (ReflectionUtils.isCalledFromMethod(chatLimitDelete2Method)) {
                                    result = 0L
                                }
                            }
                        }
                    }
                }

                after {
                    unhooked?.remove()
                }
            }
        }

        val seeMoreMethod = loadSeeMoreConstructor(classLoader)

        if (!xprefs.getBoolean("removeseemore", false)) return

        seeMoreMethod.hook {
            before {
                args[1] = Int.MAX_VALUE
            }
        }
    }

    private fun captureMsgStoreDatabase() {
        var handle: YukiHookCreator.MemberHooker<HookParam>.Handle? = null
        handle = SQLiteDatabase::class.java.resolve().firstMethod {
            name = "beginTransactionWithListener"
            parameters(SQLiteTransactionListener::class.java)
        }.hook {
            before {
                if (msgStoreDb != null) return@before
                val database = instance as SQLiteDatabase
                if (database.path?.endsWith("/msgstore.db") != true) return@before
                msgStoreDb = WeakReference(database)
                handle?.remove()
                keepEphemeralMessages()
            }
        }
    }

    private fun keepEphemeralMessages() {
        val database = msgStoreDb?.get() ?: return
        Utils.databaseExecutor.execute {
            try {
                if (!database.isOpen) return@execute
                database.execSQL(
                    "UPDATE message_ephemeral SET expire_timestamp = $KEEP_EXPIRE_TIMESTAMP " +
                            "WHERE expire_timestamp IS NULL OR expire_timestamp != $KEEP_EXPIRE_TIMESTAMP"
                )
            } catch (e: Exception) {
                YukiLog.log(e)
            }
        }
    }

    override fun getPluginName(): String {
        return "Chat Limit"
    }

    companion object {
        private const val KEEP_EXPIRE_TIMESTAMP = 2553512370000L
    }
}
