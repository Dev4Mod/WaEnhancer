package com.wmods.wppenhacer.xposed.features.others.importchat

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.features.others.MenuHome
import com.wmods.wppenhacer.xposed.utils.Utils

/**
 * Entrada "Importar chat" en el menú de la pantalla principal: abre el selector de archivos y
 * pasa el .txt/.zip elegido al flujo de importación.
 */
class ImportChat(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun getPluginName(): String = "Import Chat"

    override fun doHook() {
        if (!xprefs.getBoolean("import_chat", true)) return

        MenuHome.addMenuItem { menu, activity ->
            menu.add(0, 0, 0, activity.getString(R.string.import_chat))
                .setOnMenuItemClickListener {
                    openPicker(activity)
                    true
                }
        }

        Activity::class.java.resolve().firstMethod {
            name = "onActivityResult"
            superclass()
            parameters(Int::class, Int::class, Intent::class.java)
        }.hook {
            after {
                val requestCode = args[0] as Int
                val resultCode = args[1] as Int
                val data = args[2] as Intent?
                if (requestCode != REQUEST_PICK_FILE || resultCode != Activity.RESULT_OK) return@after
                val uri = data?.data ?: return@after
                val activity = instance as? Activity ?: return@after
                ImportChatFlow(activity, uri).start()
            }
        }
    }

    private fun openPicker(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("text/plain", "application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
        }
        try {
            activity.startActivityForResult(intent, REQUEST_PICK_FILE)
        } catch (e: Exception) {
            Utils.showToast(e.message)
        }
    }

    private companion object {
        const val REQUEST_PICK_FILE = 0x5A17
    }
}
