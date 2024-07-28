package com.wmods.wppenhacer.xposed.bridge.providers;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import com.wmods.wppenhacer.xposed.bridge.WaeIIFace;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.Utils;

public class ProviderClient {

    public static WaeIIFace mInstance;

    public static WaeIIFace getInstance() {

        if (mInstance == null || !mInstance.asBinder().isBinderAlive()) {
            try {
                ContentResolver resolver = Utils.getApplication().getContentResolver();
                Uri parse = Uri.parse("content://com.wmods.waenhancer.hookprovider");
                Bundle bundle = resolver.call(parse, "getHookBinder", null, null);
                IBinder binder = bundle.getBinder("binder");
                mInstance = WaeIIFace.Stub.asInterface(binder);
            } catch (Exception e) {
                var dialog = new AlertDialogWpp(Utils.getApplication()).setTitle("Error");
                dialog.setPositiveButton("OK", null);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    dialog.setMessage("For Android 11+ it is necessary to activate the System Framework in LSPosed scopes and restart");
                } else {
                    dialog.setMessage("Error in Bridge: " + e.getMessage());
                }
            }
        }
        return mInstance;
    }

}