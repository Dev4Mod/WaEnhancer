package com.wmods.wppenhacer.xposed.features.privacy;

import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
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
        Method method1 = Unobfuscator.loadBroadcastTagMethod(classLoader);

        XposedBridge.hookMethod(method1, new XC_MethodHook() {
            private FMessageWpp.Key keyObj;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                keyObj = null;
                var fmessage = new FMessageWpp(param.args[0]);
                var key = fmessage.getKey();
                if (!key.isFromMe && fmessage.isBroadcast()) {
                    var view = (ViewGroup) param.thisObject;
                    var res = view.findViewById((int) param.args[1]);
                    if (res == null) {
                        var dateWrapper = (ViewGroup) view.findViewById(Utils.getID("date_wrapper", "id"));
                        var broadcast = new ImageView(view.getContext());
                        broadcast.setId((int) param.args[1]);
                        dateWrapper.addView(broadcast, 0);
                    }
                    key.setIsFromMe(true);
                    keyObj = key;
                }
            }

            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (keyObj != null) {
                    keyObj.setIsFromMe(false);
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
