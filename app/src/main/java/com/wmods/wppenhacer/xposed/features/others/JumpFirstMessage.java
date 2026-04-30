package com.wmods.wppenhacer.xposed.features.others;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.Menu;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ResId;

import org.luckypray.dexkit.query.enums.StringMatchType;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class JumpFirstMessage extends Feature {

    public JumpFirstMessage(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("jump_first_message", false)) return;
        var onCreateMenuConversationMethod = Unobfuscator.loadOnCreatedMenuConversation(classLoader);
        XposedBridge.hookMethod(onCreateMenuConversationMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    var menu = (Menu) param.args[0];
                    if (menu.findItem(ResId.string.jump_first_message) != null) {
                        return;
                    }
                    var menuItem = menu.add(0, ResId.string.jump_first_message, 0, ResId.string.jump_first_message);
                    menuItem.setOnMenuItemClickListener(item -> {
                        var activity = WppCore.getCurrentActivity();
                        if (activity == null) {
                            return false;
                        }
                        jumpToFirstMessage(activity);
                        return true;
                    });
                } catch (Exception e) {
                    logDebug(e);
                }
            }
        });
    }

    private void jumpToFirstMessage(@NonNull Activity activity) {
        var userJid = WppCore.getCurrentUserJid();
        if (userJid == null || userJid.isNull()) {
            return;
        }

        var rawJid = userJid.getPhoneRawString();
        if (rawJid == null || rawJid.isEmpty()) {
            rawJid = userJid.getUserRawString();
        }
        if (rawJid == null || rawJid.isEmpty()) {
            return;
        }

        var firstMessageInfo = MessageStore.getInstance().getFirstMessageInfoByChatRawJid(rawJid);
        if (firstMessageInfo == null) {
            return;
        }

        try {
            Class<?> conversationClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "Conversation");
            Intent intent = new Intent(activity, conversationClass);
            intent.putExtra("jid", rawJid);
            intent.putExtra("sort_id", firstMessageInfo.sortId());
            intent.putExtra("row_id", firstMessageInfo.rowId());
            intent.putExtra("start_t", SystemClock.uptimeMillis());
            intent.putExtra("mat_entry_point", 64);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
        } catch (Exception e) {
            logDebug(e);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Jump First Message";
    }
}