package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class HideChat extends Feature {

//    public static View.OnClickListener mClickListenerLocked;

    public HideChat(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!Objects.equals(prefs.getString("typearchive", "0"), "0")) {

            var archiveHideViewMethod = Unobfuscator.loadArchiveHideViewMethod(classLoader);
            for (var method : archiveHideViewMethod) {
                logDebug(Unobfuscator.getMethodDescriptor(method));
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.args[0] = false;
                    }
                });
            }

        }

//        if (prefs.getBoolean("hidelocked", false)) {
//
//            var lockedChatFrame = Unobfuscator.loadArchiveLockedChatClass(classLoader);
//            log("Locked Class: " + lockedChatFrame);
//            XposedBridge.hookMethod(lockedChatFrame.getMethod("setOnLockedClickListener", View.OnClickListener.class), new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                    mClickListenerLocked = (View.OnClickListener) param.args[0];
//                }
//            });
//
//            var runMethod = ReflectionUtils.findMethodUsingFilter(lockedChatFrame, method -> method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(Runnable.class));
//            XposedBridge.hookMethod(runMethod, new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                    ((Runnable) param.args[0]).run();
//                }
//            });
//
//            var method1 = Unobfuscator.loadArchiveCheckLockedChatsMethod(classLoader);
//            var method2 = Unobfuscator.loadArchiveCheckLockedChatsMethod2(classLoader);
//            XposedBridge.hookMethod(method1, new XC_MethodHook() {
//                private Unhook hooked;
//
//                @Override
//                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                    hooked = XposedBridge.hookMethod(method2, XC_MethodReplacement.returnConstant(true));
//                    Others.propsBoolean.put(7280, false);
//                }
//
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    hooked.unhook();
//                    Others.propsBoolean.remove(7280);
//                }
//            });
//        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Archive";
    }
}
