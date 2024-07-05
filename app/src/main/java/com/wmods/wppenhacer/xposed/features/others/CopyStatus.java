package com.wmods.wppenhacer.xposed.features.others;

import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class CopyStatus extends Feature {
    public CopyStatus(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("copystatus", false)) return;

        var viewButtonMethod = Unobfuscator.loadBlueOnReplayViewButtonMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(viewButtonMethod));

        XposedBridge.hookMethod(viewButtonMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var view = (View) param.getResult();
                var caption = (TextView) view.findViewById(Utils.getID("caption", "id"));
                if (caption != null) {
                    caption.setOnLongClickListener((view1 -> {
                        Utils.setToClipboard(caption.getText().toString());
                        Utils.showToast(Utils.getApplication().getString(ResId.string.copied_to_clipboard), Toast.LENGTH_LONG);
                        return true;
                    }));
                }

            }
        });

        var viewStatusMethod = Unobfuscator.loadBlueOnReplayStatusViewMethod(classLoader);
        XposedBridge.hookMethod(viewStatusMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var view = (View) param.args[0];
                var text = (TextView) view.findViewById(Utils.getID("message_text", "id"));
                if (text != null) {
                    text.setOnLongClickListener((view1 -> {
                        Utils.setToClipboard(text.getText().toString());
                        Utils.showToast(Utils.getApplication().getString(ResId.string.copied_to_clipboard), Toast.LENGTH_LONG);
                        return true;
                    }));
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
