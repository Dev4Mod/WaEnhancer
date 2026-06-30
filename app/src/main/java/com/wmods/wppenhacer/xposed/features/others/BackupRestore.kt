package com.wmods.wppenhacer.xposed.features.others

import android.app.Activity
import android.content.Intent
import android.view.Menu
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.findFirstClassUsingName
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.Locale

class BackupRestore(loader: ClassLoader, preferences:SharedPreferences) :
    Feature(loader, preferences) {

    override fun getPluginName(): String {
        return "BackupRestore"
    }

    override fun doHook() {
        if (!prefs.getBoolean("force_restore_backup_feature", false)) return

        val restoreFromBackupClass = findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "RestoreFromBackupActivity"
        )

        XposedBridge.hookAllMethods(
            Activity::class.java,
            "onPrepareOptionsMenu",
            object : XC_MethodHook() {

                override fun afterHookedMethod(param: MethodHookParam) {
                    val name =
                        param.thisObject.javaClass.simpleName.lowercase(Locale.getDefault())
                    if (!(name.contains("drive") && name.contains("google"))) return
                    val menu = param.args[0] as Menu
                    if (menu.findItem(10001) != null) return
                    val menuItem = menu.add(0, 10001, 0, R.string.force_restore_backup_experimental)
                    val activity = param.thisObject as Activity
                    menuItem.setOnMenuItemClickListener {
                        AlertDialogWpp(activity)
                            .setTitle(R.string.force_restore_backup)
                            .setMessage(activity.getString(R.string.warning_restore))
                            .setPositiveButton(
                                activity.getString(R.string.yes)
                            ) { _,_ ->
                                try {
                                    val intent = Intent(activity, restoreFromBackupClass)
                                    intent.action = "action_show_restore_one_time_setup"
                                    activity.startActivityForResult(intent, 10001)
                                } catch (e: Exception) {
                                    XposedBridge.log(e)
                                    Utils.showToast(
                                        "Error launching restore activity: " + e.message,
                                        Toast.LENGTH_LONG
                                    )
                                }
                            }
                            .setNegativeButton(activity.getString(R.string.no), null)
                            .show()
                        true
                    }
                }
            })
    }
}
