package com.wmods.wppenhacer.xposed.features.others;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ChatFilters extends Feature {
    public ChatFilters(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("separategroups", false)) return;
        var filterAdaperClass = Unobfuscator.loadFilterAdaperClass(classLoader);
        XposedBridge.hookAllConstructors(filterAdaperClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var argResult = IntStream.range(0, param.args.length).mapToObj(i -> new Pair<>(i, param.args[i])).filter(p -> p.second instanceof List).findFirst().orElse(null);
                if (argResult != null) {
                    var newList = new ArrayList<Object>((List) argResult.second);
                    newList.removeIf(item -> {
                        var name = XposedHelpers.getObjectField(item, "A01");
                        return name == null || name == "CONTACTS_FILTER" || name == "GROUP_FILTER";
                    });
                    param.args[argResult.first] = newList;
                }
            }
        });
        var methodSetFilter = Arrays.stream(filterAdaperClass.getDeclaredMethods()).filter(m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(int.class)).findFirst().orElse(null);

        XposedBridge.hookMethod(methodSetFilter, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var index = (int) param.args[0];
                var field = Unobfuscator.getFieldByType(methodSetFilter.getDeclaringClass(), List.class);
                var list = (List) field.get(param.thisObject);
                if (list == null || index >= list.size()) {
                    param.setResult(null);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "";
    }
}
