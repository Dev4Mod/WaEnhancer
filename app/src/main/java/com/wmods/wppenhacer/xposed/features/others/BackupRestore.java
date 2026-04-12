package com.wmods.wppenhacer.xposed.features.others;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class BackupRestore extends Feature {

    public BackupRestore(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "BackupRestore";
    }

    @Override
    public void doHook() throws Exception {
        if (!prefs.getBoolean("force_restore_backup_feature", false)) return;

        var restoreFromBackupClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "RestoreFromBackupActivity");

        XposedBridge.hookAllMethods(Activity.class, "onPrepareOptionsMenu", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var name = param.thisObject.getClass().getSimpleName().toLowerCase();
                if (!(name.contains("drive") && name.contains("google"))) return;
                Menu menu = (Menu) param.args[0];
                if (menu.findItem(10001) != null) return;
                var menuItem = menu.add(0, 10001, 0, ResId.string.force_restore_backup_experimental);
                Activity activity = (Activity) param.thisObject;
                menuItem.setOnMenuItemClickListener((item) -> {
                    new AlertDialogWpp(activity)
                            .setTitle(ResId.string.force_restore_backup)
                            .setMessage(activity.getString(ResId.string.warning_restore))
                            .setPositiveButton(activity.getString(ResId.string.yes), (dialog, which) -> {
                                try {
                                    Intent intent = new Intent(activity, restoreFromBackupClass);
                                    intent.setAction("action_show_restore_one_time_setup");
                                    activity.startActivityForResult(intent, 10001);
                                } catch (Exception e) {
                                    XposedBridge.log(e);
                                    Utils.showToast("Error launching restore activity: " + e.getMessage(), Toast.LENGTH_LONG);
                                }
                            })
                            .setNegativeButton(activity.getString(ResId.string.no), null)
                            .show();

                    return true;
                });

            }
        });

    }
}
