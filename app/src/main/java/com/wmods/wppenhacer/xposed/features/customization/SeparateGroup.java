package com.wmods.wppenhacer.xposed.features.customization;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import android.annotation.SuppressLint;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.UnobfuscatorCache;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SeparateGroup extends Feature {

    public static final int CHATS = 200;
    public static final int STATUS = 300;
    public static final int CALLS = 400;
    public static final int COMMUNITY = 600;
    public static final int GROUPS = 500;
    public static ArrayList<Integer> tabs = new ArrayList<>();
    public static HashMap<Integer, Object> tabInstances = new HashMap<>();

    public SeparateGroup(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {

        var cFrag = XposedHelpers.findClass("com.whatsapp.conversationslist.ConversationsFragment", classLoader);
        var home = XposedHelpers.findClass("com.whatsapp.HomeActivity", classLoader);

        // Modifying tab list order
        hookTabList(home);

        if (!prefs.getBoolean("separategroups", false)) return;
        // Setting group icon
        hookTabIcon();
        // Setting up fragments
        hookTabInstance(cFrag);
        // Setting group tab name
        hookTabName(home);
        // Setting tab count
        hookTabCount();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Chats Filter";
    }

    private void hookTabCount() throws Exception {

        var runMethod = Unobfuscator.loadTabCountMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(runMethod));

        var enableCountMethod = Unobfuscator.loadEnableCountTabMethod(classLoader);
        var constructor1 = Unobfuscator.loadEnableCountTabConstructor1(classLoader);
        var constructor2 = Unobfuscator.loadEnableCountTabConstructor2(classLoader);
        var constructor3 = Unobfuscator.loadEnableCountTabConstructor3(classLoader);
        constructor3.setAccessible(true);

        logDebug(Unobfuscator.getMethodDescriptor(enableCountMethod));
        XposedBridge.hookMethod(enableCountMethod, new XC_MethodHook() {
            @Override
            @SuppressLint({"Range","Recycle"})
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var indexTab = (int) param.args[2];
                if (indexTab == tabs.indexOf(CHATS)) {

                    var chatCount = 0;
                    var groupCount = 0;
                    synchronized (SeparateGroup.class) {
                        var db = MessageStore.database.getReadableDatabase();
                        var sql = "SELECT * FROM chat WHERE unseen_message_count != 0";
                        var cursor = db.rawQuery(sql, null);
                        while (cursor.moveToNext()) {
                            int jid = cursor.getInt(cursor.getColumnIndex("jid_row_id"));
                            int groupType = cursor.getInt(cursor.getColumnIndex("group_type"));
                            int archived = cursor.getInt(cursor.getColumnIndex("archived"));
                            if (archived != 0 || groupType != 0) continue;
                            var sql2 = "SELECT * FROM jid WHERE _id == ?";
                            var cursor1 = db.rawQuery(sql2, new String[]{String.valueOf(jid)});
                            if (!cursor1.moveToFirst()) continue;
                            var server = cursor1.getString(cursor1.getColumnIndex("server"));
                            if (server.equals("g.us")) {
                                groupCount++;
                            } else {
                                chatCount++;
                            }
                        }
                }
                if (tabs.contains(CHATS) && tabInstances.containsKey(CHATS)) {
                    var instance12 = chatCount <= 0 ? constructor3.newInstance() : constructor2.newInstance(chatCount);
                    var instance22 = constructor1.newInstance(instance12);
                    param.args[1] = instance22;
                }
                if (tabs.contains(GROUPS) && tabInstances.containsKey(GROUPS)) {
                    var instance2 = groupCount <= 0 ? constructor3.newInstance() : constructor2.newInstance(groupCount);
                    var instance1 = constructor1.newInstance(instance2);
                    enableCountMethod.invoke(param.thisObject, param.args[0], instance1, tabs.indexOf(GROUPS));
                }
            }
        }
    });
}

    private void hookTabIcon() throws Exception {
        var iconTabMethod = Unobfuscator.loadIconTabMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(iconTabMethod));
        var iconField = Unobfuscator.loadIconTabField(classLoader);
        var iconFrameField = Unobfuscator.loadIconTabLayoutField(classLoader);
        var iconMenuField = Unobfuscator.loadIconMenuField(classLoader);

        XposedBridge.hookMethod(iconTabMethod, new XC_MethodHook() {
            @SuppressLint("ResourceType")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var superClass = param.thisObject.getClass().getSuperclass();
                if (superClass != null && superClass == iconTabMethod.getDeclaringClass()) {
                    var field1 = superClass.getDeclaredField(iconField.getName()).get(param.thisObject);
                    var field2 = getObjectField(field1, iconFrameField.getName());
                    if (field2 == null) return;
                    var menu = (Menu) getObjectField(field2, iconMenuField.getName());
                    if (menu == null) return;
                    // add Icon to menu
                    var menuItem = (MenuItem) menu.findItem(GROUPS);
                    if (menuItem != null) {
                        menuItem.setIcon(Utils.getID("home_tab_communities_selector", "drawable"));
                    }
                }
            }
        });
    }

    @SuppressLint("ResourceType")
    private void hookTabName(Class<?> home) throws Exception {
        var tabNameMethod = Unobfuscator.loadTabNameMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(tabNameMethod));
        XposedBridge.hookMethod(tabNameMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                var tab = (int) param.args[0];
                if (tab == GROUPS) {
                    param.setResult(UnobfuscatorCache.getInstance().getString("groups"));
                }
            }
        });
    }

    private void hookTabInstance(Class<?> cFrag) throws Exception {
        var getTabMethod = Unobfuscator.loadGetTabMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(getTabMethod));

        var methodTabInstance = Unobfuscator.loadTabFragmentMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(methodTabInstance));

        var recreateFragmentMethod = Unobfuscator.loadRecreateFragmentConstructor(classLoader);

        XposedBridge.hookMethod(recreateFragmentMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var object = param.args[2];
                var desc = XposedHelpers.getObjectField(object, "A06");
                if (desc == null) return;
                var split = desc.toString().split(":");
                var id = 0;
                try {
                    id = Integer.parseInt(split[split.length - 1]);
                } catch (Exception ignored) {
                    return;
                }
                if (id == GROUPS || id == CHATS) {
                    var convFragment = XposedHelpers.getObjectField(param.thisObject, "A02");
                    tabInstances.remove(id);
                    tabInstances.put(id, convFragment);
                }
            }
        });

        XposedBridge.hookMethod(getTabMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var tabId = ((Number) tabs.get((int) param.args[0])).intValue();
                if (tabId == GROUPS || tabId == CHATS) {
                    var convFragment = cFrag.newInstance();
                    param.setResult(convFragment);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var tabId = ((Number) tabs.get((int) param.args[0])).intValue();
                tabInstances.remove(tabId);
                tabInstances.put(tabId, param.getResult());
            }
        });

        XposedBridge.hookMethod(methodTabInstance, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var chatsList = (List) param.getResult();
                var resultList = filterChat(param.thisObject, chatsList);
                param.setResult(resultList);
            }
        });

        var fabintMethod = Unobfuscator.loadFabMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(fabintMethod));

        XposedBridge.hookMethod(fabintMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (Objects.equals(tabInstances.get(GROUPS), param.thisObject)) {
                    param.setResult(GROUPS);
                }
            }
        });

        var publishResultsMethod = Unobfuscator.loadGetFiltersMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(publishResultsMethod));

        XposedBridge.hookMethod(publishResultsMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var filters = param.args[1];
                var chatsList = (List) XposedHelpers.getObjectField(filters, "values");
                var baseField = Unobfuscator.getFieldByExtendType(publishResultsMethod.getDeclaringClass(), BaseAdapter.class);
                if (baseField == null) return;
                var convField = Unobfuscator.getFieldByType(baseField.getType(), cFrag);
                Object thiz = convField.get(baseField.get(param.thisObject));
                if (thiz == null) return;
                var resultList = filterChat(thiz, chatsList);
                XposedHelpers.setObjectField(filters, "values", resultList);
                XposedHelpers.setIntField(filters, "count", resultList.size());
            }
        });
    }

    private List filterChat(Object thiz, List chatsList) {
        var tabChat = tabInstances.get(CHATS);
        var tabGroup = tabInstances.get(GROUPS);
        if (!Objects.equals(tabChat, thiz) && !Objects.equals(tabGroup, thiz)) {
            return chatsList;
        }
        var editableChatList = new ArrayListFilter(Objects.equals(tabGroup, thiz));
        editableChatList.addAll(chatsList);
        return editableChatList;
    }

    private void hookTabList(@NonNull Class<?> home) throws Exception {
        var onCreateTabList = Unobfuscator.loadTabListMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onCreateTabList));
        var fieldTabsList = Arrays.stream(home.getDeclaredFields()).filter(f -> f.getType().equals(List.class)).findFirst().orElse(null);
        if (fieldTabsList == null) {
            throw new NullPointerException("fieldTabList is NULL!");
        }
        fieldTabsList.setAccessible(true);

        XposedBridge.hookMethod(onCreateTabList, new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                tabs = (ArrayList<Integer>) fieldTabsList.get(null);
                if (tabs == null) return;
                if (!prefs.getBoolean("separategroups", false)) return;
                if (!tabs.contains(GROUPS)) {
                    tabs.add(tabs.isEmpty() ? 0 : 1, GROUPS);
                }
            }
        });
    }


public static class ArrayListFilter extends ArrayList {

    private final boolean isGroup;

    public ArrayListFilter(boolean isGroup) {
        this.isGroup = isGroup;
    }


    @Override
    public void add(int index, Object element) {
        if (checkGroup(element)) {
            super.add(index, element);
        }
    }

    @Override
    public boolean add(Object object) {
        if (checkGroup(object)) {
            return super.add(object);
        }
        return true;
    }

    @Override
    public boolean addAll(@NonNull Collection c) {
        for (var chat : c) {
            if (checkGroup(chat)) {
                super.add(chat);
            }
        }
        return true;
    }

    private boolean checkGroup(Object chat) {
        var requiredServer = isGroup ? "g.us" : "s.whatsapp.net";
        var jid = getObjectField(chat, "A00");
        if (XposedHelpers.findMethodExactIfExists(jid.getClass(), "getServer") != null) {
            var server = (String) callMethod(jid, "getServer");
            return server.equals(requiredServer);
        }
        return true;
    }
}

}
