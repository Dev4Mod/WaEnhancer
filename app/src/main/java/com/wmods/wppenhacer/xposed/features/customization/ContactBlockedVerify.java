package com.wmods.wppenhacer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.general.Others;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ContactBlockedVerify extends Feature {

    private static final String TAG_CONTACT_CHECKER = "contact_checker";
    private static final int STATUS_NOT_ADDED = 401;
    private static final int STATUS_POSSIBLE_BLOCKED = 2;
    private static final long VERIFY_TIMEOUT_MS = 2000L;
    private static final long VERIFY_WAIT_MS = 20_000L;

    private final ContactCheckState state = new ContactCheckState();

    public ContactBlockedVerify(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @SuppressLint("ResourceType")
    @NonNull
    private static TextView createTextMessageView(Activity activity) {
        var textView = new TextView(activity);
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
        if (!prefs.getBoolean("verify_blocked_contact", false))
            return;

        Others.propsBoolean.put(2966, true);


        Class<?> sendGetProfilePhoto = Unobfuscator.loadGetProfilePhoto(classLoader);
        state.sendGetProfilePhoto = sendGetProfilePhoto;
        var profilePhotoProtocolHelper = sendGetProfilePhoto.getConstructors()[0].getParameterTypes()[2];
        initProfilePhotoProtocolHooks(profilePhotoProtocolHelper);
        Class<?> dialerProfilePictureLoader = Unobfuscator.loadDialerProfilePictureLoader(classLoader);
        initProfilePhotoCallbacks(dialerProfilePictureLoader);

        var verifyKeyStrategy = buildVerifyKeyStrategy();
        registerActivityListener(verifyKeyStrategy.callbackInterface, verifyKeyStrategy.invoker);
    }

    @NonNull
    private VerifyKeyStrategy buildVerifyKeyStrategy() throws Exception {
        try {
            Class<?> verifyKeyClass = Unobfuscator.loadVerifyKeyClass(classLoader);
            var callbackInterface = verifyKeyClass.getDeclaredConstructors()[0].getParameterTypes()[0];
            VerifyKeyInvoker invoker = (proxyInstance, jids) -> {
                var instance = XposedHelpers.newInstance(verifyKeyClass, proxyInstance, jids);
                XposedHelpers.callMethod(instance, "A00", 1);
            };
            return new VerifyKeyStrategy(callbackInterface, invoker);
        } catch (Exception ignored) {
            Constructor<?> verifyKeyItemConstructor = Unobfuscator.loadVerifyKeyItemConstructor(classLoader);
            Constructor<?> verifyKeyRunnableConstructor = Unobfuscator.loadVerifyKeyRunnableConstructor(classLoader);
            log(verifyKeyRunnableConstructor);
            var number = Unobfuscator.loadVerifyKeyInt(classLoader);
            var callbackInterface = verifyKeyItemConstructor.getParameterTypes()[0];
            VerifyKeyInvoker invoker = (proxyInstance, jids) -> {
                var instance = verifyKeyItemConstructor.newInstance(proxyInstance, jids);
                var runInstance = (Runnable) verifyKeyRunnableConstructor.newInstance(instance, number);
                CompletableFuture.runAsync(runInstance);
            };
            return new VerifyKeyStrategy(callbackInterface, invoker);
        }
    }

    private void registerActivityListener(Class<?> callbackInterface, VerifyKeyInvoker invoker) {
        WppCore.addListenerActivity((activity, state) -> {
            if (activity.getClass().getSimpleName().equals("Conversation") && state == WppCore.ActivityChangeState.ChangeType.STARTED) {
                CompletableFuture.runAsync(
                        () -> onConversationStarted(activity, callbackInterface, invoker)
                );
            }
        });
    }

    private void onConversationStarted(Activity activity, Class<?> callbackInterface, VerifyKeyInvoker invoker) {
        try {
            var userJid = WppCore.getCurrentUserJid();
            var meJid = WppCore.getMyUserJid();
            var view = (ViewGroup) activity.findViewById(Utils.getID("conversation_contact", "id"));
            if (userJid == null || !userJid.isContact() || view == null || meJid == null) {
                return;
            }
            var textView = resolveContactChecker(activity, view);
            showChecking(textView);
            var methodResult = ReflectionUtils.findMethodUsingFilter(callbackInterface, method1 -> method1.getParameterCount() == 1 && method1.getParameterTypes()[0] == Integer.class);
            long startTime = System.currentTimeMillis();
            var isloaded = new AtomicBoolean(false);
            var clazzProxy = Proxy.newProxyInstance(classLoader,
                    new Class[]{callbackInterface},
                    (o, method, objects) -> {
                        if (Objects.equals(method.getName(), methodResult.getName())) {
                            isloaded.set(true);
                            int value = (Integer) objects[0];
                            if (!view.isAttachedToWindow()) return null;
                            view.post(() -> onVerifyResult(view, userJid, value, startTime));
                        }
                        return null;
                    });
            var jids = List.of(userJid.userJid, meJid.phoneJid);
            invoker.invoke(clazzProxy, jids);
            // Wait for 20s
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isloaded.get()) {
                    showUnverified(textView);
                }
            }, VERIFY_WAIT_MS);

        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }


    private void initProfilePhotoProtocolHooks(Class<?> profilePhotoProtocolHelper) {
        XposedBridge.hookAllConstructors(profilePhotoProtocolHelper, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                state.profilePhotoProtocol = param.thisObject;
            }
        });
    }

    private void initProfilePhotoCallbacks(Class<?> dialerProfilePictureLoader) {
        var methods = ReflectionUtils.findAllMethodsUsingFilter(dialerProfilePictureLoader, method -> method.getName().length() == 3 && method.getParameterCount() > 1);
        var onSuccess = Arrays.stream(methods).filter(method -> !List.of(method.getParameterTypes()).contains(String.class)).findFirst().orElseThrow();
        var onError = Arrays.stream(methods).filter(method -> List.of(method.getParameterTypes()).contains(String.class)).findFirst().orElseThrow();
        XposedBridge.hookMethod(onSuccess, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var fieldUserJid = ReflectionUtils.getFieldByExtendType(param.args[0].getClass(), FMessageWpp.UserJid.TYPE_JID);
                var userJid = new FMessageWpp.UserJid(fieldUserJid.get(param.args[0]));
                if (!Objects.equals(state.pendingUserJid.get(), userJid.getUserRawString())) return;
                state.pendingUserJid.set(null);
                var tv = state.checkerView.get();
                if (tv == null) return;
                showNotBlocked(tv);
            }
        });
        XposedBridge.hookMethod(onError, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var userJid = new FMessageWpp.UserJid(param.args[0]);
                if (!Objects.equals(state.pendingUserJid.get(), userJid.getUserRawString())) return;
                state.pendingUserJid.set(null);
                var status = ReflectionUtils.getArg(param.args, Integer.class, 0);
                var tv = state.checkerView.get();
                if (tv == null) return;
                if (status == STATUS_NOT_ADDED) {
                    showProbablyNotAdded(tv);
                }
            }
        });
    }

    private void onVerifyResult(ViewGroup view, FMessageWpp.UserJid userJid, int value, long startTime) {
        var textView = (TextView) view.findViewWithTag(TAG_CONTACT_CHECKER);
        if (textView == null) return;
        textView.setSelected(true);
        if (System.currentTimeMillis() - startTime < VERIFY_TIMEOUT_MS) {
            showUnverified(textView);
            return;
        }
        if (value == STATUS_POSSIBLE_BLOCKED) {
            showPossibleBlocked(textView);
        } else {
            checkContactPhotoProfile(userJid);
        }
    }

    @NonNull
    private TextView resolveContactChecker(Activity activity, ViewGroup view) {
        var textView = (TextView) view.findViewWithTag(TAG_CONTACT_CHECKER);
        if (textView != null) {
            state.checkerView.set(textView);
            return textView;
        }
        var created = createTextMessageView(activity);
        state.checkerView.set(created);
        created.setTag(TAG_CONTACT_CHECKER);
        view.post(() -> view.addView(created));
        return created;
    }

    private void showChecking(TextView textView) {
        textView.post(() -> {
            textView.setText(ResId.string.checking_if_the_contact_is_blocked);
            textView.setTextColor(DesignUtils.getPrimaryTextColor());
        });
    }

    private void showNotBlocked(TextView textView) {
        textView.post(() -> {
            textView.setTextColor(Color.GREEN);
            textView.setText(ResId.string.block_not_detected);
        });
    }

    private void showProbablyNotAdded(TextView textView) {
        textView.post(() -> {
            textView.setTextColor(Color.YELLOW);
            textView.setText(ResId.string.contact_probably_not_added);
        });
    }

    private void showPossibleBlocked(TextView textView) {
        textView.post(() -> {
            textView.setText(ResId.string.possible_block_detected);
            textView.setTextColor(Color.RED);
        });
    }

    private void showUnverified(TextView textView) {
        textView.post(() -> {
            textView.setText(ResId.string.block_unverified);
            textView.setTextColor(DesignUtils.getPrimaryTextColor());
        });
    }

    private void checkContactPhotoProfile(FMessageWpp.UserJid userJid) {
        try {
            var sendGetProfilePhoto = state.sendGetProfilePhoto;
            if (sendGetProfilePhoto == null) {
                return;
            }
            var params = ReflectionUtils.initArray(sendGetProfilePhoto.getConstructors()[0].getParameterTypes());
            params[2] = state.profilePhotoProtocol;
            params[3] = userJid.userJid;
            var runnable = (Runnable) sendGetProfilePhoto.getConstructors()[0].newInstance(params);
            this.state.pendingUserJid.set(userJid.getUserRawString());
            CompletableFuture.runAsync(runnable);
        } catch (Exception e) {
            logDebug(e);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Contact Blocked Verify";
    }

    @FunctionalInterface
    private interface VerifyKeyInvoker {
        void invoke(Object proxyInstance, List<Object> jids) throws Exception;
    }

    private record VerifyKeyStrategy(Class<?> callbackInterface, VerifyKeyInvoker invoker) {
    }

    private static final class ContactCheckState {
        private volatile Object profilePhotoProtocol;
        private Class<?> sendGetProfilePhoto;
        private final AtomicReference<TextView> checkerView = new AtomicReference<>();
        private final AtomicReference<String> pendingUserJid = new AtomicReference<>();
    }
}