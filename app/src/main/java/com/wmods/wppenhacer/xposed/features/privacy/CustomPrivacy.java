package com.wmods.wppenhacer.xposed.features.privacy;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.DebugUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONObject;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CustomPrivacy extends Feature {
    private Method chatUserJidMethod;
    private Method groupUserJidMethod;

    public CustomPrivacy(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    public static boolean getBoolean(JSONObject json, String json_key, XSharedPreferences prefs, String prefkey) {
        return json.optBoolean(json_key, prefs.getBoolean(prefkey, false));
    }

    @Override
    public void doHook() throws Throwable {
        Class<?> ContactInfoActivityClass = XposedHelpers.findClass("com.whatsapp.chatinfo.ContactInfoActivity", classLoader);
        Class<?> GroupInfoActivityClass = XposedHelpers.findClass("com.whatsapp.group.GroupChatInfoActivity", classLoader);
        Class<?> listItemWithLeftIconClass = XposedHelpers.findClass("com.whatsapp.ListItemWithLeftIcon", classLoader);
        Class<?> userJidClass = XposedHelpers.findClass("com.whatsapp.jid.UserJid", classLoader);
        Class<?> groupJidClass = XposedHelpers.findClass("com.whatsapp.jid.GroupJid", classLoader);

        chatUserJidMethod = ReflectionUtils.findMethodUsingFilter(ContactInfoActivityClass, method -> method.getParameterCount() == 0 && userJidClass.isAssignableFrom(method.getReturnType()));
        groupUserJidMethod = ReflectionUtils.findMethodUsingFilter(GroupInfoActivityClass, method -> method.getParameterCount() == 0 && groupJidClass.isAssignableFrom(method.getReturnType()));

        XC_MethodHook hooker = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                DebugUtils.debugFields(GroupInfoActivityClass, activity);
                int id = Utils.getID("contact_info_security_card_layout", "id");
                if (id == -1) {
                    throw new Exception("contact_info_security_card_layout not found");
                }
                ViewGroup infoLayout = activity.getWindow().findViewById(id);
                View itemView = (View) listItemWithLeftIconClass.getConstructor(Context.class).newInstance(activity);
                XposedHelpers.callMethod(itemView, "setTitle", activity.getString(ResId.string.custom_privacy));
                XposedHelpers.callMethod(itemView, "setDescription", activity.getString(ResId.string.custom_privacy_sum));
                XposedHelpers.callMethod(itemView, "setIcon", ResId.drawable.ic_privacy);
                itemView.setOnClickListener((v) -> showPrivacyDialog(activity, ContactInfoActivityClass.isInstance(activity)));
                infoLayout.addView(itemView);
            }
        };

        XposedBridge.hookAllMethods(ContactInfoActivityClass, "onCreate", hooker);
        XposedBridge.hookAllMethods(GroupInfoActivityClass, "onCreate", hooker);
    }

    private void showPrivacyDialog(Activity activity, boolean isChat) {
        Object userJid;
        if (isChat) {
            userJid = ReflectionUtils.callMethod(chatUserJidMethod, activity);
        } else {
            userJid = ReflectionUtils.callMethod(groupUserJidMethod, activity);
        }
        if (userJid == null) return;
        var rawJid = WppCore.getRawString(userJid);
        var number = WppCore.stripJID(rawJid);
        var builder = new AlertDialogWpp(activity);
        builder.setTitle(ResId.string.custom_privacy);
        String[] items = {activity.getString(ResId.string.hideread), activity.getString(ResId.string.hidestatusview), activity.getString(ResId.string.hidereceipt), activity.getString(ResId.string.ghostmode), activity.getString(ResId.string.ghostmode_r)};
        String[] itemsKeys = {"HideSeen", "HideViewStatus", "HideReceipt", "HideTyping", "HideRecording"};
        String[] globalKeys = {"hideread", "hidestatusview", "hidereceipt", "ghostmode_t", "ghostmode_r"};

        // load prefs
        boolean[] checkedItems = new boolean[items.length];
        var json = WppCore.getPrivJSON(number + "_privacy", new JSONObject());
        for (int i = 0; i < itemsKeys.length; i++) {
            checkedItems[i] = json.optBoolean(itemsKeys[i], prefs.getBoolean(globalKeys[i], false));
        }

        builder.setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                JSONObject jsonObject = new JSONObject();
                for (int i = 0; i < itemsKeys.length; i++) {
                    if (prefs.getBoolean(globalKeys[i], false) != checkedItems[i])
                        jsonObject.put(itemsKeys[i], checkedItems[i]);
                }
                WppCore.setPrivJSON(number + "_privacy", jsonObject);
            } catch (Exception e) {
                Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
            }
        });
        builder.setNegativeButton(activity.getString(ResId.string.cancel), null);
        builder.show();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Custom Privacy";
    }
}
