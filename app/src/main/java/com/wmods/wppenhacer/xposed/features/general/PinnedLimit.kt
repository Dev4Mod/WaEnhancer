@file:Suppress("UNCHECKED_CAST")

package com.wmods.wppenhacer.xposed.features.general

import android.annotation.SuppressLint
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.Spliterator
import kotlin.math.abs
import java.lang.reflect.Array as ReflectArray

class PinnedLimit(
    loader: ClassLoader,
    preferences:SharedPreferences
) : Feature(loader, preferences) {

    @SuppressLint("DiscouragedApi")
    override fun doHook() {
        val pinnedHashSetMethod = Unobfuscator.loadPinnedHashSetMethod(classLoader)
        if (prefs.getBoolean(PINNED_LIMIT_PREF_KEY, false)) {
            XposedBridge.hookMethod(Unobfuscator.loadPinnedInChatMethod(classLoader),
                XC_MethodReplacement.returnConstant(PINNED_LIMIT_ENABLED))

            XposedBridge.hookMethod(Unobfuscator.loadSetPinnedLimitMethod(classLoader), object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (ReflectionUtils.isCalledFromStrings(SYNC_RESPONSE_HANDLER_CLASS_NAME)) {
                        param.result = null
                    }
                }
            })
        }

        XposedHelpers.findAndHookConstructor(LinkedHashSet::class.java, Int::class.javaPrimitiveType, object : XC_MethodHook(){
            override fun beforeHookedMethod(param: MethodHookParam) {
                val initialCapacity = param.args[0] as Int
                if (initialCapacity < 0) {
                    param.args[0] = abs(initialCapacity)
                }
            }
        })

        XposedHelpers.findAndHookConstructor(ArrayList::class.java, Int::class.javaPrimitiveType, object : XC_MethodHook(){
            override fun beforeHookedMethod(param: MethodHookParam) {
                val initialCapacity = param.args[0] as Int
                if (initialCapacity < 0) {
                    param.args[0] = abs(initialCapacity)
                }
            }
        })
        XposedBridge.hookMethod(pinnedHashSetMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val map = param.result as Map<Any?, Any?>
                val thisObject = param.thisObject ?: param.args[0]!!
                val pinnedMap = if (map is PinnedLinkedHashMap<*>) {
                    map as PinnedLinkedHashMap<Any?>
                } else {
                    PinnedLinkedHashMap<Any?>().apply {
                        putAll(map)
                        param.result = this
                    }
                }

                pinnedMap.limit = getPinnedLimit()

                val keySet = map.keys
                val setFields = ReflectionUtils.getFieldsByType(thisObject.javaClass, Set::class.java)

                for (setField in setFields) {
                    val set = setField.get(thisObject)

                    if (set == keySet) {
                        val newKeySet = pinnedMap.keys as PinnedLinkedHashMap.PinnedKeySet<Any?>
                        newKeySet.setDisableInterator(false)
                        setField.set(thisObject, newKeySet)
                    }
                }
            }
        })

        XposedBridge.hookMethod( Unobfuscator.loadPinnedFilterMethod(classLoader), object : XC_MethodHook(){
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.args[0] !is PinnedLinkedHashMap.PinnedKeySet<*>) {
                    return
                }

                val set = param.result as Set<*>
                val pinnedMap = PinnedLinkedHashMap<Any?>().apply {
                    limit = getPinnedLimit()

                    for (item in set) {
                        put(item, item)
                    }
                }

                val newKeySet = pinnedMap.keys as PinnedLinkedHashMap.PinnedKeySet<Any?>
                newKeySet.setDisableInterator(false)
                param.result = pinnedMap.keys
            }
        })
    }

    override fun getPluginName(): String = "Pinned Limit"

    private fun getPinnedLimit(): Int {
        return if (prefs.getBoolean(PINNED_LIMIT_PREF_KEY, false)) {
            PINNED_LIMIT_ENABLED
        } else {
            PINNED_LIMIT_DEFAULT
        }
    }

    private class PinnedLinkedHashMap<T> : LinkedHashMap<T, T>() {

        var limit: Int = PINNED_LIMIT_DEFAULT

        override val keys: MutableSet<T>
            get() = PinnedKeySet(this, super.keys)

        override val size: Int
            get() {
                val currentSize = super.size
                return if (currentSize >= limit) currentSize else -limit
            }

        class PinnedKeySet<T>(
            private val pinnedMap: PinnedLinkedHashMap<T>,
            private val set: MutableSet<T>
        ) : AbstractMutableSet<T>() {

            override val size: Int
                get() = pinnedMap.size

            override fun isEmpty(): Boolean = set.isEmpty()

            override fun contains(element: T): Boolean = set.contains(element)

            override fun containsAll(elements: Collection<T>): Boolean {
                return set.containsAll(elements)
            }

            override fun iterator(): MutableIterator<T> {
                if (disableInterator && pinnedMap.size < pinnedMap.limit) {
                    return EmptyMutableIterator()
                }

                return set.iterator()
            }

            override fun add(element: T): Boolean {
                val hadKey = pinnedMap.containsKey(element)
                pinnedMap[element] = element
                return !hadKey
            }

            override fun addAll(elements: Collection<T>): Boolean {
                var changed = false

                for (item in elements) {
                    changed = add(item) || changed
                }

                return changed
            }

            override fun remove(element: T): Boolean {
                val hadKey = pinnedMap.containsKey(element)

                if (hadKey) {
                    pinnedMap.remove(element)
                }

                return hadKey
            }

            override fun removeAll(elements: Collection<T>): Boolean {
                return set.removeAll(elements.toSet())
            }

            override fun retainAll(elements: Collection<T>): Boolean {
                return set.retainAll(elements.toSet())
            }

            override fun clear() {
                set.clear()
            }

            override fun spliterator(): Spliterator<T> {
                return set.spliterator()
            }

            override fun toArray(): Array<Any?> {
                val result = arrayOfNulls<Any?>(set.size)
                var index = 0

                for (item in set) {
                    result[index++] = item
                }

                return result
            }

            override fun <E> toArray(array: Array<E>): Array<E> {
                @Suppress("UNCHECKED_CAST")
                val result = if (array.size >= set.size) {
                    array
                } else {
                    ReflectArray.newInstance(array.javaClass.componentType!!, set.size) as Array<E>
                }

                var index = 0
                for (item in set) {
                    @Suppress("UNCHECKED_CAST")
                    result[index++] = item as E
                }

                if (result.size > set.size) {
                    @Suppress("UNCHECKED_CAST")
                    result[set.size] = null as E
                }

                return result
            }

            fun setDisableInterator(disabled: Boolean) {
                disableInterator = disabled
            }

            private class EmptyMutableIterator<T> : MutableIterator<T> {

                override fun hasNext(): Boolean = false

                override fun next(): T {
                    throw NoSuchElementException()
                }

                override fun remove() = Unit
            }

            companion object {
                private var disableInterator = true
            }
        }
    }

    private companion object {
        private const val PINNED_LIMIT_PREF_KEY = "pinnedlimit"
        private const val PINNED_LIMIT_ENABLED = 60
        private const val PINNED_LIMIT_DEFAULT = 3
        private const val SYNC_RESPONSE_HANDLER_CLASS_NAME = "SyncResponseHandler"
    }
}