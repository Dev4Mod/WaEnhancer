package com.wmods.wppenhacer.xposed.core.components

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadSharedPreferencesClasses
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

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
        private val prefHook = HashSet<SPrefHook>()

        @Throws(Exception::class)
        fun hookInit(classLoader: ClassLoader) {
            XposedHelpers.findAndHookMethod(
                "android.app.ContextImpl",
                classLoader,
                "getSharedPreferences",
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val pref = param.result as SharedPreferences?
                        if (pref == null || pref is SharedPreferencesWrapper) return
                        param.setResult(SharedPreferencesWrapper(pref))
                    }
                })
            val sharedPreferencesClasses =
                loadSharedPreferencesClasses(classLoader)
            if (sharedPreferencesClasses.isNullOrEmpty()) return

            val getStringHook: XC_MethodHook = object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as String?
                    val value = param.result
                    param.setResult(applyHook(key, value))
                }
            }

            val getBooleanHook: XC_MethodHook = object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as String?
                    val value = param.result
                    param.setResult(applyHook(key, value))
                }
            }

            val getIntHook: XC_MethodHook = object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as String?
                    val value = param.result
                    param.setResult(applyHook(key, value))
                }
            }

            val getLongHook: XC_MethodHook = object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as String?
                    val value = param.result
                    param.setResult(applyHook(key, value))
                }
            }

            val getFloatHook: XC_MethodHook = object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as String?
                    val value = param.result
                    param.setResult(applyHook(key, value))
                }
            }

            val containsHook: XC_MethodHook = object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as String?
                    val value = param.result
                    param.setResult(applyHook(key, value))
                }
            }

            val getAllHook: XC_MethodHook = object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    @Suppress("UNCHECKED_CAST")
                    val result = param.result as MutableMap<String?, Any?>?
                    if (result.isNullOrEmpty()) return
                    val updated = HashMap<String?, Any?>(result.size)
                    for (entry in result.entries) {
                        updated[entry.key] = applyHook(entry.key, entry.value)
                    }
                    param.setResult(updated)
                }
            }

            for (sharedPreferencesClass in sharedPreferencesClasses) {
                if (SharedPreferencesWrapper::class.java.name == sharedPreferencesClass.name) continue
                XposedBridge.hookAllMethods(sharedPreferencesClass, "getString", getStringHook)
                XposedBridge.hookAllMethods(sharedPreferencesClass, "getStringSet", getStringHook)
                XposedBridge.hookAllMethods(sharedPreferencesClass, "getInt", getIntHook)
                XposedBridge.hookAllMethods(sharedPreferencesClass, "getLong", getLongHook)
                XposedBridge.hookAllMethods(sharedPreferencesClass, "getFloat", getFloatHook)
                XposedBridge.hookAllMethods(sharedPreferencesClass, "getBoolean", getBooleanHook)
                XposedBridge.hookAllMethods(sharedPreferencesClass, "contains", containsHook)
                XposedBridge.hookAllMethods(sharedPreferencesClass, "getAll", getAllHook)
            }
        }

        fun addHook(hook: SPrefHook?) {
            prefHook.add(hook!!)
        }

        private fun applyHook(key: String?, value: Any?): Any? {
            var value = value
            for (hook in prefHook) {
                value = hook.hookValue(key, value)
            }
            return value
        }
    }
}
