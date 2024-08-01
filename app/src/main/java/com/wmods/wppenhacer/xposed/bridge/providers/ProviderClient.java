package com.wmods.wppenhacer.xposed.bridge.providers;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.activities.ForceStartActivity;
import com.wmods.wppenhacer.xposed.bridge.WaeIIFace;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XposedBridge;

public class ProviderClient {

    public static WaeIIFace mInstance;

    public static WaeIIFace getInstance() {

        if (mInstance == null || !mInstance.asBinder().isBinderAlive()) {

            try {
                var intent = new Intent();
                intent.setComponent(new ComponentName(BuildConfig.APPLICATION_ID, ForceStartActivity.class.getName()));
                intent.putExtra("pkg", WppCore.getCurrentActivity().getPackageName());
                WppCore.getCurrentActivity().startActivity(intent);
            } catch (Exception ignored) {

            }
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