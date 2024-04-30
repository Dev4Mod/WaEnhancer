package com.wmods.wppenhacer.xposed.features.general;

import static de.robv.android.xposed.XposedHelpers.findClass;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.DesignUtils;
import com.wmods.wppenhacer.xposed.core.ResId;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.Feature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Others extends Feature {

    public static HashMap<Integer, Boolean> props = new HashMap<>();

    public Others(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {


        // Removido pois as não há necessidade de ficar em uma versão obsoleta.

//        var deprecatedMethod = Unobfuscator.loadDeprecatedMethod(loader);
//        logDebug(Unobfuscator.getMethodDescriptor(deprecatedMethod));
//
//        XposedBridge.hookMethod(deprecatedMethod, new XC_MethodHook() {
//            @Override
//            protected void beforeHookedMethod(MethodHookParam param) {
//                Date date = new Date(10554803081056L);
//                param.setResult(date);
//            }
//        });
        var novoTema = prefs.getBoolean("novotema", false);
        var menuWIcons = prefs.getBoolean("menuwicon", false);
        var newSettings = prefs.getBoolean("novaconfig", false);
        var filterChats = prefs.getString("chatfilter", null);
        var strokeButtons = prefs.getBoolean("strokebuttons", false);
        var outlinedIcons = prefs.getBoolean("outlinedicons", false);
        var showDnd = prefs.getBoolean("show_dndmode", false);
        var removechannelRec = prefs.getBoolean("removechannel_rec", false);
        var separateGroups = prefs.getBoolean("separategroups", false);

        props.put(5171, true); // filtros de chat e grupos
        props.put(4524, novoTema);
        props.put(4497, menuWIcons);
        props.put(4023, newSettings);
        props.put(8013, Objects.equals(filterChats, "2")); // lupa sera removida e sera adicionado uma barra no lugar.
        props.put(5834, strokeButtons);
        props.put(5509, outlinedIcons);
        props.put(2358, false);

        var methodProps = Unobfuscator.loadPropsMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(methodProps));

        var dataUsageActivityClass = XposedHelpers.findClass("com.whatsapp.settings.SettingsDataUsageActivity", loader);

        XposedBridge.hookMethod(methodProps, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int i = (int) (param.args.length > 2 ? param.args[2] : param.args[1]);

                var propValue = props.get(i);
                if (propValue == null) return;
                param.setResult(propValue);
                // Fix Bug in Settings Data Usage
                if (i == 4023 && propValue && Unobfuscator.isCalledFromClass(dataUsageActivityClass)) {
                    param.setResult(false);
                }
            }
        });

        var homeActivity = findClass("com.whatsapp.HomeActivity", loader);
        XposedHelpers.findAndHookMethod(homeActivity, "onCreateOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Menu menu = (Menu) param.args[0];
                Activity home = (Activity) param.thisObject;
                @SuppressLint({"UseCompatLoadingForDrawables", "DiscouragedApi"})
                var iconDraw = DesignUtils.getDrawableByName("vec_account_switcher");
                iconDraw.setTint(0xff8696a0);
                var itemMenu = menu.add(0, 0, 0, ResId.string.restart_whatsapp).setIcon(iconDraw).setOnMenuItemClickListener(item -> {
                    restartApp(home);
                    return true;
                });
                if (newSettings) {
                    itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                }
                if (showDnd) {
                    InsertDNDOption(menu, home);
                } else {
                    Utils.getApplication().getSharedPreferences(Utils.getApplication().getPackageName() + "_mdgwa_preferences", Context.MODE_PRIVATE).edit().putBoolean("dndmode", false).commit();
                }
            }
        });


        XposedHelpers.findAndHookMethod("com.whatsapp.HomeActivity", loader, "onPrepareOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                var item = menu.findItem(Utils.getID("menuitem_search", "id"));
                if (item != null) {
                    item.setVisible(Objects.equals(filterChats, "1"));
                }
            }
        });

        if (removechannelRec) {
            var removeChannelRecClass = Unobfuscator.loadRemoveChannelRecClass(loader);
            XposedBridge.hookAllConstructors(removeChannelRecClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0 && param.args[0] instanceof List list) {
                        if (list.isEmpty()) return;
                        list.clear();
                    }
                }
            });
        }

        if (separateGroups) {
            var filterAdaperClass = Unobfuscator.loadFilterAdaperClass(loader);
            XposedBridge.hookAllConstructors(filterAdaperClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length == 3 && param.args[2] instanceof List list) {
                        var newList = new ArrayList<Object>(list);
                        newList.removeIf(item -> {
                            var name = XposedHelpers.getObjectField(item, "A01");
                            return name == null || name == "CONTACTS_FILTER" || name == "GROUP_FILTER";
                        });
                        param.args[2] = newList;
                    }
                }
            });
            var methodSetFilter = Arrays.stream(filterAdaperClass.getDeclaredMethods()).filter(m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(int.class)).findFirst().orElse(null);

            XposedBridge.hookMethod(methodSetFilter, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    var index = (int) param.args[0];
                    var list = (List) XposedHelpers.getObjectField(param.thisObject, "A01");
                    if (list == null || index >= list.size()) {
                        param.setResult(null);
                    }
                }
            });
        }
    }

    private static void restartApp(Activity home) {
        Intent intent = Utils.getApplication().getPackageManager().getLaunchIntentForPackage(Utils.getApplication().getPackageName());
        if (intent != null) {
            home.finishAffinity();
            Utils.getApplication().startActivity(intent);
        }
        Runtime.getRuntime().exit(0);
    }

    @SuppressLint({"DiscouragedApi", "UseCompatLoadingForDrawables"})
    private static void InsertDNDOption(Menu menu, Activity home) {
        var shared = Utils.getApplication().getSharedPreferences(Utils.getApplication().getPackageName() + "_mdgwa_preferences", Context.MODE_PRIVATE);
        var dndmode = shared.getBoolean("dndmode", false);
        int iconDraw;
        iconDraw = Utils.getID(dndmode ? "ic_location_nearby_disabled" : "ic_location_nearby", "drawable");
        var item = menu.add(0, 0, 0, "Dnd Mode " + dndmode);
        item.setIcon(iconDraw);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        item.setOnMenuItemClickListener(menuItem -> {
            if (!dndmode) {
                new AlertDialog.Builder(home)
                        .setTitle(ResId.string.dnd_mode_title)
                        .setMessage(ResId.string.dnd_message)
                        .setPositiveButton(ResId.string.activate, (dialog, which) -> {
                            shared.edit().putBoolean("dndmode", true).commit();
                            XposedBridge.log(String.valueOf(shared.getBoolean("dndmode", false)));
                            restartApp(home);
                        })
                        .setNegativeButton(ResId.string.cancel, (dialog, which) -> dialog.dismiss())
                        .create().show();
                return true;
            }
            shared.edit().putBoolean("dndmode", false).commit();
            restartApp(home);
            return true;
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Others";
    }
}
