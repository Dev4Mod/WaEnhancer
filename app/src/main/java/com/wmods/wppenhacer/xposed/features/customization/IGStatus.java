package com.wmods.wppenhacer.xposed.features.customization;

import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.adapter.IGStatusAdapter;
import com.wmods.wppenhacer.views.IGStatusView;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class IGStatus extends Feature {
    public static ArrayList<Object> itens = new ArrayList<>();
    private static final ArrayList<IGStatusView> mListStatusContainer = new ArrayList<>();

    public IGStatus(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("igstatus", false))
            return;

        try {
            var fabintMethod = Unobfuscator.loadFabMethod(classLoader);

            var getViewConversationMethod = Unobfuscator.loadGetViewConversationMethod(classLoader);
            XposedBridge.hookMethod(getViewConversationMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (XposedHelpers.findClass("com.whatsapp.conversationslist.ArchivedConversationsFragment", classLoader).isInstance(param.thisObject))
                            return;
                        if (XposedHelpers.findClass("com.whatsapp.conversationslist.FolderConversationsFragment", classLoader).isInstance(param.thisObject))
                            return;
                        var view = (ViewGroup) param.getResult();
                        if (view == null) return;
                        var list = (ViewGroup) view.findViewById(android.R.id.list);
                        var mStatusContainer = new IGStatusView(WppCore.getCurrentActivity());
                        if (list instanceof ListView listView) {
                            listView.setNestedScrollingEnabled(true);
                            var layoutParams = new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Utils.dipToPixels(88));
                            mStatusContainer.setLayoutParams(layoutParams);
                            listView.addHeaderView(mStatusContainer);
                        } else {
                            // RecyclerView
                            var paddingTop = list.getPaddingTop();
                            var parentView = (ViewGroup) list.getParent();
                            var background = list.getBackground();
                            mStatusContainer.setBackground(background);
                            list.setPadding(0, 0, 0, 0);
                            var layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Utils.dipToPixels(88));
                            layoutParams.topMargin = paddingTop;
                            mStatusContainer.setLayoutParams(layoutParams);
                            parentView.addView(mStatusContainer, 0);
                        }
                        var id = (int) fabintMethod.invoke(param.thisObject);
                        var igStatus = mListStatusContainer.stream().filter(ig -> ig.getFragmentId() == id).findFirst().orElse(null);
                        if (igStatus != null) {
                            mStatusContainer.setAdapter(igStatus.getAdapter());
                            mListStatusContainer.remove(igStatus);
                        }
                        mStatusContainer.setFragmentId(id);
                        mListStatusContainer.add(mStatusContainer);
                    } catch (Throwable t) {
                        XposedBridge.log("IGStatus: Error in getViewConversationMethod hook: " + t);
                    }
                }
            });

            var clazz2 = Unobfuscator.getClassByName("UpdatesViewModel", classLoader);
            var onUpdateStatusChanged = Unobfuscator.loadOnUpdateStatusChanged(classLoader);
            logDebug(Unobfuscator.getMethodDescriptor(onUpdateStatusChanged));
            var statusInfoClass = Unobfuscator.loadStatusInfoClass(classLoader);
            logDebug(statusInfoClass);

            XposedBridge.hookAllConstructors(clazz2, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        itens.add(0, null);
                        for (var mStatusContainer : mListStatusContainer) {
                            IGStatusAdapter mStatusAdapter = new IGStatusAdapter(WppCore.getCurrentActivity(), statusInfoClass);
                            mStatusContainer.setAdapter(mStatusAdapter);
                            mStatusContainer.updateList();
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("IGStatus: Error in UpdatesViewModel constructor hook: " + t);
                    }
                }
            });

            var onStatusListUpdatesClass = Unobfuscator.loadStatusListUpdatesClass(classLoader);
            logDebug(onStatusListUpdatesClass);

            XposedBridge.hookAllConstructors(onStatusListUpdatesClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        final var lists = Arrays.stream(param.args).filter(v -> v instanceof List<?>).toArray();
                        if (lists.length >= 2) {
                            itens.clear();
                            itens.add(0, null);
                            itens.addAll((List) lists[0]);
                            itens.addAll((List) lists[1]);
                            for (var mStatusContainer : mListStatusContainer)
                                mStatusContainer.updateList();
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("IGStatus: Error in onStatusListUpdatesClass constructor hook: " + t);
                    }
                }
            });


            var onGetInvokeField = Unobfuscator.loadGetInvokeField(classLoader);
            logDebug(Unobfuscator.getFieldDescriptor(onGetInvokeField));
            XposedBridge.hookMethod(onUpdateStatusChanged, new XC_MethodHook() {

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        var object = onGetInvokeField.get(param.args[0]);
                        if (object == null) return;
                        var method = ReflectionUtils.findMethodUsingFilter(object.getClass(), method1 -> method1.getReturnType().equals(Object.class));
                        var StatusListUpdates = ReflectionUtils.callMethod(method, object);
                        if (StatusListUpdates == null) return;
                        var lists = ReflectionUtils.findAllFieldsUsingFilter(StatusListUpdates.getClass(), field -> field.getType().equals(List.class));
                        if (lists.length < 3) return;
                        var list1 = (List) lists[1].get(StatusListUpdates);
                        var list2 = (List) lists[2].get(StatusListUpdates);
                        itens.clear();
                        itens.add(0, null);
                        if (list1 != null) itens.addAll(list1);
                        if (list2 != null) itens.addAll(list2);
                        for (var mStatusContainer : mListStatusContainer)
                            mStatusContainer.updateList();
                    } catch (Throwable t) {
                        XposedBridge.log("IGStatus: Error in onUpdateStatusChanged hook: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("IGStatus: Error initializing hooks: " + t);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "IGStatus";
    }
}
