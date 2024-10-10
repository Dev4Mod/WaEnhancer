package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONObject;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HideSeen extends Feature {

    public HideSeen(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {


        Method SendReadReceiptJobMethod = Unobfuscator.loadHideViewSendReadJob(classLoader);
        var sendJob = XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendReadReceiptJob", classLoader);
        log(Unobfuscator.getMethodDescriptor(SendReadReceiptJobMethod));

        var ghostmode = WppCore.getPrivBoolean("ghostmode", false);
        var hideread = prefs.getBoolean("hideread", false);
        var hideaudioseen = prefs.getBoolean("hideaudioseen", false);
        var hideonceseen = prefs.getBoolean("hideonceseen", false);
        var hideread_group = prefs.getBoolean("hideread_group", false);
        var hidestatusview = prefs.getBoolean("hidestatusview", false);

        XposedBridge.hookMethod(SendReadReceiptJobMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!sendJob.isInstance(param.thisObject)) return;
                var srj = sendJob.cast(param.thisObject);
                var messageIds = XposedHelpers.getObjectField(srj, "messageIds");
                var firstmessage = (String) Array.get(messageIds, 0);
                if (firstmessage != null && WppCore.getPrivBoolean(firstmessage + "_rpass", false)) {
                    WppCore.removePrivKey(firstmessage + "_rpass");
                    return;
                }
                var jid = (String) XposedHelpers.getObjectField(srj, "jid");
                if (jid == null) return;
                var stripJid = WppCore.stripJID(jid);
                var privacy = WppCore.getPrivJSON(stripJid + "_privacy", new JSONObject());
                var customHideRead = privacy.optBoolean("HideSeen", hideread);
                var cutomHideStatusView = privacy.optBoolean("HideViewStatus", hidestatusview);

                if (WppCore.isGroup(jid)) {
                    if (privacy.optBoolean("HideSeen", hideread_group) || ghostmode) {
                        param.setResult(null);
                    }
                } else if (jid.startsWith("status")) {
                    if (cutomHideStatusView || ghostmode) {
                        param.setResult(null);
                    }
                } else if (customHideRead || ghostmode) {
                    param.setResult(null);
                }

            }
        });

        Method hideViewInChatMethod = Unobfuscator.loadHideViewInChatMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(hideViewInChatMethod));

        Method hideViewMethod = Unobfuscator.loadHideViewMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(hideViewMethod));

        XposedBridge.hookMethod(hideViewMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!ReflectionUtils.isCalledFromMethod(hideViewInChatMethod)) return;
                if (param.args[4] == null || !param.args[4].equals("read")) return;
                var jid = WppCore.getCurrentRawJID();
                var stripJid = WppCore.stripJID(jid);
                var privacy = WppCore.getPrivJSON(stripJid + "_privacy", new JSONObject());
                var customHideRead = privacy.optBoolean("HideSeen", false);

                if (WppCore.isGroup(jid)) {
                    if (hideread_group)
                        param.args[4] = null;
                    if (customHideRead) {
                        param.args[4] = null;
                    }
                } else if (hideread) {
                    param.args[4] = null;
                } else if (customHideRead) {
                    param.args[4] = null;
                }
            }
        });

        var loadSenderPlayed = Unobfuscator.loadSenderPlayed(classLoader);
        XposedBridge.hookMethod(loadSenderPlayed, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var fMessage = new FMessageWpp(param.args[0]);
                logDebug("MEDIA TYPE", fMessage.getMediaType());
                var media_type = fMessage.getMediaType();  // 2 = voice note ; 82 = viewonce note voice; 42 = image view once; 43 = video view once
                if (hideonceseen && (media_type == 82 || media_type == 42 || media_type == 43)) {
                    param.setResult(null);
                } else if (hideaudioseen && media_type == 2) {
                    param.setResult(null);
                }
            }
        });

        if (Utils.getApplication().getPackageName().equals(FeatureLoader.PACKAGE_BUSINESS)) {
            var loadSenderPlayedBusiness = Unobfuscator.loadSenderPlayedBusiness(classLoader);
            XposedBridge.hookMethod(loadSenderPlayedBusiness, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    var set = (Set) param.args[0];
                    if (set != null && !set.isEmpty()) {
                        var fMessage = new FMessageWpp(set.iterator().next());
                        logDebug("MEDIA TYPE", fMessage.getMediaType());
                        var media_type = fMessage.getMediaType();  // 2 = voice note ; 82 = viewonce note voice; 42 = image view once; 43 = video view once
                        if (hideonceseen && (media_type == 82 || media_type == 42 || media_type == 43)) {
                            param.setResult(null);
                        } else if (hideaudioseen && media_type == 2) {
                            param.setResult(null);
                        }
                    }
                }
            });
        }

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Seen";
    }

}
