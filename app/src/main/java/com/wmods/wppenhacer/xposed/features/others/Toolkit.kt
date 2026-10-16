package com.wmods.wppenhacer.xposed.features.others

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.toolkit.Hub
import com.wmods.wppenhacer.toolkit.Host
import com.wmods.wppenhacer.toolkit.S
import com.wmods.wppenhacer.toolkit.backup.BackupFlow
import com.wmods.wppenhacer.toolkit.backup.RecoverChatFlow
import com.wmods.wppenhacer.toolkit.deleted.DeletedMessagesFlow
import com.wmods.wppenhacer.toolkit.export.ExportChatFlow
import com.wmods.wppenhacer.toolkit.importer.ImportChatFlow
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.homeActivityClass

/**
 * Herramientas de WaToolkit (importar/exportar chats, mensajes borrados, copias): añade "Tools"/"Herramientas" al menú de la pantalla principal con
 * las herramientas de importar/exportar chats, mensajes borrados y copias de seguridad.
 *
 * Se activa con el ajuste "toolkit" (por defecto activado).
 */
class Toolkit(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    @Throws(Throwable::class)
    override fun doHook() {
        if (!xprefs.getBoolean("toolkit", true)) return

        MenuHome.addMenuItem { menu, activity ->
            menu.add(0, MENU_ID, 0, S.get(S.hub_title)).setOnMenuItemClickListener {
                showHub(activity)
                true
            }
        }
        hookActivityResult()
    }

    private fun hookActivityResult() {
        homeActivityClass.resolve().firstMethod {
            name = "onActivityResult"
            superclass()
            parameters(
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Intent::class.java
            )
        }.hook {
            after {
                val requestCode = args[0] as Int
                val resultCode = args[1] as Int
                val data = args[2] as? Intent
                val activity = instance as? Activity ?: return@after

                if (requestCode == ExportChatFlow.REQUEST_SAVE) {
                    try {
                        ExportChatFlow.onResult(resultCode, data)
                    } catch (t: Throwable) {
                        Host.log(t)
                    }
                    return@after
                }
                if (requestCode != REQUEST_PICK_FILE || resultCode != Activity.RESULT_OK) return@after
                val uri = data?.data ?: return@after
                try {
                    ImportChatFlow(activity, uri).start()
                } catch (t: Throwable) {
                    Host.log(t)
                    Host.toast(activity, t.message)
                }
            }
        }
    }

    /** Menú común: cada herramienta nueva se añade aquí. */
    private fun showHub(activity: Activity) {
        Hub.show(
            activity, BuildConfig.VERSION_NAME, listOf(
                Hub.Tool("📥", S.get(S.import_chat), S.get(S.tool_import_desc)) { openPicker(activity) },
                Hub.Tool("📤", S.get(S.export_menu), S.get(S.tool_export_desc)) {
                    ExportChatFlow(activity).start()
                },
                Hub.Tool("🗑", S.get(S.deleted_menu), S.get(S.tool_deleted_desc)) {
                    DeletedMessagesFlow(activity).start()
                },
                Hub.Tool("♻️", S.get(S.recover_menu), S.get(S.tool_recover_desc)) {
                    RecoverChatFlow(activity).start()
                },
                Hub.Tool("💾", S.get(S.backup_menu), S.get(S.tool_backup_desc)) {
                    BackupFlow(activity).start()
                }
            )
        )
    }

    private fun openPicker(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "text/plain",
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream"
                )
            )
        }
        try {
            activity.startActivityForResult(intent, REQUEST_PICK_FILE)
        } catch (e: Exception) {
            Host.toast(activity, e.message)
        }
    }

    override fun getPluginName(): String = "Toolkit"

    private companion object {
        const val REQUEST_PICK_FILE = 0x5A17
        const val MENU_ID = 0x5A18
    }
}
