package com.wmods.wppenhacer.xposed.features.privacy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONObject;

import java.lang.reflect.Method;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class CustomPrivacy extends Feature {
    private Method chatUserJidMethod;
    private Method groupUserJidMethod;

    public CustomPrivacy(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    public static JSONObject getJSON(String number) {
        if (!Utils.xprefs.getBoolean("custom_privacy", true) || TextUtils.isEmpty(number))
            return new JSONObject();
        return WppCore.getPrivJSON(number + "_privacy", new JSONObject());
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("custom_privacy", true)) return;

        Class<?> ContactInfoActivityClass = XposedHelpers.findClass("com.whatsapp.chatinfo.ContactInfoActivity", classLoader);
        Class<?> GroupInfoActivityClass = XposedHelpers.findClass("com.whatsapp.group.GroupChatInfoActivity", classLoader);
        Class<?> listItemWithLeftIconClass = XposedHelpers.findClass("com.whatsapp.ListItemWithLeftIcon", classLoader);
        Class<?> userJidClass = XposedHelpers.findClass("com.whatsapp.jid.UserJid", classLoader);
        Class<?> groupJidClass = XposedHelpers.findClass("com.whatsapp.jid.GroupJid", classLoader);

        chatUserJidMethod = ReflectionUtils.findMethodUsingFilter(ContactInfoActivityClass, method -> method.getParameterCount() == 0 && userJidClass.isAssignableFrom(method.getReturnType()));
        groupUserJidMethod = ReflectionUtils.findMethodUsingFilter(GroupInfoActivityClass, method -> method.getParameterCount() == 0 && groupJidClass.isAssignableFrom(method.getReturnType()));

        var hooker = new WppCore.ActivityChangeState() {
            @SuppressLint("ResourceType")
            @Override
            public void onChange(Object objActivity, ChangeType type) {
                try {
                    if (type != ChangeType.START) return;
                    if (!ContactInfoActivityClass.isInstance(objActivity) && !GroupInfoActivityClass.isInstance(objActivity))
                        return;
                    Activity activity = (Activity) objActivity;
                    if (activity.findViewById(0x7f0a9999) != null) return;
                    int id = Utils.getID("contact_info_security_card_layout", "id");
                    ViewGroup infoLayout = activity.getWindow().findViewById(id);
                    View itemView = (View) listItemWithLeftIconClass.getConstructor(Context.class).newInstance(activity);
                    itemView.setId(0x7f0a9999);
                    listItemWithLeftIconClass.getMethod("setTitle", CharSequence.class).invoke(itemView, activity.getString(ResId.string.custom_privacy));
                    listItemWithLeftIconClass.getMethod("setDescription", CharSequence.class).invoke(itemView, activity.getString(ResId.string.custom_privacy_sum));
                    listItemWithLeftIconClass.getMethod("setIcon", int.class).invoke(itemView, ResId.drawable.ic_privacy);
                    itemView.setOnClickListener((v) -> showPrivacyDialog(activity, ContactInfoActivityClass.isInstance(activity)));
                    infoLayout.addView(itemView);
                } catch (Throwable e) {
                    logDebug(e);
                    Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
                }
            }
        };
        WppCore.addListenerActivity(hooker);
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
        var json = CustomPrivacy.getJSON(number);
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
