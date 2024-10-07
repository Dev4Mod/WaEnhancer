package com.wmods.wppenhacer.xposed.features.others;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.HashSet;
import java.util.LinkedHashSet;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class MenuHome extends Feature {

    public static HashSet<HomeMenuItem> menuItems = new LinkedHashSet<>();


    public MenuHome(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        hookMenu();
        var action = prefs.getBoolean("buttonaction", true);

        // restart button
        menuItems.add((menu, activity) -> InsertRestartButton(menu, activity, action));

        // dnd mode
        menuItems.add((menu, activity) -> InsertDNDOption(menu, activity, action));

        // ghost mode
        menuItems.add((menu, activity) -> InsertGhostModeOption(menu, activity, action));

        // freeze last seen
        menuItems.add((menu, activity) -> InsertFreezeLastSeenOption(menu, activity, action));

        // open WAE
        menuItems.add(this::InsertOpenWae);

    }

    private void InsertOpenWae(Menu menu, Activity activity) {
        var waeMenu = prefs.getBoolean("open_wae", true);
        if (!waeMenu) return;
        var itemMenu = menu.add(0, 0, 9999, " " + activity.getString(ResId.string.app_name));
        var iconDraw = DesignUtils.getDrawableByName("ic_settings");
        iconDraw.setTint(0xff8696a0);
        itemMenu.setIcon(iconDraw);
        itemMenu.setOnMenuItemClickListener(item -> {
            Intent intent = activity.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        });
    }

    private void InsertGhostModeOption(Menu menu, Activity activity, boolean newSettings) {
        var ghostmode = WppCore.getPrivBoolean("ghostmode", false);
        if (!prefs.getBoolean("ghostmode", true)) {
            if (ghostmode) {
                WppCore.setPrivBoolean("ghostmode", false);
                Utils.doRestart(activity);
            }
            return;
        }
        var itemMenu = menu.add(0, 0, 0, ResId.string.ghost_mode);

        var iconDraw = activity.getDrawable(ghostmode ? ResId.drawable.ghost_enabled : ResId.drawable.ghost_disabled);
        if (iconDraw != null) {
            iconDraw.setTint(newSettings ? DesignUtils.getPrimaryTextColor() : 0xff8696a0);
            itemMenu.setIcon(iconDraw);
        }
        if (newSettings) {
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        itemMenu.setOnMenuItemClickListener(item -> {
            new AlertDialogWpp(activity).setTitle(activity.getString(ResId.string.ghost_mode_s, (ghostmode ? "ON" : "OFF"))).
                    setMessage(activity.getString(ResId.string.ghost_mode_message))
                    .setPositiveButton(activity.getString(ResId.string.disable), (dialog, which) -> {
                        WppCore.setPrivBoolean("ghostmode", false);
                        Utils.doRestart(activity);
                    })
                    .setNegativeButton(activity.getString(ResId.string.enable), (dialog, which) -> {
                        WppCore.setPrivBoolean("ghostmode", true);
                        Utils.doRestart(activity);
                    }).show();
            return true;

        });
    }

    private void InsertRestartButton(Menu menu, Activity activity, boolean newSettings) {
        if (!prefs.getBoolean("restartbutton", true)) return;
        var iconDraw = activity.getDrawable(ResId.drawable.refresh);
        iconDraw.setTint(newSettings ? DesignUtils.getPrimaryTextColor() : 0xff8696a0);
        var itemMenu = menu.add(0, 0, 0, ResId.string.restart_whatsapp).setIcon(iconDraw);
        if (newSettings) {
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        itemMenu.setOnMenuItemClickListener(item -> {
            Utils.doRestart(activity);
            return true;
        });
    }

    @SuppressLint({"DiscouragedApi", "UseCompatLoadingForDrawables", "ApplySharedPref"})
    private void InsertDNDOption(Menu menu, Activity activity, boolean newSettings) {
        var dndmode = WppCore.getPrivBoolean("dndmode", false);
        if (!prefs.getBoolean("show_dndmode", false)) {
            if (WppCore.getPrivBoolean("dndmode", false)) {
                WppCore.setPrivBoolean("dndmode", false);
                Utils.doRestart(activity);
            }
            return;
        }
        var item = menu.add(0, 0, 0, activity.getString(ResId.string.dnd_mode_title));
        var drawable = Utils.getApplication().getDrawable(dndmode ? ResId.drawable.airplane_enabled : ResId.drawable.airplane_disabled);
        if (drawable != null) {
            drawable.setTint(newSettings ? DesignUtils.getPrimaryTextColor() : 0xff8696a0);
            item.setIcon(drawable);
        }
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        item.setOnMenuItemClickListener(menuItem -> {
            if (!dndmode) {
                new AlertDialogWpp(activity)
                        .setTitle(activity.getString(ResId.string.dnd_mode_title))
                        .setMessage(activity.getString(ResId.string.dnd_message))
                        .setPositiveButton(activity.getString(ResId.string.activate), (dialog, which) -> {
                            WppCore.setPrivBoolean("dndmode", true);
                            Utils.doRestart(activity);
                        })
                        .setNegativeButton(activity.getString(ResId.string.cancel), (dialog, which) -> dialog.dismiss())
                        .create().show();
                return true;
            }
            WppCore.setPrivBoolean("dndmode", false);
            Utils.doRestart(activity);
            return true;
        });
    }

    private void InsertFreezeLastSeenOption(Menu menu, Activity activity, boolean newSettings) {
        var freezelastseen = WppCore.getPrivBoolean("freezelastseen", false);
        if (!prefs.getBoolean("show_freezeLastSeen", true)) {
            if (freezelastseen) {
                WppCore.setPrivBoolean("freezelastseen", false);
                Utils.doRestart(activity);
            }
            return;
        }

        MenuItem item = menu.add(0, 0, 0, activity.getString(ResId.string.freezelastseen_title));
        var drawable = Utils.getApplication().getDrawable(freezelastseen ? ResId.drawable.eye_disabled : ResId.drawable.eye_enabled);
        if (drawable != null) {
            drawable.setTint(newSettings ? DesignUtils.getPrimaryTextColor() : 0xff8696a0);
            item.setIcon(drawable);
        }
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        item.setOnMenuItemClickListener(menuItem -> {
            if (!freezelastseen) {
                new AlertDialogWpp(activity)
                        .setTitle(activity.getString(ResId.string.freezelastseen_title))
                        .setMessage(activity.getString(ResId.string.freezelastseen_message))
                        .setPositiveButton(activity.getString(ResId.string.activate), (dialog, which) -> {
                            WppCore.setPrivBoolean("freezelastseen", true);
                            Utils.doRestart(activity);
                        })
                        .setNegativeButton(activity.getString(ResId.string.cancel), (dialog, which) -> dialog.dismiss())
                        .create().show();
                return true;
            }
            WppCore.setPrivBoolean("freezelastseen", false);
            Utils.doRestart(activity);
            return true;
        });
    }

    private void hookMenu() {
        XposedHelpers.findAndHookMethod("com.whatsapp.HomeActivity", classLoader, "onCreateOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                var activity = (Activity) param.thisObject;
                for (var menuItem : MenuHome.menuItems) {
                    menuItem.addMenu(menu, activity);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Menu Home";
    }

    public interface HomeMenuItem {

        void addMenu(Menu menu, Activity activity);

    }
}
