package com.wmods.wppenhacer.xposed.features.general

import android.content.ContentValues
import android.content.SharedPreferences
import android.os.Bundle
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.homeActivityClass
import com.wmods.wppenhacer.xposed.core.db.MessageStore.Companion.getInstance
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadChatLimitDelete2Method
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadChatLimitDeleteMethod
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadEphemeralInsertdb
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadFmessageTimestampField
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadSeeMoreConstructor
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils

class ChatLimit(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        val antiDisappearing = xprefs.getBoolean("antidisappearing", false)
        val revokeallmessages = xprefs.getBoolean("revokeallmessages", false)

        val chatLimitDeleteMethod = loadChatLimitDeleteMethod(classLoader)
        val chatLimitDelete2Method = loadChatLimitDelete2Method(classLoader)
        val fmessageTimestampMethod = loadFmessageTimestampField(classLoader)

        val epUpdateMethod = loadEphemeralInsertdb(classLoader)

        homeActivityClass.resolve().firstMethod {
            name = "onCreate"
            superclass()
            parameters(Bundle::class.java)
        }.hook {
            after {
                if (antiDisappearing) {
                    Utils.databaseExecutor.execute {
                        getInstance().executeWritableSQL("UPDATE message_ephemeral SET expire_timestamp = 2553512370000")
                    }
                }
            }
        }

        epUpdateMethod.hook {
            after {
                if (antiDisappearing) {
                    val contentValues = result as ContentValues
                    contentValues.put("expire_timestamp", 2553512370000L)
                }
            }
        }

        if (revokeallmessages) {
            var unhooked: YukiMemberHookCreator.MemberHookCreator.Result? = null

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

        seeMoreMethod.hook {
            before {
                if (!xprefs.getBoolean("removeseemore", false)) return@before
                args[1] = Int.MAX_VALUE
            }
        }
    }

    override fun getPluginName(): String {
        return "Chat Limit"
    }
}
