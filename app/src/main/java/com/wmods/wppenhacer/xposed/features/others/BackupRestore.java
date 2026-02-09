package com.wmods.wppenhacer.xposed.features.others;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class BackupRestore extends Feature {

    public BackupRestore(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public String getPluginName() {
        return "BackupRestore";
    }

    @Override
    public void doHook() throws Exception {
        if (!prefs.getBoolean("force_restore_backup_feature", false)) return;

        Class<?> settingsDriveClass = Unobfuscator.loadSettingsGoogleDriveActivity(classLoader);
        
        XposedHelpers.findAndHookMethod(settingsDriveClass, "onPrepareOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Menu menu = (Menu) param.args[0];
                // Hardcoding string to ensure it appears without resource injection issues
                String title = "Force Restore Backup (Experimental)";
                
                // Use a high ID to avoid conflicts
                if (menu.findItem(10001) == null) {
                    menu.add(0, 10001, 0, title);
                }
            }
        });

        XposedHelpers.findAndHookMethod(settingsDriveClass, "onOptionsItemSelected", MenuItem.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                MenuItem item = (MenuItem) param.args[0];
                if (item.getItemId() == 10001) {
                    Activity activity = (Activity) param.thisObject;
                    try {
                        Class<?> restoreClass = Unobfuscator.loadRestoreBackupActivity(classLoader);
                        Intent intent = new Intent(activity, restoreClass);
                        activity.startActivity(intent);
                        param.setResult(true);
                    } catch (Exception e) {
                        Utils.showToast("Error launching restore activity: " + e.getMessage(), Toast.LENGTH_LONG);
                        XposedBridge.log(e);
                    }
                }
            }
        });
    }
}
