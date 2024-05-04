package com.wmods.wppenhacer.xposed.features.general;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.BaseBundle;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.ResId;
import com.wmods.wppenhacer.xposed.core.UnobfuscatorCache;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CallType extends Feature {
    private XC_MethodHook.Unhook hookBundleBoolean;

    public CallType(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("calltype", false)) return;

        var callConfirmationFragment = XposedHelpers.findClass("com.whatsapp.calling.fragment.CallConfirmationFragment", loader);
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
                hookBundleBoolean = XposedHelpers.findAndHookMethod(BaseBundle.class, "getBoolean", String.class,  new XC_MethodHook() {
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
                            intent.setData(Uri.parse("tel:+" + WppCore.stripJID(jid)));
                            context.startActivity(intent);
                            break;
                        case 1:
                            origDialog.show();
                            break;
                    }
                });
                newDialog = (Dialog) mAlertDialog.create();
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
