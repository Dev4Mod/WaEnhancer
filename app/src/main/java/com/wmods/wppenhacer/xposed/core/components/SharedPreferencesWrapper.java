package com.wmods.wppenhacer.xposed.core.components;

import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

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
        return (String) applyHook(s, value);
    }

    /**
     * @noinspection unchecked
     */
    @Nullable
    @Override
    public Set<String> getStringSet(String s, @Nullable Set<String> set) {
        var value = mPreferences.getStringSet(s, set);
        return (Set<String>) applyHook(s, value);
    }

    @Override
    public int getInt(String s, int i) {
        var value = mPreferences.getInt(s, i);
        return (int) applyHook(s, value);
    }

    @Override
    public long getLong(String s, long l) {
        var value = mPreferences.getLong(s, l);
        return (long) applyHook(s, value);
    }

    @Override
    public float getFloat(String s, float v) {
        var value = mPreferences.getFloat(s, v);
        return (float) applyHook(s, value);
    }

    @Override
    public boolean getBoolean(String s, boolean b) {
        var value = mPreferences.getBoolean(s, b);
        return (boolean) applyHook(s, value);
    }

    @Override
    public boolean contains(String s) {
        var value = mPreferences.contains(s);
        return (boolean) applyHook(s, value);
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

    public static void hookInit(ClassLoader classLoader) throws Exception {
        XposedHelpers.findAndHookMethod("android.app.ContextImpl", classLoader, "getSharedPreferences", String.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var pref = (SharedPreferences) param.getResult();
                if (pref == null || pref instanceof SharedPreferencesWrapper) return;
                param.setResult(new SharedPreferencesWrapper(pref));
            }
        });
        var sharedPreferencesClasses = Unobfuscator.loadSharedPreferencesClasses(classLoader);
        if (sharedPreferencesClasses == null || sharedPreferencesClasses.length == 0) return;

        XC_MethodHook getStringHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var key = (String) param.args[0];
                var value = param.getResult();
                param.setResult(applyHook(key, value));
            }
        };

        XC_MethodHook getBooleanHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var key = (String) param.args[0];
                var value = param.getResult();
                param.setResult((boolean) applyHook(key, value));
            }
        };

        XC_MethodHook getIntHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var key = (String) param.args[0];
                var value = param.getResult();
                param.setResult((int) applyHook(key, value));
            }
        };

        XC_MethodHook getLongHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var key = (String) param.args[0];
                var value = param.getResult();
                param.setResult((long) applyHook(key, value));
            }
        };

        XC_MethodHook getFloatHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var key = (String) param.args[0];
                var value = param.getResult();
                param.setResult((float) applyHook(key, value));
            }
        };

        XC_MethodHook containsHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var key = (String) param.args[0];
                var value = param.getResult();
                param.setResult((boolean) applyHook(key, value));
            }
        };

        XC_MethodHook getAllHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) param.getResult();
                if (result == null || result.isEmpty()) return;
                var updated = new java.util.HashMap<String, Object>(result.size());
                for (var entry : result.entrySet()) {
                    updated.put(entry.getKey(), applyHook(entry.getKey(), entry.getValue()));
                }
                param.setResult(updated);
            }
        };

        for (var sharedPreferencesClass : sharedPreferencesClasses) {
            if (sharedPreferencesClass == null) continue;
            if (SharedPreferencesWrapper.class.getName().equals(sharedPreferencesClass.getName())) continue;
            XposedHelpers.findAndHookMethod(sharedPreferencesClass, "getString", String.class, String.class, getStringHook);
            XposedHelpers.findAndHookMethod(sharedPreferencesClass, "getStringSet", String.class, Set.class, getStringHook);
            XposedHelpers.findAndHookMethod(sharedPreferencesClass, "getInt", String.class, int.class, getIntHook);
            XposedHelpers.findAndHookMethod(sharedPreferencesClass, "getLong", String.class, long.class, getLongHook);
            XposedHelpers.findAndHookMethod(sharedPreferencesClass, "getFloat", String.class, float.class, getFloatHook);
            XposedHelpers.findAndHookMethod(sharedPreferencesClass, "getBoolean", String.class, boolean.class, getBooleanHook);
            XposedHelpers.findAndHookMethod(sharedPreferencesClass, "contains", String.class, containsHook);
            XposedHelpers.findAndHookMethod(sharedPreferencesClass, "getAll", getAllHook);
        }
    }

    public static void addHook(SPrefHook hook) {
        prefHook.add(hook);
    }

    private static Object applyHook(String key, Object value) {
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
