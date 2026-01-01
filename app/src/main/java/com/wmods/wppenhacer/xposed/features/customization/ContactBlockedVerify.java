package com.wmods.wppenhacer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ContactBlockedVerify extends Feature {

    private Object meManagerInstance;

    public ContactBlockedVerify(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @SuppressLint("ResourceType")
    @NonNull
    private static TextView createTextMessageView(Activity activity) {
        var textView = new TextView(activity);
        textView.setId(0x7f990001);
        textView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        textView.setSingleLine(true);
        textView.setMarqueeRepeatLimit(-1);
        textView.setTextSize(10);
        textView.setFocusable(true);
        textView.setFocusableInTouchMode(true);
        textView.setSelected(true);
        textView.setTextColor(DesignUtils.getPrimaryTextColor());
        textView.setText(ResId.string.checking_if_the_contact_is_blocked);
        return textView;
    }

    @SuppressLint("ResourceType")
    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("verify_blocked_contact", false) ||
                Utils.getApplication().getPackageName().equals(FeatureLoader.PACKAGE_BUSINESS))
            return;

        Class<?> meManagerClass = Unobfuscator.loadMeManagerClass(classLoader);
        Class phoneJidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "PhoneUserJid");
        Field meManagerPhoneJidField = ReflectionUtils.getFieldByType(meManagerClass, phoneJidClass);

        Class verifyKeyClass = Unobfuscator.loadVerifyKeyClass(classLoader);

        XposedBridge.hookAllConstructors(meManagerClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                meManagerInstance = param.thisObject;
            }
        });

        WppCore.addListenerActivity((activity, state) -> {
            if (activity.getClass().getSimpleName().equals("Conversation") && state == WppCore.ActivityChangeState.ChangeType.STARTED) {
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                var userJid = WppCore.getCurrentUserJid();
                                var view = (ViewGroup) activity.findViewById(Utils.getID("conversation_contact", "id"));
                                if (userJid == null || !userJid.isContact() || view == null) {
                                    log("User is not a contact or view is null");
                                    return;
                                }
                                var textView = (TextView) view.findViewById(0x7f990001);
                                if (textView != null) {
                                    textView.setText(ResId.string.checking_if_the_contact_is_blocked);
                                    textView.setTextColor(DesignUtils.getPrimaryTextColor());
                                    textView.postInvalidate();
                                } else {
                                    var textView1 = createTextMessageView(activity);
                                    view.post(() -> view.addView(textView1));
                                }
                                var mePhoneJid = meManagerPhoneJidField.get(meManagerInstance);
                                var clazzInterface = verifyKeyClass.getDeclaredConstructors()[0].getParameterTypes()[0];
                                var methodResult = ReflectionUtils.findMethodUsingFilter(clazzInterface, method1 -> method1.getParameterCount() == 1 && method1.getParameterTypes()[0] == Integer.class);
                                var clazzProxy = Proxy.newProxyInstance(classLoader,
                                        new Class[]{clazzInterface},
                                        (o, method, objects) -> {
                                            if (Objects.equals(method.getName(), methodResult.getName())) {
                                                int value = (Integer) objects[0];
                                                if (!view.isAttachedToWindow()) return null;
                                                view.post(() -> {
                                                    var textView2 = (TextView) view.findViewById(0x7f990001);
                                                    if (textView2 == null) return;
                                                    textView2.setSelected(true);
                                                    if (value == 2) {
                                                        textView2.setText(ResId.string.possible_block_detected);
                                                        textView2.setTextColor(Color.RED);
                                                    } else {
                                                        textView2.setText(ResId.string.block_not_detected);
                                                        textView2.setTextColor(Color.GREEN);
                                                    }
                                                });
                                            }
                                            return null;
                                        });
                                var instance = XposedHelpers.newInstance(verifyKeyClass, clazzProxy, List.of(userJid.userJid, mePhoneJid));
                                XposedHelpers.callMethod(instance, "A00", 1);
                            } catch (Exception e) {
                                XposedBridge.log(e);
                            }
                        }
                );

            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Contact Blocked Verify";
    }
}
