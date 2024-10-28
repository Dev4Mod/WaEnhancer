package com.wmods.wppenhacer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.listeners.OnMultiClickListener;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
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
        expirationAboutInfo();
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
            var toolbar = homeActivity.findViewById(Utils.getID("toolbar", "id"));
            var logo = toolbar.findViewById(Utils.getID("toolbar_logo", "id"));
            var name = WppCore.getMyName();
            var bio = WppCore.getMyBio();


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

            if (!(logo.getParent() instanceof LinearLayout parent)) {
                var methods = Arrays.stream(actionbar.getClass().getDeclaredMethods()).filter(m -> m.getParameterCount() == 1 && m.getParameterTypes()[0] == CharSequence.class).toArray(Method[]::new);

                if (showName) {
                    methods[1].invoke(actionbar, name);
                }

                if (showBio) {
                    methods[0].invoke(actionbar, bio);
                }
                XposedBridge.hookMethod(methods[1], new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (showName) {
                            param.args[0] = name;
                        }
                    }
                });
                return;
            }
            var mTitle = new TextView(homeActivity);
            mTitle.setText(showName ? name : "WhatsApp");
            mTitle.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            mTitle.setTextSize(20f);
            mTitle.setTextColor(DesignUtils.getPrimaryTextColor());
            parent.addView(mTitle);
            if (showBio) {
                TextView mSubtitle = new TextView(homeActivity);
                mSubtitle.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
                mSubtitle.setText(bio);
                mSubtitle.setTextSize(12f);
                mSubtitle.setTextColor(DesignUtils.getPrimaryTextColor());
                mSubtitle.setMarqueeRepeatLimit(-1);
                mSubtitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                mSubtitle.setSingleLine();
                mSubtitle.setSelected(true);
                parent.addView(mSubtitle);
            } else {
                mTitle.setGravity(Gravity.CENTER);
            }
            parent.removeView(logo);
        }
    }
}
