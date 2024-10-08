package com.wmods.wppenhacer.xposed.features.privacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.utils.DebugUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class CustomPrivacy extends Feature {
    private Method userJidMethod;

    public CustomPrivacy(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        Class<?> ContactInfoActivityClass = classLoader.loadClass("com.whatsapp.chatinfo.ContactInfoActivity");
        var userJidClass = classLoader.loadClass("com.whatsapp.jid.UserJid");
        userJidMethod = ReflectionUtils.findMethodUsingFilter(ContactInfoActivityClass, method -> method.getParameterCount() == 0 && method.getReturnType() == userJidClass);

        XposedHelpers.findAndHookMethod(ContactInfoActivityClass, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                ViewGroup infoLayout = activity.findViewById(Utils.getID("contact_info_security_card_layout", "id"));
                Class listItemWithLeftIconClass = classLoader.loadClass("com.whatsapp.ListItemWithLeftIcon");
                Object itemView = XposedHelpers.newInstance(listItemWithLeftIconClass, activity);
                XposedHelpers.callMethod(itemView, "setTitle", activity.getString(ResId.string.custom_privacy));
                XposedHelpers.callMethod(itemView, "setDescription", activity.getString(ResId.string.custom_privacy_sum));
                XposedHelpers.callMethod(itemView, "setIcon", ResId.drawable.ic_privacy);
                View layoutItem = (View) itemView;
                layoutItem.setOnClickListener((v) -> {
                    showPrivacyDialog(activity);
                });
                infoLayout.addView(layoutItem);
            }
        });
    }

    private void showPrivacyDialog(Activity activity) {
        var userJid = ReflectionUtils.callMethod(userJidMethod, activity);
        if (userJid == null) return;
        var rawJid = WppCore.getRawString(userJid);
        var number = WppCore.stripJID(rawJid);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(ResId.string.custom_privacy);
        String[] items = {activity.getString(ResId.string.hideread), activity.getString(ResId.string.hidestatusview), activity.getString(ResId.string.hidereceipt), activity.getString(ResId.string.ghostmode), activity.getString(ResId.string.ghostmode_r)};
        String[] itemsKeys = {"HideSeen", "HideViewStatus", "HideReceipt", "HideTyping", "HideRecording"};
        boolean[] checkedItems = new boolean[items.length];
        var jsonStr = WppCore.getPrivString(number + "_privacy", null);
        if (jsonStr != null) {
            try {
                var json = new JSONObject(jsonStr);
                for (int i = 0; i < itemsKeys.length; i++) {
                    if (json.optBoolean(itemsKeys[i], false)) {
                        checkedItems[i] = true;
                    }
                }
            } catch (JSONException e) {
                Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
            }
        }
        builder.setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                JSONObject jsonObject = new JSONObject();
                for (int i = 0; i < itemsKeys.length; i++) {
                    jsonObject.put(itemsKeys[i], checkedItems[i]);
                }
                WppCore.setPrivString(number + "_privacy", jsonObject.toString());
            } catch (Exception e) {
                Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
            }
        });
        builder.setNegativeButton(ResId.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Custom Privacy";
    }
}
