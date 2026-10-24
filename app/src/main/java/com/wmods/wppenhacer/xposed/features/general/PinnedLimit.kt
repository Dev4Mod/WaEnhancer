@file:Suppress("UNCHECKED_CAST")

package com.wmods.wppenhacer.xposed.features.general

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils

class PinnedLimit(
    loader: ClassLoader,
    preferences: SharedPreferences
) : Feature(loader, preferences) {

    @SuppressLint("DiscouragedApi")
    override fun doHook() {
        if (!xprefs.getBoolean(PINNED_LIMIT_PREF_KEY, false)) return

        Unobfuscator.loadPinnedInChatMethod(classLoader).hook {
            this.intercept { PINNED_LIMIT_ENABLED }
        }

        Unobfuscator.loadSetPinnedLimitMethod(classLoader).hook {
            before {
                if (ReflectionUtils.isCalledFromStrings(SYNC_RESPONSE_HANDLER_CLASS_NAME)) {
                    result = null
                }
            }
        }

        hookPinnedChatsScope()
    }

    private fun hookPinnedChatsScope() {
        val pinnedHashSetMethod = Unobfuscator.loadPinnedHashSetMethod(classLoader)
        val pinSelectedMethod = Unobfuscator.loadPinSelectedChatsMethod(classLoader)
        val trimExcessMethod = Unobfuscator.loadTrimExcessPinnedChatsMethod(classLoader)
        val pinSelection = ThreadLocal<Set<Any?>>()
        val trimming = ThreadLocal<Boolean>()

        runCatching {
            Unobfuscator.loadPinnedChatsPremiumUpsellMethod(classLoader).hook {
                this.intercept { false }
            }
        }.onFailure { logDebug(it) }

        pinnedHashSetMethod.hook {
            after {
                val map = result as? Map<Any?, Any?> ?: return@after
                if (trimming.get() == true) {
                    result = LinkedHashMap<Any?, Any?>()
                    return@after
                }
                val selection = pinSelection.get() ?: return@after
                result = LinkedHashMap(map.filterKeys { it in selection })
            }
        }

        pinSelectedMethod.hook {
            before {
                val selectionField = instance.javaClass.declaredFields.firstOrNull { field ->
                    field.isAccessible = true
                    val value = (field.get(instance) as? Set<*>).orEmpty()
                    value.isNotEmpty() && value.all { FMessageWpp.UserJid.TYPE_JID.isInstance(it) }
                } ?: return@before
                val selection = selectionField.get(instance) as Set<Any?>
                if (selection.size <= PIN_BATCH_SIZE) {
                    pinSelection.set(HashSet(selection))
                    return@before
                }
                try {
                    for (batch in selection.chunked(PIN_BATCH_SIZE)) {
                        val batchSet = LinkedHashSet(batch)
                        selectionField.set(instance, batchSet)
                        pinSelection.set(batchSet)
                        invokeOriginal()
                    }
                } finally {
                    selectionField.set(instance, selection)
                    pinSelection.remove()
                }
                result = null
            }
            after { pinSelection.remove() }
        }

        trimExcessMethod.hook {
            before { trimming.set(true) }
            after { trimming.remove() }
        }

        runCatching {
            val pinChatHandlerMethod = Unobfuscator.loadPinChatHandlerMethod(classLoader)
            val applyingSync = ThreadLocal<Boolean>()
            pinChatHandlerMethod.hook {
                before { applyingSync.set(true) }
                after { applyingSync.remove() }
            }
            Unobfuscator.loadSetPinnedLimitMethod(classLoader).hook {
                before {
                    if (applyingSync.get() == true && args[2] == false) result = null
                }
            }
        }.onFailure { logDebug(it) }
    }

    override fun getPluginName(): String = "Pinned Limit"

    private companion object {
        private const val PINNED_LIMIT_PREF_KEY = "pinnedlimit"
        private const val PINNED_LIMIT_ENABLED = 60
        private const val PIN_BATCH_SIZE = 3
        private const val SYNC_RESPONSE_HANDLER_CLASS_NAME = "SyncResponseHandler"
    }
}
