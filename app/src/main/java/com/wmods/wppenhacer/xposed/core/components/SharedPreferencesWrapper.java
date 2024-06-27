package com.wmods.wppenhacer.xposed.core.components;

import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class SharedPreferencesWrapper implements SharedPreferences {

    private final static HashSet<SPrefHook> prefHook = new HashSet<>();
    private final SharedPreferences mPreferences;

    public SharedPreferencesWrapper(SharedPreferences sharedPreferences) {
        mPreferences = sharedPreferences;
    }

    @Override
    public Map<String, ?> getAll() {
        return mPreferences.getAll();
    }

    @Nullable
    @Override
    public String getString(String s, @Nullable String s1) {
        var value = mPreferences.getString(s, s1);
        return (String) hookValue(s, value);
    }

    /**
     * @noinspection unchecked
     */
    @Nullable
    @Override
    public Set<String> getStringSet(String s, @Nullable Set<String> set) {
        var value = mPreferences.getStringSet(s, set);
        return (Set<String>) hookValue(s, value);
    }

    @Override
    public int getInt(String s, int i) {
        var value = mPreferences.getInt(s, i);
        return (int) hookValue(s, value);
    }

    @Override
    public long getLong(String s, long l) {
        var value = mPreferences.getLong(s, l);
        return (long) hookValue(s, value);
    }

    @Override
    public float getFloat(String s, float v) {
        var value = mPreferences.getFloat(s, v);
        return (float) hookValue(s, value);
    }

    @Override
    public boolean getBoolean(String s, boolean b) {
        var value = mPreferences.getBoolean(s, b);
        return (boolean) hookValue(s, value);
    }

    @Override
    public boolean contains(String s) {
        return mPreferences.contains(s);
    }

    @Override
    public Editor edit() {
        return mPreferences.edit();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
        mPreferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
        mPreferences.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    public static void hookInit(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(ContextWrapper.class.getName(), classLoader, "getSharedPreferences", String.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var pref = (SharedPreferences) param.getResult();
                if (pref instanceof SharedPreferencesWrapper) return;
                param.setResult(new SharedPreferencesWrapper(pref));
            }
        });
    }

    public static void addHook(SPrefHook hook) {
        prefHook.add(hook);
    }

    private Object hookValue(String key, Object value) {
        for (SPrefHook hook : prefHook) {
            value = hook.hookValue(key, value);
        }
        return value;
    }

    public interface SPrefHook {
        @Nullable
        Object hookValue(String key, Object value);
    }
}
