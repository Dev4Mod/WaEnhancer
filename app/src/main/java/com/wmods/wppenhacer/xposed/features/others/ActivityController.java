package com.wmods.wppenhacer.xposed.features.others;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.preference.ContactPickerPreference;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class ActivityController extends Feature {

    public static final String EXPORTED_ACTIVITY = "com.whatsapp.settings.SettingsNotifications";
    private static String Key;

    public ActivityController(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var clazz = XposedHelpers.findClass(EXPORTED_ACTIVITY, classLoader);
        Class<?> statusDistribution = Unobfuscator.loadStatusDistributionClass(classLoader);

        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (clazz != param.thisObject.getClass()) return;
                var activity = (Activity) param.thisObject;
                var intent = activity.getIntent();
                if (intent.getBooleanExtra("contact_mode", false)) {
                    Key = intent.getStringExtra("key");
                    var contacts = intent.getStringArrayListExtra("contacts");
                    var intent2 = new Intent();
                    intent2.setClassName(activity.getPackageName(), "com.whatsapp.status.audienceselector.StatusTemporalRecipientsActivity");
                    intent2.putExtra("contact_mode", true);
                    intent2.putExtra("is_black_list", false);
                    List<Object> listContacts = new ArrayList<>();
                    if (contacts != null) {
                        for (String contact : contacts) {
                            try {
                                Object jid = WppCore.createUserJid(contact);
                                listContacts.add(jid);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    Constructor constructor = ReflectionUtils.findConstructorUsingFilter(statusDistribution, constructor1 -> constructor1.getParameterCount() > 5);
                    Object[] params = ReflectionUtils.initArray(constructor.getParameterTypes());
                    var lists = ReflectionUtils.findArrayOfType(constructor.getParameterTypes(), List.class);
                    params[lists.get(0).first] = listContacts;
                    params[lists.get(1).first] = new ArrayList();
                    Parcelable instance = (Parcelable) constructor.newInstance(params);
                    intent2.putExtra("status_distribution", instance);
                    activity.startActivityForResult(intent2, ContactPickerPreference.REQUEST_CONTACT_PICKER);
                }

            }
        });

        XposedHelpers.findAndHookMethod("com.whatsapp.status.audienceselector.StatusTemporalRecipientsActivity", classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;
                var intent = activity.getIntent();
                if (intent.getBooleanExtra("contact_mode", false)) {
                    var toolbar = XposedHelpers.callMethod(activity, "getSupportActionBar");
                    var methods = ReflectionUtils.findAllMethodsUsingFilter(toolbar.getClass(), method -> method.getParameterCount() == 1 && method.getParameterTypes()[0] == CharSequence.class);
                    ReflectionUtils.callMethod(methods[1], toolbar, activity.getString(ResId.string.select_contacts));
                }
            }
        });


        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult", int.class, int.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (clazz != param.thisObject.getClass()) return;
                var activity = (Activity) param.thisObject;
                Intent intent = (Intent) param.args[2];
                if ((int) param.args[0] == ContactPickerPreference.REQUEST_CONTACT_PICKER && intent != null) {
                    var instance = intent.getExtras().get("status_distribution");
                    var listContactsField = ReflectionUtils.findFieldUsingFilter(instance.getClass(), field -> field.getType() == List.class);
                    var listContacts = (List) ReflectionUtils.getObjectField(listContactsField, instance);
                    var contacts = new ArrayList<String>();
                    for (Object contact : listContacts) {
                        var rawContacts = WppCore.getRawString(contact);
                        contacts.add(rawContacts);
                    }
                    Intent intent2 = new Intent();
                    intent2.putStringArrayListExtra("contacts", contacts);
                    intent2.putExtra("key", Key);
                    activity.setResult(-1, intent2);
                }
                activity.finish();
            }
        });

    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Activity Controller";
    }

}
