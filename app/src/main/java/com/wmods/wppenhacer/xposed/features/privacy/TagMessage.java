package com.wmods.wppenhacer.xposed.features.privacy;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class TagMessage extends Feature {
    public TagMessage(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {

        Method method = Unobfuscator.loadForwardTagMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(method));
        Class<?> forwardClass = Unobfuscator.loadForwardClassMethod(classLoader);
        logDebug("ForwardClass: " + forwardClass.getName());

        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("hidetag", false)) return;
                var arg = (long) param.args[0];
                if (arg == 1) {
                    if (ReflectionUtils.isCalledFromClass(forwardClass)) {
                        param.args[0] = 0;
                    }
                }
            }
        });

        if (prefs.getBoolean("broadcast_tag", false)) {
            hookBroadcastView();
        }
    }

    private void hookBroadcastView() throws Exception {

        var bubbleMethod = Unobfuscator.loadAntiRevokeBubbleMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(bubbleMethod));

        XposedBridge.hookMethod(bubbleMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                var objMessage = param.args[2];
                var dateTextView = (TextView) param.args[1];
                if (dateTextView == null) return;
                var fmessage = new FMessageWpp(objMessage);
                var key = fmessage.getKey();
                var dateWrapper = (ViewGroup) dateTextView.getParent();
                int id = Utils.getID("broadcast_icon", "id");
                View res = dateWrapper.findViewById(id);
                if (!key.isFromMe)
                    if (fmessage.isBroadcast() && res == null) {
                        var broadcast = new ImageView(dateWrapper.getContext());
                        broadcast.setId(id);
                        broadcast.setImageDrawable(DesignUtils.getDrawableByName("broadcast_status_icon"));
                        dateWrapper.addView(broadcast, 0);
                    } else if (res != null) {
                        dateWrapper.removeView(res);
                    }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Tag Message";
    }
}
