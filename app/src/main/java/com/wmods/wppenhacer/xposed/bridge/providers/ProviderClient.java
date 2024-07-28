package com.wmods.wppenhacer.xposed.bridge.providers;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import com.wmods.wppenhacer.xposed.bridge.WaeIIFace;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XposedBridge;

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
                XposedBridge.log("Error in Bridge: " + e.getMessage());
            }
        }
        return mInstance;
    }

}