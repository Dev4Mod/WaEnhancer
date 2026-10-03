package com.wmods.wppenhacer.xposed.core.components

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadSharedPreferencesClasses
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import java.util.concurrent.CopyOnWriteArraySet

class SharedPreferencesWrapper(private val mPreferences: SharedPreferences) : SharedPreferences {
    override fun getAll(): MutableMap<String?, *>? {
        return mPreferences.all
    }

    override fun getString(s: String?, s1: String?): String? {
        val value = mPreferences.getString(s, s1)
        return applyHook(s, value) as String?
    }

    /**
     * @noinspection unchecked
     */
    override fun getStringSet(s: String?, set: MutableSet<String?>?): MutableSet<String?>? {
        val value = mPreferences.getStringSet(s, set)
        @Suppress("UNCHECKED_CAST")
        return applyHook(s, value) as MutableSet<String?>?
    }

    override fun getInt(s: String?, i: Int): Int {
        val value = mPreferences.getInt(s, i)
        return applyHook(s, value) as Int
    }

    override fun getLong(s: String?, l: Long): Long {
        val value = mPreferences.getLong(s, l)
        return applyHook(s, value) as Long
    }

    override fun getFloat(s: String?, v: Float): Float {
        val value = mPreferences.getFloat(s, v)
        return applyHook(s, value) as Float
    }

    override fun getBoolean(s: String?, b: Boolean): Boolean {
        val value = mPreferences.getBoolean(s, b)
        return applyHook(s, value) as Boolean
    }

    override fun contains(s: String?): Boolean {
        val value = mPreferences.contains(s)
        return applyHook(s, value) as Boolean
    }

    override fun edit(): SharedPreferences.Editor? {
        return mPreferences.edit()
    }

    override fun registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener?) {
        mPreferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener?) {
        mPreferences.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    fun interface SPrefHook {
        fun hookValue(key: String?, value: Any?): Any?
    }

    companion object {
        private val prefHook = CopyOnWriteArraySet<SPrefHook>()

        @Throws(Exception::class)
        fun hookInit(packageParam: PackageParam, classLoader: ClassLoader) {
            packageParam.apply {
                ReflectionUtils.findClass("android.app.ContextImpl", classLoader).resolve()
                    .firstMethod {
                        name = "getSharedPreferences"
                        superclass()
                        parameters(String::class.java, Int::class)
                    }.hook {
                    after {
                        val pref = result as SharedPreferences?
                        if (pref == null || pref is SharedPreferencesWrapper) return@after
                        result = (SharedPreferencesWrapper(pref))
                    }
                }
                val sharedPreferencesClasses =
                    loadSharedPreferencesClasses(classLoader)
                if (sharedPreferencesClasses.isNullOrEmpty()) return

                val applyKeyHook: HookParam.() -> Unit = {
                    val key = args[0] as String?
                    val value = result
                    result = applyHook(key, value)
                }

                val getAllHook: HookParam.() -> Unit = {
                    @Suppress("UNCHECKED_CAST")
                    val result = result as MutableMap<String?, Any?>?
                    if (!result.isNullOrEmpty()) {
                        val updated = HashMap<String?, Any?>(result.size)
                        for (entry in result.entries) {
                            updated[entry.key] = applyHook(entry.key, entry.value)
                        }
                        this.result = updated
                    }
                }

                for (sharedPreferencesClass in sharedPreferencesClasses) {
                    if (SharedPreferencesWrapper::class.java.name == sharedPreferencesClass.name) continue
                    for (methodName in listOf(
                        "getString",
                        "getStringSet",
                        "getInt",
                        "getLong",
                        "getFloat",
                        "getBoolean",
                        "contains"
                    )) {
                        sharedPreferencesClass.resolve().method {
                            name = methodName
                        }.hookAll { after(applyKeyHook) }
                    }
                    sharedPreferencesClass.resolve().method {
                        name = "getAll"
                    }.hookAll { after(getAllHook) }
                }
            }
        }

        fun addHook(hook: SPrefHook?) {
            prefHook.add(hook!!)
        }

        private fun applyHook(key: String?, value: Any?): Any? {
            if (prefHook.isEmpty()) return value
            var value = value
            for (hook in prefHook) {
                value = hook.hookValue(key, value)
            }
            return value
        }
    }
}
