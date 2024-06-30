package com.wmods.wppenhacer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ListView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.adapter.IGStatusAdapter;
import com.wmods.wppenhacer.views.IGStatusView;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class IGStatus extends Feature {
    public static ArrayList<Object> itens = new ArrayList<>();
    @SuppressLint("StaticFieldLeak")
    private static IGStatusView mStatusContainer;
    private WeakReference<Activity> homeActivity;

    public IGStatus(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("igstatus", false) || Utils.getApplication().getPackageName().equals("com.whatsapp.w4b"))
            return;

        var clazz = XposedHelpers.findClass("com.whatsapp.HomeActivity", classLoader);

        XposedHelpers.findAndHookMethod(clazz.getSuperclass(), "onCreate", android.os.Bundle.class, new XC_MethodHook() {
            @Override
            @SuppressLint("DiscouragedApi")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                homeActivity = new WeakReference<>((Activity) param.thisObject);
                // create status container
                mStatusContainer = new IGStatusView(homeActivity.get());
                var layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Utils.dipToPixels(105));
                layoutParams.gravity = Gravity.TOP;

                if (Objects.equals(prefs.getString("chatfilter", null), "2") && prefs.getBoolean("topnav", false)) {
                    layoutParams.topMargin = Utils.dipToPixels(168);
                } else if (prefs.getBoolean("topnav", false)) {
                    layoutParams.topMargin = Utils.dipToPixels(112);
                } else if (Objects.equals(prefs.getString("chatfilter", null), "2")) {
                    layoutParams.topMargin = Utils.dipToPixels(112);
                } else {
                    layoutParams.topMargin = Utils.dipToPixels(56);
                }

                mStatusContainer.setLayoutParams(layoutParams);
                mStatusContainer.setBackgroundColor(Color.TRANSPARENT);
                var mainContainer = homeActivity.get().findViewById(Utils.getID("main_container", "id"));
                var pagerView = (ViewGroup) mainContainer.findViewById(Utils.getID("pager", "id"));
                var pager_holder = (ViewGroup) pagerView.getParent();
                pager_holder.addView(mStatusContainer);

            }
        });

        // fix scroll
        var onScrollPagerMethod = Unobfuscator.loadScrollPagerMethod(classLoader);

        XposedBridge.hookMethod(onScrollPagerMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var scroll = -(float) XposedHelpers.getIntField(homeActivity.get(), "A02");
                if (mStatusContainer.isShown())
                    mStatusContainer.setTranslationY(scroll);
            }
        });

        var getViewConversationMethod = Unobfuscator.loadGetViewConversationMethod(classLoader);
        XposedBridge.hookMethod(getViewConversationMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (XposedHelpers.findClass("com.whatsapp.conversationslist.ArchivedConversationsFragment", classLoader).isInstance(param.thisObject))
                    return;
                if (XposedHelpers.findClass("com.whatsapp.conversationslist.FolderConversationsFragment", classLoader).isInstance(param.thisObject))
                    return;
                var view = (ViewGroup) param.getResult();
                if (view == null) return;
                var mainView = (ListView) view.findViewById(android.R.id.list);
                mainView.setNestedScrollingEnabled(true);
                var paddingView = new View(homeActivity.get());
                paddingView.setClickable(true);
                var layoutParams = new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Utils.dipToPixels(105));
                paddingView.setLayoutParams(layoutParams);
                mainView.addHeaderView(paddingView);
            }
        });

        // hide on tab

        var onMenuItemSelected = Unobfuscator.loadOnMenuItemSelected(classLoader);
        var separateGroups = prefs.getBoolean("separategroups", false);
        var onMenuItemClick = Unobfuscator.loadOnMenuItemClickClass(classLoader);
        var onMenuItemClick2 = Unobfuscator.loadOnMenuItemClickClass2(classLoader);

        XposedBridge.hookMethod(onMenuItemSelected, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!Unobfuscator.isCalledFromClass(clazz) && !Unobfuscator.isCalledFromClass(onMenuItemClick2) && !Unobfuscator.isCalledFromClass(onMenuItemClick))
                    return;
                var index = (int) param.args[0];
                homeActivity.get().runOnUiThread(() -> {
                    XposedHelpers.setObjectField(homeActivity.get(), "A02", 0);
                    var visible = View.GONE;
                    if (index == SeparateGroup.tabs.indexOf(SeparateGroup.CHATS) || (separateGroups && index == SeparateGroup.tabs.indexOf(SeparateGroup.GROUPS))) {
                        visible = View.VISIBLE;
                    }
                    if (mStatusContainer.getVisibility() != visible)
                        mStatusContainer.setVisibility(visible);
                    if (visible == View.VISIBLE) mStatusContainer.setTranslationY(0);
                });
            }
        });


        var clazz2 = XposedHelpers.findClass("com.whatsapp.updates.viewmodels.UpdatesViewModel", classLoader);
        var onUpdateStatusChanged = Unobfuscator.loadOnUpdateStatusChanged(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onUpdateStatusChanged));
        var statusInfoClass = Unobfuscator.loadStatusInfoClass(classLoader);
        logDebug(statusInfoClass);

        XposedBridge.hookAllConstructors(clazz2, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                itens.add(0, null);
                IGStatusAdapter mStatusAdapter = new IGStatusAdapter(homeActivity.get(), statusInfoClass);
                mStatusContainer.setAdapter(mStatusAdapter);
                mStatusContainer.updateList();
            }
        });

        var onStatusListUpdatesClass = Unobfuscator.loadStatusListUpdatesClass(classLoader);
        logDebug(onStatusListUpdatesClass);

        XposedBridge.hookAllConstructors(onStatusListUpdatesClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var list1 = (List) param.args[2];
                var list2 = (List) param.args[3];
                itens.clear();
                itens.add(0, null);
                itens.addAll(list1);
                itens.addAll(list2);
                mStatusContainer.updateList();
            }
        });


        var onGetInvokeField = Unobfuscator.loadGetInvokeField(classLoader);
        logDebug(Unobfuscator.getFieldDescriptor(onGetInvokeField));
        XposedBridge.hookMethod(onUpdateStatusChanged, new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var object = onGetInvokeField.get(param.args[0]);
                var method = ReflectionUtils.findMethodUsingFilter(object.getClass(), method1 -> method1.getReturnType().equals(Object.class));
                var StatusListUpdates = ReflectionUtils.callMethod(method, object);
                if (StatusListUpdates == null) return;
                var lists = ReflectionUtils.findAllFieldsUsingFilter(StatusListUpdates.getClass(), field -> field.getType().equals(List.class));
                if (lists.length < 3) return;
                var list1 = (List) lists[1].get(StatusListUpdates);
                var list2 = (List) lists[2].get(StatusListUpdates);
                itens.clear();
                itens.add(0, null);
                itens.addAll(list1);
                itens.addAll(list2);
                mStatusContainer.updateList();
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "IGStatus";
    }
}
