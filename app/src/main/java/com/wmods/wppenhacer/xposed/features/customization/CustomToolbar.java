package com.wmods.wppenhacer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.listeners.OnMultiClickListener;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.general.Others;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CustomToolbar extends Feature {

    private static final String TYPE_ARCHIVE_MULTI_CLICK = "1";
    private static final String TYPE_ARCHIVE_LONG_CLICK = "2";
    private static final int MULTI_CLICK_COUNT = 5;
    private static final int MULTI_CLICK_INTERVAL = 700;
    private static final float TITLE_TEXT_SIZE = 20f;
    private static final float SUBTITLE_TEXT_SIZE = 12f;

    private String mDateExpiration;
    private static Method onMenuItemSelected;

    public CustomToolbar(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        var showName = prefs.getBoolean("shownamehome", false);
        var showBio = prefs.getBoolean("showbiohome", false);
        var typeArchive = prefs.getString("typearchive", "0");

        onMenuItemSelected = Unobfuscator.loadOnMenuItemSelected(classLoader);

        var methodHook = new ToolbarMethodHook(showName, showBio, typeArchive);
        XposedHelpers.findAndHookMethod(
                WppCore.getHomeActivityClass(classLoader),
                "onCreate",
                Bundle.class,
                methodHook
        );

        hookExpirationInfo();
        Others.propsBoolean.put(6481, false);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Show Name and Bio";
    }

    private void hookExpirationInfo() throws Exception {
        hookExpirationDate();
        hookAboutActivity();
    }

    private void hookExpirationDate() throws Exception {
        var expirationClass = Unobfuscator.loadExpirationClass(classLoader);

        XposedBridge.hookAllConstructors(expirationClass, new XC_MethodHook() {
            @SuppressLint("SetTextI18n")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var method = ReflectionUtils.findMethodUsingFilter(
                        param.thisObject.getClass(),
                        m -> m.getReturnType().equals(Date.class)
                );
                var date = (Date) method.invoke(param.thisObject);
                mDateExpiration = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        .format(Objects.requireNonNull(date));
            }
        });
    }

    private void hookAboutActivity() throws Exception {
        XposedHelpers.findAndHookMethod(
                WppCore.getAboutActivityClass(classLoader),
                "onCreate",
                classLoader.loadClass("android.os.Bundle"),
                new XC_MethodHook() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        var activity = (Activity) param.thisObject;
                        var viewRoot = activity.getWindow().getDecorView();
                        var version = (TextView) viewRoot.findViewById(Utils.getID("version", "id"));

                        if (version != null) {
                            var expirationText = activity.getString(ResId.string.expiration, mDateExpiration);
                            version.setText(version.getText() + " " + expirationText);
                        }
                    }
                }
        );
    }

    private static class ToolbarMethodHook extends XC_MethodHook {
        
        private final boolean showName;
        private final boolean showBio;
        private final String typeArchive;

        ToolbarMethodHook(boolean showName, boolean showBio, String typeArchive) {
            this.showName = showName;
            this.showBio = showBio;
            this.typeArchive = typeArchive;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            var homeActivity = (Activity) param.thisObject;
            ViewGroup toolbar = homeActivity.findViewById(Utils.getID("toolbar", "id"));
            var logo = toolbar.findViewById(Utils.getID("toolbar_logo", "id"));

            var tabInstance = getTabInstance(homeActivity);
            var archiveIntent = createArchiveIntent(homeActivity);

            setupArchiveListener(toolbar, homeActivity, archiveIntent);

            if (!showBio && !showName) return;

            var toolbarLayout = createToolbarLayout(homeActivity, toolbar);
            createTitleView(homeActivity, toolbarLayout);

            if (showBio) {
                createSubtitleView(homeActivity, toolbarLayout);
            }

            hideOriginalLogo(homeActivity, logo);
            setupTabVisibilityHook(tabInstance, toolbarLayout);
        }

        private Object getTabInstance(Activity homeActivity) throws Exception {
            var clazz = WppCore.getTabsPagerClass(homeActivity.getClassLoader());
            var fieldTab = ReflectionUtils.getFieldByType(homeActivity.getClass(), clazz);
            return fieldTab.get(homeActivity);
        }

        private Intent createArchiveIntent(Activity homeActivity) throws Exception {
            var archivedClass = Unobfuscator.findFirstClassUsingName(
                    homeActivity.getClassLoader(),
                    StringMatchType.EndsWith,
                    "ArchivedConversationsActivity"
            );
            Intent intent = new Intent();
            intent.setClassName(Utils.getApplication().getPackageName(), archivedClass.getName());
            return intent;
        }

        private void setupArchiveListener(ViewGroup toolbar, Activity homeActivity, Intent intent) {
            if (TYPE_ARCHIVE_MULTI_CLICK.equals(typeArchive)) {
                setupMultiClickListener(toolbar, homeActivity, intent);
            } else if (TYPE_ARCHIVE_LONG_CLICK.equals(typeArchive)) {
                setupLongClickListener(toolbar, homeActivity, intent);
            }
        }

        private void setupMultiClickListener(ViewGroup toolbar, Activity homeActivity, Intent intent) {
            var listener = new OnMultiClickListener(MULTI_CLICK_COUNT, MULTI_CLICK_INTERVAL) {
                @Override
                public void onMultiClick(View v) {
                    homeActivity.startActivity(intent);
                }
            };
            toolbar.setOnClickListener(listener);
        }

        private void setupLongClickListener(ViewGroup toolbar, Activity homeActivity, Intent intent) {
            toolbar.setOnLongClickListener(v -> {
                homeActivity.startActivity(intent);
                return true;
            });
        }

        private LinearLayout createToolbarLayout(Activity homeActivity, ViewGroup toolbar) {
            LinearLayout linearLayout = new LinearLayout(homeActivity);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            toolbar.addView(linearLayout, 0);
            return linearLayout;
        }

        private void createTitleView(Activity homeActivity, LinearLayout parent) {
            var name = WppCore.getMyName();
            var titleText = showName ? name : "WhatsApp";
            
            var mTitle = new TextView(homeActivity);
            mTitle.setText(titleText);
            mTitle.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
            ));
            mTitle.setTextSize(TITLE_TEXT_SIZE);
            mTitle.setTextColor(DesignUtils.getPrimaryTextColor());

            if (!showBio) {
                mTitle.setGravity(Gravity.CENTER);
            }

            parent.addView(mTitle);
        }

        private void createSubtitleView(Activity homeActivity, LinearLayout parent) {
            var bio = WppCore.getMyBio();

            TextView mSubtitle = new TextView(homeActivity);
            mSubtitle.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            ));
            mSubtitle.setText(bio);
            mSubtitle.setTextSize(SUBTITLE_TEXT_SIZE);
            mSubtitle.setTextColor(DesignUtils.getPrimaryTextColor());
            mSubtitle.setMarqueeRepeatLimit(-1);
            mSubtitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            mSubtitle.setSingleLine();
            mSubtitle.setSelected(true);

            parent.addView(mSubtitle);
        }

        private void hideOriginalLogo(Activity homeActivity, View logo) {
            var parent = (ViewGroup) logo.getParent();
            var window = (ViewGroup) homeActivity.getWindow().getDecorView();
            
            parent.removeView(logo);

            RelativeLayout hideLayout = new RelativeLayout(homeActivity);
            hideLayout.setLayoutParams(new LinearLayout.LayoutParams(2, 2));
            hideLayout.setVisibility(View.GONE);

            window.addView(hideLayout);
            hideLayout.addView(logo);
        }

        private void setupTabVisibilityHook(Object tabInstance, LinearLayout toolbarLayout) {
            XposedBridge.hookMethod(onMenuItemSelected, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (tabInstance != param.thisObject) return;

                    var currentIndex = (int) param.args[0];
                    var visibility = currentIndex == 0 ? View.VISIBLE : View.GONE;
                    toolbarLayout.setVisibility(visibility);
                }
            });
        }
    }
}
