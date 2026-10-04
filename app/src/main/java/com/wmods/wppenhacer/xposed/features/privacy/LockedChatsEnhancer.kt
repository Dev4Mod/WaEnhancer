package com.wmods.wppenhacer.xposed.features.privacy

import android.content.SharedPreferences
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp.UserJid
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadChatCacheClass
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadLoadedContactsMethod
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadLockedChatsMethod
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadNotificationMethod
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import java.util.stream.Collectors

class LockedChatsEnhancer(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {
    private var chatCache: Any? = null

    override fun doHook() {
        if (!xprefs.getBoolean("lockedchats_enhancer", false)) return

        val jidNotifications = loadNotificationMethod(classLoader)
        val lockedChatsMethod = loadLockedChatsMethod(classLoader)
        val suppressLockedChats = ThreadLocal.withInitial { false }

        jidNotifications.hook {
            before {
                suppressLockedChats.set(true)
            }
            after {
                suppressLockedChats.remove()
            }
        }

        lockedChatsMethod.hook {
            before {
                if (suppressLockedChats.get() == true) {
                    result = (ArrayList<Any?>())
                }
            }
        }

        val chatCacheClass = loadChatCacheClass(classLoader)
        val lockedChatsFields = ReflectionUtils.findAllFieldsUsingFilter(chatCacheClass) { f ->
            f.type == HashSet::class.java
        }

        chatCacheClass.resolve().constructor { }.hookAll {
            before {
                chatCache = instance
            }
        }

        val loadedContacts = loadLoadedContactsMethod(classLoader)

        loadedContacts!!.hook {
            before {
                val listField = args[0]?.javaClass?.declaredFields?.firstOrNull {
                    List::class.java.isAssignableFrom(it.type)
                } ?: return@before
                listField.isAccessible = true
                val list = listField.get(args[0]) as? List<*> ?: return@before
                val lockedChats = lockedChatsFields[1].get(chatCache) as HashSet<*>?
                val lockedNumbers = lockedChats!!.stream()
                    .map<String?> { userjid: Any? -> UserJid(userjid).phoneNumber }.collect(
                        Collectors.toList()
                    )
                val filteredlist = list.filter { item: Any? ->
                    if (!WaContactWpp.TYPE.isInstance(item)) return@filter false
                    val waContact = WaContactWpp(item)
                    val phoneNumber = waContact.userJid.phoneNumber
                    lockedNumbers.contains(phoneNumber)
                }
                listField.set(args[0], filteredlist)
            }
        }
    }

    public override fun getPluginName(): String {
        return "Locked Chats Enhancer"
    }
}
