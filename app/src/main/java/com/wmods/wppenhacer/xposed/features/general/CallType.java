package com.wmods.wppenhacer.xposed.features.general;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.BaseBundle;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CallType extends Feature {
    private XC_MethodHook.Unhook hookBundleBoolean;

    public CallType(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("calltype", false)) return;

        var intPreferences = Unobfuscator.loadGetIntPreferences(classLoader);
        XposedBridge.hookMethod(intPreferences, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[1] == "call_confirmation_dialog_count") {
                    param.setResult(1);
                }
            }
        });


        var callConfirmationFragment = XposedHelpers.findClass("com.whatsapp.calling.fragment.CallConfirmationFragment", classLoader);
        var method = ReflectionUtils.findMethodUsingFilter(callConfirmationFragment, m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(android.os.Bundle.class));
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            private boolean isVideoCall;
            private String jid;
            private Dialog newDialog;
            private Unhook hookBundleString;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                hookBundleString = XposedHelpers.findAndHookMethod(BaseBundle.class, "getString", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args[0] == "jid") {
                            jid = (String) param.getResult();
                        }
                    }
                });
                hookBundleBoolean = XposedHelpers.findAndHookMethod(BaseBundle.class, "getBoolean", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args[0] == "is_video_call") {
                            isVideoCall = (boolean) param.getResult();
                        }
                    }
                });
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                hookBundleString.unhook();
                hookBundleBoolean.unhook();
                if (jid == null || isVideoCall) return;
                var origDialog = (Dialog) param.getResult();
                var context = origDialog.getContext();
                var mAlertDialog = new AlertDialogWpp(origDialog.getContext());
                mAlertDialog.setTitle(UnobfuscatorCache.getInstance().getString("selectcalltype"));
                mAlertDialog.setItems(new String[]{context.getString(ResId.string.phone_call), context.getString(ResId.string.whatsapp_call)}, (dialog, which) -> {
                    newDialog.dismiss();
                    switch (which) {
                        case 0:
                            var intent = new Intent();
                            intent.setAction(Intent.ACTION_DIAL);
                            var userJid = new FMessageWpp.UserJid(jid);
                            intent.setData(Uri.parse("tel:+" + userJid.getStripJID()));
                            context.startActivity(intent);
                            break;
                        case 1:
                            origDialog.show();
                            break;
                    }
                });
                newDialog = mAlertDialog.create();
                param.setResult(newDialog);
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Call Type";
    }
}
