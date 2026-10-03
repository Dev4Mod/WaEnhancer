package com.wmods.wppenhacer.xposed.utils

import android.content.SharedPreferences
import com.highcapable.yukihookapi.YukiHookAPI.Status.isXposedEnvironment
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge
import java.util.concurrent.CopyOnWriteArraySet

class YukiSharedPreference(private val pref: YukiHookPrefsBridge) : SharedPreferences {
    private val listeners =
        CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun contains(key: String?): Boolean {
        return key?.let { pref.contains(it) } ?: false
    }

    override fun edit(): SharedPreferences.Editor {
        if (isXposedEnvironment.not()) {
            throw UnsupportedOperationException("YukiHookPrefsBridge.Editor not allowed in Xposed Environment")
        }
        return Editor(this, pref.edit())
    }

    override fun getAll(): Map<String, *> {
        return pref.all()
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return key?.let { pref.getBoolean(it, defValue) } ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return key?.let { pref.getFloat(it, defValue) } ?: defValue
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return key?.let { pref.getInt(it, defValue) } ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return key?.let { pref.getLong(it, defValue) } ?: defValue
    }

    override fun getString(key: String?, defValue: String?): String? {
        if (key == null || pref.contains(key).not()) return defValue
        return pref.getString(key, defValue.orEmpty())
    }

    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? {
        if (key == null || pref.contains(key).not()) return defValues
        return pref.getStringSet(key, defValues ?: emptySet())
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        listener?.let { listeners += it }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        listener?.let { listeners -= it }
    }

    private fun notifyChanged(keys: Set<String>) {
        if (keys.isEmpty()) return
        keys.forEach { key ->
            listeners.forEach { listener -> listener.onSharedPreferenceChanged(this, key) }
        }
    }

    class Editor(
        private val owner: YukiSharedPreference,
        private val editor: YukiHookPrefsBridge.Editor
    ) : SharedPreferences.Editor {
        private val changedKeys = linkedSetOf<String>()

        override fun apply() {
            editor.apply()
            owner.notifyChanged(changedKeys)
        }

        override fun clear(): SharedPreferences.Editor {
            changedKeys += owner.all.keys
            editor.clear()
            return this
        }

        override fun commit(): Boolean {
            val committed = editor.commit()
            if (committed) owner.notifyChanged(changedKeys)
            return committed
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) {
                changedKeys += key
                editor.putBoolean(key, value)
            }
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) {
                changedKeys += key
                editor.putFloat(key, value)
            }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) {
                changedKeys += key
                editor.putInt(key, value)
            }
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) {
                changedKeys += key
                editor.putLong(key, value)
            }
            return this
        }

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) {
                changedKeys += key
                if (value == null) editor.remove(key) else editor.putString(key, value)
            }
            return this
        }

        override fun putStringSet(
            key: String?,
            values: Set<String?>?
        ): SharedPreferences.Editor {
            if (key != null) {
                changedKeys += key
                if (values == null) {
                    editor.remove(key)
                } else {
                    editor.putStringSet(key, values.filterNotNull().toSet())
                }
            }
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) {
                changedKeys += key
                editor.remove(key)
            }
            return this
        }
    }
}
