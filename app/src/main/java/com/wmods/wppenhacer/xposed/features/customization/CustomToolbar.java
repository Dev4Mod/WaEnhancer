package com.wmods.wppenhacer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.listeners.OnMultiClickListener;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.general.Others;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CustomToolbar extends Feature {

    private String mDateExpiration;

    public CustomToolbar(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        var showName = prefs.getBoolean("shownamehome", false);
        var showBio = prefs.getBoolean("showbiohome", false);
        var typeArchive = prefs.getString("typearchive", "0");
        var methodHook = new MethodHook(showName, showBio, typeArchive);
        XposedHelpers.findAndHookMethod("com.whatsapp.HomeActivity", classLoader, "onCreate", Bundle.class, methodHook);
        Others.propsBoolean.put(6481, false);
        Others.propsBoolean.put(5353, true);
        Others.propsBoolean.put(8735, false);
        expirationAboutInfo();
        if (showName || showBio) {
            disableNewLogo();
        }
    }

    private void disableNewLogo() throws Exception {
        var changeLogoMethod = Unobfuscator.loadChangeTitleLogoMethod(classLoader);
        var changeLogoField = Unobfuscator.loadChangeTitleLogoField(classLoader);

        XposedBridge.hookMethod(changeLogoMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var idx = ReflectionUtils.findIndexOfType(param.args, Activity.class);
                var homeActivity = (Activity) param.args[idx];
                changeLogoField.setAccessible(true);
                changeLogoField.set(homeActivity, Integer.valueOf(2));
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var idx = ReflectionUtils.findIndexOfType(param.args, Activity.class);
                var homeActivity = (Activity) param.args[idx];
                var toolbar = (ViewGroup) homeActivity.findViewById(Utils.getID("toolbar", "id"));
                var logo = toolbar.findViewById(Utils.getID("toolbar_logo_text", "id"));
                if (logo != null) {
                    logo.setVisibility(View.GONE);
                }
            }
        });
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Show Name and Bio";
    }

    public void expirationAboutInfo() throws Exception {

        var expirationClass = Unobfuscator.loadExpirationClass(classLoader);
        XposedBridge.hookAllConstructors(expirationClass, new XC_MethodHook() {
            @SuppressLint("SetTextI18n")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var method = ReflectionUtils.findMethodUsingFilter(param.thisObject.getClass(), m -> m.getReturnType().equals(Date.class));
                var date = (Date) method.invoke(param.thisObject);
                mDateExpiration = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Objects.requireNonNull(date));

            }
        });

        XposedHelpers.findAndHookMethod("com.whatsapp.settings.About", classLoader, "onCreate", classLoader.loadClass("android.os.Bundle"),
                new XC_MethodHook() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        var activity = (Activity) param.thisObject;
                        var viewRoot = activity.getWindow().getDecorView();
                        var version = (TextView) viewRoot.findViewById(Utils.getID("version", "id"));
                        if (version != null) {
                            version.setText(version.getText() + " " + activity.getString(ResId.string.expiration, mDateExpiration));
                        }
                    }
                });
    }


    public static class MethodHook extends XC_MethodHook {
        private final boolean showName;
        private final boolean showBio;
        private final String typeArchive;


        public MethodHook(boolean showName, boolean showBio, String typeArchive) {
            this.showName = showName;
            this.showBio = showBio;
            this.typeArchive = typeArchive;
        }


        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            var homeActivity = (Activity) param.thisObject;
            var actionbar = XposedHelpers.callMethod(homeActivity, "getSupportActionBar");
            var toolbar = (ViewGroup) homeActivity.findViewById(Utils.getID("toolbar", "id"));
            var logo = toolbar.findViewById(Utils.getID("toolbar_logo", "id"));
            var name = WppCore.getMyName();
            var bio = WppCore.getMyBio();
            var nameWa = homeActivity.getPackageManager().getApplicationLabel(homeActivity.getApplicationInfo()).toString();


            if (typeArchive.equals("1")) {
                var onMultiClickListener = new OnMultiClickListener(5, 500) {

                    @Override
                    public void onMultiClick(View v) {
                        Intent intent = new Intent();
                        intent.setClassName(Utils.getApplication().getPackageName(), "com.whatsapp.conversationslist.ArchivedConversationsActivity");
                        homeActivity.startActivity(intent);
                    }
                };
                toolbar.setOnClickListener(onMultiClickListener);
            } else if (typeArchive.equals("2")) {
                toolbar.setOnLongClickListener(v -> {
                    Intent intent = new Intent();
                    intent.setClassName(Utils.getApplication().getPackageName(), "com.whatsapp.conversationslist.ArchivedConversationsActivity");
                    homeActivity.startActivity(intent);
                    return true;
                });
            }

            if (!showBio && !showName) return;

            var methods = Arrays.stream(actionbar.getClass().getDeclaredMethods()).filter(m -> m.getParameterCount() == 1 && m.getParameterTypes()[0] == CharSequence.class).toArray(Method[]::new);

            methods[1].invoke(actionbar, showName ? name : nameWa);

            if (showBio) {
                methods[0].invoke(actionbar, bio);
            }

            XposedBridge.hookMethod(methods[1], new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    var name = WppCore.getMyName();
                    var bio = WppCore.getMyBio();
                    if (showBio && (param.args[0] == "" || param.args[0] == nameWa)) {
                        ReflectionUtils.callMethod(methods[0], param.thisObject, bio);
                    } else {
                        ReflectionUtils.callMethod(methods[0], param.thisObject, "");
                    }
                    if (param.args[0] == "" || param.args[0] == nameWa) {
                        param.args[0] = showName ? name : nameWa;
                    }

                    var layoutParams = logo.getLayoutParams();
                    layoutParams.width = 1;
                    layoutParams.height = 1;
                    logo.setLayoutParams(layoutParams);

                }
            });
        }
    }
}
