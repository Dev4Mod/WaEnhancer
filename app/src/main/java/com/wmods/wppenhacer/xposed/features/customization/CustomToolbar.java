package com.wmods.wppenhacer.xposed.features.customization;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.DesignUtils;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.features.privacy.HideArchive;

import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CustomToolbar extends Feature {
    public CustomToolbar(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() {
        var showName = prefs.getBoolean("shownamehome", false);
        var showBio = prefs.getBoolean("showbiohome", false);
        var methodHook = new MethodHook(showName, showBio);
        XposedHelpers.findAndHookMethod("com.whatsapp.HomeActivity", classLoader, "onCreate", Bundle.class, methodHook);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Show Name and Bio";
    }

    public static class MethodHook extends XC_MethodHook {
        private final boolean showName;
        private final boolean showBio;

        public MethodHook(boolean showName, boolean showBio) {
            this.showName = showName;
            this.showBio = showBio;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            var homeActivity = (Activity) param.thisObject;
            var actionbar = XposedHelpers.callMethod(homeActivity, "getSupportActionBar");
            var toolbar = homeActivity.findViewById(Utils.getID("toolbar", "id"));
            var logo = toolbar.findViewById(Utils.getID("toolbar_logo", "id"));
            var name = WppCore.getMyName();
            var bio = WppCore.getMyBio();

            toolbar.setOnLongClickListener((v) -> {
                for (var onClick : HideArchive.mClickListenerList) {
                    onClick.onClick(v);
                    return true;
                }
                return false;
            });

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
                var mSubtitle = new TextView(homeActivity);
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
