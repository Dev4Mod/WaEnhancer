package com.wmods.wppenhacer.xposed.features.general

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.Menu
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.utils.RealPathUtil
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.features.others.MenuHome
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import androidx.core.net.toUri

class LiteMode(loader: ClassLoader, preferences: XSharedPreferences) : Feature(loader, preferences) {

    companion object {
        const val REQUEST_FOLDER = 852583

        @JvmStatic
        fun getDownloadsUri(): Uri {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            }
        }

        @JvmStatic
        fun processDownloadResult(activity: Activity, intent: Intent): String? {
            val uri = intent.data ?: return null
            WppCore.setPrivString("download_folder", uri.toString())
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            return uri.toString()
        }

        private fun showDialogUriPermission(activity: Activity) {
            AlertDialogWpp(activity)
                .setTitle(activity.getString(R.string.download_folder_permission))
                .setMessage(activity.getString(R.string.ask_download_folder))
                .setPositiveButton(activity.getString(R.string.allow)) { _, _ ->
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getDownloadsUri())
                    activity.startActivityForResult(intent, REQUEST_FOLDER)
                }
                .setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    @Throws(Throwable::class)
    override fun doHook() {
        if (!prefs.getBoolean("lite_mode", false)) return

        MenuHome.addMenuItem { menu, activity -> insertDownloadFolderButton(menu, activity) }

        XposedHelpers.findAndHookMethod(WppCore.homeActivityClass, "onCreate", Bundle::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                val wae = WppCore.getPrivString("download_folder", null)
                if (wae == null || !isUriPermissionGranted(activity, wae.toUri())) {
                    showDialogUriPermission(activity)
                }
            }
        })

        XposedHelpers.findAndHookMethod(
            Activity::class.java, "onActivityResult",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    val id = param.args[0] as Int
                    val intent = param.args[2] as Intent
                    if (id == REQUEST_FOLDER && param.args[1] as Int == Activity.RESULT_OK) {
                        processDownloadResult(activity, intent)
                    }
                }
            })
    }

    override fun getPluginName(): String {
        return "Lite Mode"
    }

    private fun insertDownloadFolderButton(menu: Menu, activity: Activity) {
        val waeMenu = prefs.getBoolean("open_wae", true)
        if (!waeMenu) return
        val itemMenu = menu.add(0, 0, 9999, "Download Folder")
        val iconDraw = activity.getDrawable(R.drawable.download)!!
        iconDraw.setTint(0xff8696a0.toInt())
        itemMenu.icon = iconDraw
        itemMenu.setOnMenuItemClickListener {
            var folder = WppCore.getPrivString("download_folder", null)
            if (folder != null) {
                try {
                    folder = RealPathUtil.getRealFolderPath(activity, folder.toUri())
                } catch (_: Exception) {
                }
            }
            val dialog = AlertDialogWpp(activity)
            dialog.setTitle("Download Folder")
            dialog.setMessage("Current Folder to download is $folder")
            dialog.setNegativeButton("Cancel") { dialog1, _ -> dialog1.dismiss() }
            dialog.setPositiveButton("Select") { _, _ ->
                showDialogUriPermission(activity)
            }.show()
            true
        }
    }

    private fun isUriPermissionGranted(context: Context, uri: Uri): Boolean {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val permissionCheck = context.checkUriPermission(
            uri, android.os.Process.myPid(), android.os.Process.myUid(), takeFlags
        )
        return permissionCheck == PackageManager.PERMISSION_GRANTED
    }
}
