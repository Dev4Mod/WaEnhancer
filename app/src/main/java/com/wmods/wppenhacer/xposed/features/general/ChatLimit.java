package com.wmods.wppenhacer.xposed.features.general;

import android.content.ContentValues;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ChatLimit extends Feature {
    public ChatLimit(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var antiDisappearing = prefs.getBoolean("antidisappearing", false);
        var revokeallmessages = prefs.getBoolean("revokeallmessages", false);

        var chatLimitDeleteMethod = Unobfuscator.loadChatLimitDeleteMethod(classLoader);
        var chatLimitDelete2Method = Unobfuscator.loadChatLimitDelete2Method(classLoader);
        var fmessageTimestampMethod = Unobfuscator.loadFmessageTimestampField(classLoader);

        var epUpdateMethod = Unobfuscator.loadEphemeralInsertdb(classLoader);

        XposedHelpers.findAndHookMethod(WppCore.getHomeActivityClass(classLoader), "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (antiDisappearing) {
                    MessageStore.getInstance().executeSQL("UPDATE message_ephemeral SET expire_timestamp = 2553512370000");
                }
            }
        });

        XposedBridge.hookMethod(epUpdateMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (antiDisappearing) {
                    var contentValues = (ContentValues) param.getResult();
                    contentValues.put("expire_timestamp", 2553512370000L);
                }
            }
        });


//        var chatLimitEditClass = Unobfuscator.loadChatLimitEditClass(loader);

//        if (prefs.getBoolean("editallmessages", false)) {
//            Others.propsInteger.put(2983, Integer.MAX_VALUE);
//            Others.propsInteger.put(3272, Integer.MAX_VALUE);
//        }


        if (revokeallmessages) {
            XposedBridge.hookMethod(chatLimitDelete2Method, new XC_MethodHook() {
                private Unhook unhooked;

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    var list = ReflectionUtils.findArrayOfType(param.args, Set.class);
                    if (list.isEmpty()) return;
                    var listMessages = (Set) list.get(0).second;
                    var isExpired = false;
                    for (var fmessageObj : listMessages) {
                        var timestamp = fmessageTimestampMethod.getLong(fmessageObj);
                        // verify message is expired (max: 3 days)
                        if (System.currentTimeMillis() - timestamp > 3 * 24 * 60 * 60 * 1000) {
                            isExpired = true;
                            break;
                        }
                    }
                    if (!isExpired) {
                        unhooked = XposedBridge.hookMethod(chatLimitDeleteMethod, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                if (ReflectionUtils.isCalledFromMethod(chatLimitDelete2Method)) {
                                    param.setResult(0L);
                                }
                            }
                        });
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (unhooked != null) {
                        unhooked.unhook();
                    }
                }
            });
        }

        var seeMoreMethod = Unobfuscator.loadSeeMoreConstructor(classLoader);
        XposedBridge.hookMethod(seeMoreMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("removeseemore", false)) return;
                param.args[1] = Integer.MAX_VALUE;
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Chat Limit";
    }
}
