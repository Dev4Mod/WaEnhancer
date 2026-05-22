package com.wmods.wppenhacer.xposed.features.others;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.model.ContactPickerResult;
import com.wmods.wppenhacer.preference.ContactPickerPreference;
import com.wmods.wppenhacer.utils.WhatsAppContactPickerLauncher;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ActivityController extends Feature {

    private static String Key;
    private final AtomicBoolean disableAuth = new AtomicBoolean(false);

    public ActivityController(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var clazz = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, ".SettingsNotifications");

        var authCheckMethod = Unobfuscator.loadLockedAuthCheckMethod(classLoader);

        XposedBridge.hookMethod(authCheckMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (disableAuth.get())
                    param.setResult(false);
            }
        });

        WppCore.addListenerActivity((activity, type) -> {
            if (clazz.isAssignableFrom(activity.getClass()) && type == WppCore.ActivityChangeState.ChangeType.ENDED) {
                disableAuth.set(false);
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (clazz != param.thisObject.getClass()) return;
                var activity = (Activity) param.thisObject;
                var intent = activity.getIntent();
                if (intent.getBooleanExtra("contact_mode", false)) {
                    disableAuth.set(true);
                    contactController(intent, activity);
                }
            }
        });


        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult", int.class, int.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                disableAuth.set(false);
                if (clazz != param.thisObject.getClass()) return;
                var activity = (Activity) param.thisObject;
                var id = (int) param.args[0];
                Intent intent = (Intent) param.args[2];
                if (id == ContactPickerPreference.REQUEST_CONTACT_PICKER && intent != null) {
                    processResultContact(intent, activity);
                }
                activity.finish();
            }
        });

    }

    private static void processResultContact(Intent intent, Activity activity) {
        if (!intent.hasExtra("key") && Key != null) {
            intent.putExtra("key", Key);
        }
        if (!intent.hasExtra("contacts")) {
            intent.putStringArrayListExtra("contacts", new ArrayList<>());
        }
        if (!intent.hasExtra("picker_contacts")) {
            intent.putExtra("picker_contacts", new ArrayList<ContactPickerResult>());
        }
        activity.setResult(Activity.RESULT_OK, intent);
    }


    private static void contactController(Intent intent, Activity activity) throws Exception {
        Key = intent.getStringExtra("key");
        var contacts = intent.getStringArrayListExtra("contacts");
        var pickerIntent = WhatsAppContactPickerLauncher.createAboutPickerIntent(activity, activity.getPackageName(), Key == null ? "" : Key, contacts);
        activity.startActivityForResult(pickerIntent, ContactPickerPreference.REQUEST_CONTACT_PICKER);
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Activity Controller";
    }

}