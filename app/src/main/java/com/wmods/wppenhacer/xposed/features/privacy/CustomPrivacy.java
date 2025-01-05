package com.wmods.wppenhacer.xposed.features.privacy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.adapter.CustomPrivacyAdapter;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.features.others.MenuHome;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class CustomPrivacy extends Feature {
    private Method chatUserJidMethod;
    private Method groupUserJidMethod;

    public CustomPrivacy(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    public static JSONObject getJSON(String number) {
        if (Objects.equals(Utils.xprefs.getString("custom_privacy_type", "0"), "0") || TextUtils.isEmpty(number))
            return new JSONObject();
        return WppCore.getPrivJSON(number + "_privacy", new JSONObject());
    }

    @Override
    public void doHook() throws Throwable {
        if (Objects.equals(Utils.xprefs.getString("custom_privacy_type", "0"), "0")) return;

        Class<?> ContactInfoActivityClass = XposedHelpers.findClass("com.whatsapp.chatinfo.ContactInfoActivity", classLoader);
        Class<?> GroupInfoActivityClass = XposedHelpers.findClass("com.whatsapp.group.GroupChatInfoActivity", classLoader);
        Class<?> listItemWithLeftIconClass = XposedHelpers.findClass("com.whatsapp.ListItemWithLeftIcon", classLoader);
        Class<?> userJidClass = XposedHelpers.findClass("com.whatsapp.jid.UserJid", classLoader);
        Class<?> groupJidClass = XposedHelpers.findClass("com.whatsapp.jid.GroupJid", classLoader);

        chatUserJidMethod = ReflectionUtils.findMethodUsingFilter(ContactInfoActivityClass, method -> method.getParameterCount() == 0 && userJidClass.isAssignableFrom(method.getReturnType()));
        groupUserJidMethod = ReflectionUtils.findMethodUsingFilter(GroupInfoActivityClass, method -> method.getParameterCount() == 0 && groupJidClass.isAssignableFrom(method.getReturnType()));

        var type = Integer.parseInt(Utils.xprefs.getString("custom_privacy_type", "0"));

        if (type == 1) {
            var hooker = new WppCore.ActivityChangeState() {
                @SuppressLint("ResourceType")
                @Override
                public void onChange(Activity activity, ChangeType type) {
                    try {
                        if (type != ChangeType.START) return;
                        if (!ContactInfoActivityClass.isInstance(activity) && !GroupInfoActivityClass.isInstance(activity))
                            return;
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
        } else if (type == 2) {
            var hooker = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var menu = (Menu) param.args[0];
                    var activity = (Activity) param.thisObject;
                    var customPrivacy = menu.add(0, 0, 0, ResId.string.custom_privacy);
                    customPrivacy.setIcon(ResId.drawable.ic_privacy);
                    customPrivacy.setOnMenuItemClickListener(item -> {
                        showPrivacyDialog(activity, ContactInfoActivityClass.isInstance(activity));
                        return true;
                    });
                }
            };
            XposedHelpers.findAndHookMethod(ContactInfoActivityClass, "onCreateOptionsMenu", Menu.class, hooker);
            XposedHelpers.findAndHookMethod(GroupInfoActivityClass, "onCreateOptionsMenu", Menu.class, hooker);
        }

        if (type == 0) return;

        var icon = DesignUtils.resizeDrawable(DesignUtils.getDrawable(ResId.drawable.ic_privacy), Utils.dipToPixels(24), Utils.dipToPixels(24));
        icon.setTint(0xff8696a0);
        MenuHome.menuItems.add((menu, activity) -> menu.add(0, 0, 0, ResId.string.custom_privacy).setIcon(icon).setOnMenuItemClickListener(item -> {
            showCustomPrivacyList(activity, ContactInfoActivityClass, GroupInfoActivityClass);
            return true;
        }));
    }

    private void showCustomPrivacyList(Activity activity, Class<?> contactClass, Class<?> groupClass) {

        SharedPreferences pprefs = WppCore.getPrivPrefs();
        var maps = pprefs.getAll();
        ArrayList<CustomPrivacyAdapter.Item> list = new ArrayList<>();
        for (var key : maps.keySet()) {
            if (key.endsWith("_privacy")) {
                var number = key.replace("_privacy", "");
                Object userJid = WppCore.createUserJid(number + (number.length() > 14 ? "@g.us" : "@s.whatsapp.net"));

                var contactName = WppCore.getContactName(userJid);

                if (TextUtils.isEmpty(contactName)) {
                    contactName = number;
                }
                CustomPrivacyAdapter.Item item = new CustomPrivacyAdapter.Item();
                item.name = contactName;
                item.number = number;
                item.key = key;
                list.add(item);
            }
        }

        if (list.isEmpty()) {
            Utils.showToast(activity.getString(ResId.string.no_contact_with_custom_privacy), Toast.LENGTH_SHORT);
            return;
        }

        AlertDialogWpp builder = new AlertDialogWpp(activity);
        builder.setTitle(ResId.string.custom_privacy);
        ListView listView = new ListView(activity);
        listView.setAdapter(new CustomPrivacyAdapter(activity, pprefs, list, contactClass, groupClass));
        builder.setView(listView);
        builder.show();
    }


    private void showPrivacyDialog(Activity activity, boolean isChat) {
        Object userJid = getUserJid(activity, isChat);
        if (userJid == null) return;

        String rawJid = WppCore.getRawString(userJid);
        String number = WppCore.stripJID(rawJid);

        AlertDialogWpp builder = createPrivacyDialog(activity, number);
        builder.show();
    }

    private Object getUserJid(Activity activity, boolean isChat) {
        if (isChat) {
            return ReflectionUtils.callMethod(chatUserJidMethod, activity);
        } else {
            return ReflectionUtils.callMethod(groupUserJidMethod, activity);
        }
    }

    private AlertDialogWpp createPrivacyDialog(Activity activity, String number) {
        AlertDialogWpp builder = new AlertDialogWpp(activity);
        builder.setTitle(ResId.string.custom_privacy);

        String[] items = {
                activity.getString(ResId.string.hideread),
                activity.getString(ResId.string.hidestatusview),
                activity.getString(ResId.string.hidereceipt),
                activity.getString(ResId.string.ghostmode),
                activity.getString(ResId.string.ghostmode_r),
                activity.getString(ResId.string.block_call)
        };

        String[] itemsKeys = {
                "HideSeen", "HideViewStatus", "HideReceipt", "HideTyping", "HideRecording", "BlockCall"
        };

        boolean[] checkedItems = loadPreferences(number, itemsKeys);

        builder.setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked);
        builder.setPositiveButton("OK", (dialog, which) -> savePreferences(number, itemsKeys, checkedItems));
        builder.setNegativeButton(activity.getString(ResId.string.cancel), null);

        return builder;
    }

    private boolean[] loadPreferences(String number, String[] itemsKeys) {
        boolean[] checkedItems = new boolean[itemsKeys.length];
        JSONObject json = CustomPrivacy.getJSON(number);

        for (int i = 0; i < itemsKeys.length; i++) {
            String globalKey = getGlobalKey(itemsKeys[i]);
            checkedItems[i] = json.optBoolean(itemsKeys[i], getDefaultPreference(globalKey));
        }

        return checkedItems;
    }

    private String getGlobalKey(String itemKey) {
        return switch (itemKey) {
            case "HideSeen" -> "hideread";
            case "HideViewStatus" -> "hidestatusview";
            case "HideReceipt" -> "hidereceipt";
            case "HideTyping" -> "ghostmode_t";
            case "HideRecording" -> "ghostmode_r";
            case "BlockCall" -> "call_privacy";
            default -> "";
        };
    }

    private boolean getDefaultPreference(String globalKey) {
        if (globalKey.equals("call_privacy")) {
            return Objects.equals(prefs.getString(globalKey, "0"), "1");
        } else {
            return prefs.getBoolean(globalKey, false);
        }
    }

    private void savePreferences(String number, String[] itemsKeys, boolean[] checkedItems) {
        try {
            JSONObject jsonObject = new JSONObject();
            for (int i = 0; i < itemsKeys.length; i++) {
                String globalKey = getGlobalKey(itemsKeys[i]);
                if (globalKey.equals("call_privacy")) {
                    if (Objects.equals(prefs.getString(globalKey, "0"), "1") != checkedItems[i])
                        jsonObject.put(itemsKeys[i], checkedItems[i]);
                } else {
                    if (prefs.getBoolean(globalKey, false) != checkedItems[i])
                        jsonObject.put(itemsKeys[i], checkedItems[i]);
                }
            }
            WppCore.setPrivJSON(number + "_privacy", jsonObject);
        } catch (Exception e) {
            Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Custom Privacy";
    }
}
