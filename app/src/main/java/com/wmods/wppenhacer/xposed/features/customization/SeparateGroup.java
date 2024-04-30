package com.wmods.wppenhacer.xposed.features.customization;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

import android.annotation.SuppressLint;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.UnobfuscatorCache;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SeparateGroup extends Feature {

    public final int CHATS = 200;
    public final int STATUS = 300;
    public final int CALLS = 400;
    public final int COMMUNITY = 600;
    public final int GROUPS = 500;
    public ArrayList<Integer> tabs = new ArrayList<>();
    public static HashMap<Integer, Object> tabInstances = new HashMap<>();

    public SeparateGroup(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {

        var cFrag = XposedHelpers.findClass("com.whatsapp.conversationslist.ConversationsFragment", loader);
        var home = XposedHelpers.findClass("com.whatsapp.HomeActivity", loader);

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

        var runMethod = Unobfuscator.loadTabCountMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(runMethod));
        var idField = Unobfuscator.getFieldByType(runMethod.getDeclaringClass(), int.class);
        var pagerField = Unobfuscator.loadTabCountField(loader);

        XposedBridge.hookMethod(runMethod, new XC_MethodHook() {
            @Override
            @SuppressLint({"Recycle", "Range"})
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var id = (int) getObjectField(param.thisObject, idField.getName());
                if (id != 32 && id != 35 && id != 37) return;

                var homeActivity = XposedHelpers.getObjectField(param.thisObject, "A00");
                var a1 = XposedHelpers.getObjectField(homeActivity, pagerField.getName());
                var chatCount = 0;
                var groupCount = 0;
                // Fiz ele pegar direto da database, esse metodo que dei hook, e chamado sempre q vc muda de tab, entra/sai de um chat ->
                // ou quando a lista e atualizada, ent ele sempre vai atualizar
                var db = MessageStore.database.getReadableDatabase();
                // essa coluna que eu peguei, mostra a quantidade de mensagens n lidas (obvio ne).
                // nao coloquei apenas > 0 pq quando vc marca um chat como nao lido, esse valor fica -1
                // entao pra contar direitinho deixei != 0
                var sql = "SELECT * FROM chat WHERE unseen_message_count != 0";
                var cursor = db.rawQuery(sql, null);
                while (cursor.moveToNext()) {
                    // row da jid do chat
                    int jid = cursor.getInt(cursor.getColumnIndex("jid_row_id"));
                    int groupType = cursor.getInt(cursor.getColumnIndex("group_type"));
                    // verifica se esta arquivado ou n
                    int archived = cursor.getInt(cursor.getColumnIndex("archived"));
                    // se estiver arquivado, pula
                    if (archived != 0 || groupType != 0) continue;

                    // aqui eu fiz pra verificar se e grupo ou n, ai ele pega as infos da jid de acordo com a row da jid ali de cima
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
                if (tabs.contains(CHATS)) {
                    var q = XposedHelpers.callMethod(a1, "A00", a1, tabs.indexOf(CHATS));
                    setObjectField(q, "A01", chatCount);
                }
                if (tabs.contains(GROUPS)) {
                    setObjectField(tabInstances.get(GROUPS), "A01", groupCount);
                }
            }
        });

        var enableCountMethod = Unobfuscator.loadEnableCountTabMethod(loader);
        var constructor1 = Unobfuscator.loadEnableCountTabConstructor1(loader);
        var constructor2 = Unobfuscator.loadEnableCountTabConstructor2(loader);
        var constructor3 = Unobfuscator.loadEnableCountTabConstructor3(loader);
        constructor3.setAccessible(true);

        logDebug(Unobfuscator.getMethodDescriptor(enableCountMethod));
        XposedBridge.hookMethod(enableCountMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var indexTab = (int) param.args[2];
                if (indexTab == tabs.indexOf(CHATS)) {
                    var groupCount = XposedHelpers.getIntField(tabInstances.get(GROUPS), "A01");
                    var instance2 = groupCount <= 0 ? constructor3.newInstance() : constructor2.newInstance(groupCount);
                    var instance1 = constructor1.newInstance(instance2);
                    enableCountMethod.invoke(param.thisObject, param.args[0], instance1, tabs.indexOf(GROUPS));
                }
            }
        });
    }

    private void hookTabIcon() throws Exception {
        var iconTabMethod = Unobfuscator.loadIconTabMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(iconTabMethod));
        var iconField = Unobfuscator.loadIconTabField(loader);
        var iconFrameField = Unobfuscator.loadIconTabLayoutField(loader);
        var iconMenuField = Unobfuscator.loadIconMenuField(loader);

        XposedBridge.hookMethod(iconTabMethod, new XC_MethodHook() {
            @SuppressLint("ResourceType")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var superClass = param.thisObject.getClass().getSuperclass();
                if (superClass != null && superClass == iconTabMethod.getDeclaringClass()) {
                    var field1 = superClass.getDeclaredField(iconField.getName()).get(param.thisObject);
                    var field2 = getObjectField(field1, iconFrameField.getName());
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
        var tabNameMethod = Unobfuscator.loadTabNameMethod(loader);
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
        var getTabMethod = Unobfuscator.loadGetTabMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(getTabMethod));

        var methodTabInstance = Unobfuscator.loadTabFragmentMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(methodTabInstance));

        var recreateFragmentMethod = Unobfuscator.loadRecreateFragmentConstructor(loader);

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

        var fabintMethod = Unobfuscator.loadFabMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(fabintMethod));

        XposedBridge.hookMethod(fabintMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (Objects.equals(tabInstances.get(GROUPS), param.thisObject)) {
                    param.setResult(GROUPS);
                }
            }
        });

        var publishResultsMethod = Unobfuscator.loadGetFiltersMethod(loader);
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

    private void hookTabList(Class<?> home) throws Exception {
        var onCreateTabList = Unobfuscator.loadTabListMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(onCreateTabList));
        var fieldTabsList = Arrays.stream(home.getDeclaredFields()).filter(f -> f.getType().equals(List.class)).findFirst().orElse(null);
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

        var hidetabs = prefs.getStringSet("hidetabs", null);
        if (hidetabs == null || hidetabs.isEmpty())
            return;

        var hideTabsList = new ArrayList<>(hidetabs);

        var OnTabItemAddMethod = Unobfuscator.loadOnTabItemAddMethod(loader);

        XposedBridge.hookMethod(OnTabItemAddMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (MenuItem) param.getResult();
                var menuItemId = menu.getItemId();
                if (hideTabsList.contains(String.valueOf(menuItemId))) {
                    menu.setVisible(false);
                }
            }
        });

        var loadTabFrameClass = Unobfuscator.loadTabFrameClass(loader);
        logDebug(loadTabFrameClass);

        XposedBridge.hookAllMethods(FrameLayout.class, "onMeasure", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!loadTabFrameClass.isInstance(param.thisObject)) return;
                for (var item : hideTabsList) {
                    View view;
                    if ((view = ((View) param.thisObject).findViewById(Integer.parseInt(item))) != null) {
                        view.setVisibility(View.GONE);
                    }
                }
            }
        });

        var onMenuItemSelected = Unobfuscator.loadOnMenuItemSelected(loader);
        var onMenuItemClick = Unobfuscator.loadOnMenuItemClickClass(loader);

        XposedBridge.hookMethod(onMenuItemSelected, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!Unobfuscator.isCalledFromClass(home) && !Unobfuscator.isCalledFromClass(onMenuItemClick))
                    return;
                var index = (int) param.args[0];
                param.args[0] = getNewTabIndex(hideTabsList, index);
            }
        });
    }

    public int getNewTabIndex(List hidetabs, int index) {
        var tabIsHidden = hidetabs.contains(String.valueOf(tabs.get(index)));
        if (!tabIsHidden) return index;
        var idAtual = XposedHelpers.getIntField(WppCore.getMainActivity(), "A03");
        var indexAtual = tabs.indexOf(idAtual);
        var newIndex = index > indexAtual ? index + 1 : index - 1;
        if (newIndex < 0) return 0;
        if (newIndex >= tabs.size()) return indexAtual;
        return getNewTabIndex(hidetabs, newIndex);
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
