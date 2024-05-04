package com.wmods.wppenhacer.xposed.features.general;

import static de.robv.android.xposed.XposedHelpers.findClass;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.DesignUtils;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.ResId;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;

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

    public static HashMap<Integer, Boolean> propsBoolean = new HashMap<>();
    public static HashMap<Integer, Integer> propsInteger = new HashMap<>();

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
        var filterSeen = prefs.getBoolean("filterseen", false);
        var fbstyle = prefs.getBoolean("fbstyle", false);
        var alertSticker = prefs.getBoolean("alertsticker", false);
        var channels = prefs.getBoolean("channels", false);
        var igstatus = prefs.getBoolean("igstatus", false);
        var metaai = prefs.getBoolean("metaai", false);

        propsBoolean.put(5171, filterSeen); // filtros de chat e grupos
        propsBoolean.put(4524, novoTema);
        propsBoolean.put(4497, menuWIcons);
        propsBoolean.put(4023, newSettings);
        propsBoolean.put(8013, Objects.equals(filterChats, "2")); // lupa sera removida e sera adicionado uma barra no lugar.
        propsBoolean.put(5834, strokeButtons);
        propsBoolean.put(5509, outlinedIcons);
        propsBoolean.put(2358, false);
        propsBoolean.put(7516, fbstyle);
        if (metaai) {
            propsBoolean.put(8025, false);
            propsBoolean.put(6251, false);
            propsBoolean.put(7639, false);
        }

        propsInteger.put(8522, fbstyle ? 1 : 0);
        propsInteger.put(8521, fbstyle ? 1 : 0);
        propsInteger.put(3877, channels ? igstatus ? 2 : 0 : 2);


        var methodPropsBoolean = Unobfuscator.loadPropsBooleanMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(methodPropsBoolean));

        var dataUsageActivityClass = XposedHelpers.findClass("com.whatsapp.settings.SettingsDataUsageActivity", loader);
        XposedBridge.hookMethod(methodPropsBoolean, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                int i = (int) param.args[param.args.length - 1];

                var propValue = propsBoolean.get(i);
                if (propValue != null) {
                    param.setResult(propValue);
                    // Fix Bug in Settings Data Usage
                    if (i == 4023 && propValue && Unobfuscator.isCalledFromClass(dataUsageActivityClass)) {
                        param.setResult(false);
                    }
                }
//                if ((boolean)param.getResult())
//                    log("i: " + i + " propValue: " + param.getResult());
            }
        });

        var methodPropsInteger = Unobfuscator.loadPropsIntegerMethod(loader);

        XposedBridge.hookMethod(methodPropsInteger, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int i = (int) param.args[param.args.length - 1];
                var propValue = propsInteger.get(i);
                if (propValue == null) return;
                param.setResult(propValue);
            }
        });

        var homeActivity = findClass("com.whatsapp.HomeActivity", loader);
        XposedHelpers.findAndHookMethod(homeActivity, "onCreateOptionsMenu", Menu.class, new XC_MethodHook() {
            @SuppressLint("ApplySharedPref")
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

        if (alertSticker) {
            var sendStickerMethod = Unobfuscator.loadSendStickerMethod(loader);
            XposedBridge.hookMethod(sendStickerMethod, new XC_MethodHook() {
                private Unhook unhooked;

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    unhooked = XposedHelpers.findAndHookMethod(View.class, "setOnClickListener", View.OnClickListener.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            View.OnClickListener mCaptureOnClickListener = (View.OnClickListener) param.args[0];
                            if (mCaptureOnClickListener == null) return;
                            param.args[0] = (View.OnClickListener) view -> {
                                var context = view.getContext();
                                var dialog = new AlertDialogWpp(view.getContext());
                                dialog.setTitle(context.getString(ResId.string.send_sticker));
                                dialog.setMessage(context.getString(ResId.string.do_you_want_to_send_sticker));
                                dialog.setPositiveButton(context.getString(ResId.string.send), (dialog1, which) -> mCaptureOnClickListener.onClick(view));
                                dialog.setNegativeButton(context.getString(ResId.string.cancel), null);
                                dialog.show();
                            };
                        }
                    });
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    unhooked.unhook();
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

    @SuppressLint({"DiscouragedApi", "UseCompatLoadingForDrawables", "ApplySharedPref"})
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
                new AlertDialogWpp(home)
                        .setTitle(home.getString(ResId.string.dnd_mode_title))
                        .setMessage(home.getString(ResId.string.dnd_message))
                        .setPositiveButton(home.getString(ResId.string.activate), (dialog, which) -> {
                            shared.edit().putBoolean("dndmode", true).commit();
                            XposedBridge.log(String.valueOf(shared.getBoolean("dndmode", false)));
                            restartApp(home);
                        })
                        .setNegativeButton(home.getString(ResId.string.cancel), (dialog, which) -> dialog.dismiss())
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
