package com.wmods.wppenhacer.xposed.features.others

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.view.Menu
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.findFirstClassUsingName
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.Locale

class BackupRestore(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    override fun getPluginName(): String {
        return "BackupRestore"
    }

    override fun doHook() {
        if (!xprefs.getBoolean("force_restore_backup_feature", false)) return

        val restoreFromBackupClass = findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "RestoreFromBackupActivity"
        )

        Activity::class.java.resolve().method {
            name = "onPrepareOptionsMenu"
        }.hookAll {
            after {
                val name =
                    instance.javaClass.simpleName.lowercase(Locale.getDefault())
                if (!(name.contains("drive") && name.contains("google"))) return@after
                val menu = args[0] as Menu
                if (menu.findItem(10001) != null) return@after
                val menuItem = menu.add(0, 10001, 0, R.string.force_restore_backup_experimental)
                val activity = instance as Activity
                menuItem.setOnMenuItemClickListener {
                    AlertDialogWpp(activity)
                        .setTitle(R.string.force_restore_backup)
                        .setMessage(activity.getString(R.string.warning_restore))
                        .setPositiveButton(
                            activity.getString(R.string.yes)
                        ) { _, _ ->
                            try {
                                val intent = Intent(activity, restoreFromBackupClass)
                                intent.action = "action_show_restore_one_time_setup"
                                activity.startActivityForResult(intent, 10001)
                            } catch (e: Exception) {
                                YukiLog.log(e)
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
        }
    }
}
