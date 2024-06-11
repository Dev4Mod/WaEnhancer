package com.wmods.wppenhacer.xposed.features.customization;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class CustomTime extends Feature {

    public CustomTime(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        var secondsToTime = prefs.getBoolean("segundos", false);
        var ampm = prefs.getBoolean("ampm", false);
        var secondsToTimeMethod = Unobfuscator.loadTimeToSecondsMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(secondsToTimeMethod));

        XposedBridge.hookMethod(secondsToTimeMethod, new XC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                var timestamp = (long) param.args[1];
                var date = new Date(timestamp);
                var patternDefault = "HH:mm";
                var patternSeconds = "HH:mm:ss";
                if (ampm) {
                    patternDefault = "hh:mm a";
                    patternSeconds = "hh:mm:ss a";
                }
                var pattern = secondsToTime ? patternSeconds : patternDefault;
                var formattedDate = new SimpleDateFormat(pattern, Locale.US).format(date);

                param.setResult(getTextInHour(formattedDate));
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Seconds To Time";
    }

    private String getTextInHour(String date) {
        var summary = prefs.getString("secondstotime", "");
        if (summary == null) return date;
        else return date + " " + summary;
    }
}
