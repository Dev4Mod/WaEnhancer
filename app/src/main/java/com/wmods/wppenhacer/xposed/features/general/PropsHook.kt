package com.wmods.wppenhacer.xposed.features.general

import android.content.SharedPreferences
import com.highcapable.yukihookapi.hook.core.api.priority.YukiHookPriority
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class PropsHook(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        val resolver = runCatching { Unobfuscator.loadPropsResolveMethod(classLoader) }
            .onFailure {
                logDebug("Props resolver not found, using fallback getters")
                logDebug(it)
            }
            .getOrNull()

        if (resolver != null) {
            hookProps(resolver)
            return
        }

        hookProps(Unobfuscator.loadPropsBooleanMethod(classLoader))
        hookProps(Unobfuscator.loadPropsIntegerMethod(classLoader))
    }

    private fun hookProps(method: Method) {
        logDebug(Unobfuscator.getMethodDescriptor(method))
        val idIndex = method.parameterTypes.indexOfLast { it == Int::class.javaPrimitiveType }
        if (idIndex < 0) throw IllegalStateException("No prop id parameter in $method")

        method.hook(priority = YukiHookPriority.HIGHEST).after {
            val id = args[idIndex] as? Int ?: return@after
            val override = resolve(id, result) ?: return@after
            result = override
        }
    }

    private fun resolve(id: Int, original: Any?): Any? {
        var debug: Any? = null
        for (listener in listeners) {
            val value = listener.onResolve(id, original)
            if (debug == null) debug = value
        }
        if (debug != null) return debug

        val feature = features.find(id, original) ?: return null
        // Fix Bug in Settings Data Usage
        if (id == ME_TAB_ENABLED_CODE
            && ReflectionUtils.isCalledFromClass(WppCore.dataUsageActivityClass)
        ) return null
        return feature
    }

    override fun getPluginName(): String = "Props Hook"

    /** Values for one layer. A prop id has a single type in WhatsApp, so the id picks the map. */
    class PropLayer {
        val booleans = ConcurrentHashMap<Int, Boolean>()
        val integers = ConcurrentHashMap<Int, Int>()
        val floats = ConcurrentHashMap<Int, Float>()
        val strings = ConcurrentHashMap<Int, String>()

        /**
         * Only returns a value whose type matches what WhatsApp would have returned
         * ([original]), so a wrong-typed override can never reach a cast in WhatsApp's code.
         * When the original read failed ([original] is null) any stored type is accepted.
         */
        fun find(id: Int, original: Any?): Any? = when (original) {
            is Boolean -> booleans[id]
            is Int -> integers[id]
            is Float -> floats[id]
            is String -> strings[id]
            null -> booleans[id] ?: integers[id] ?: floats[id] ?: strings[id]
            else -> null
        }
    }

    /** Sees the untouched value of every prop read; a non-null return overrides it. */
    fun interface PropListener {
        fun onResolve(id: Int, original: Any?): Any?
    }

    companion object {
        private const val ME_TAB_ENABLED_CODE = 4023

        /** Defaults set by the features of this module. */
        @JvmField
        val features = PropLayer()

        private val listeners = CopyOnWriteArrayList<PropListener>()

        val booleans: ConcurrentHashMap<Int, Boolean> get() = features.booleans
        val integers: ConcurrentHashMap<Int, Int> get() = features.integers
        val floats: ConcurrentHashMap<Int, Float> get() = features.floats
        val strings: ConcurrentHashMap<Int, String> get() = features.strings

        @JvmStatic
        fun addListener(listener: PropListener) {
            listeners.add(listener)
        }
    }
}
