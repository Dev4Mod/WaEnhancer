package com.wmods.wppenhacer.toolkit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.provider.ContactsContract
import android.util.TypedValue
import android.widget.Toast
import de.robv.android.xposed.XposedBridge
import java.io.File

/** Utilidades para trabajar dentro del proceso de WhatsApp (o WhatsApp Business). */
object Host {

    fun dp(context: Context, v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
        ).toInt()

    /** Carpeta de datos de la cuenta activa (con varias cuentas está en accounts/<id>). */
    fun accountDataDir(context: Context): File {
        val dataDir = context.applicationContext.filesDir.parentFile!!
        val sw = File(dataDir, "app_account_switching/active_account")
        if (sw.exists()) {
            val id = try { sw.readText().trim() } catch (_: Exception) { "" }
            if (id.isNotEmpty()) {
                val accountDir = File(dataDir, "accounts/$id")
                if (accountDir.exists()) return accountDir
            }
        }
        return dataDir
    }

    /** Carpeta donde se guardan las copias de seguridad de las bases de datos. */
    fun backupRoot(context: Context): File = File(rootWhatsAppDir(context).parentFile, "WaToolkit/Backups")

    /** Carpeta raíz de medios de WhatsApp ("WhatsApp" o "WhatsApp Business"). */
    fun rootWhatsAppDir(context: Context): File {
        val app = context.applicationContext
        val appName = app.packageManager.getApplicationLabel(app.applicationInfo).toString()
        val mediaDirs = app.externalMediaDirs
        if (mediaDirs.isNotEmpty() && mediaDirs[0] != null) {
            val root = File(mediaDirs[0], appName)
            if (root.exists()) return root
        }
        return File(Environment.getExternalStorageDirectory(), appName)
    }

    fun restart(activity: Activity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
        val main = Intent.makeRestartActivityTask(intent.component)
        main.setPackage(activity.packageName)
        activity.startActivity(main)
        Runtime.getRuntime().exit(0)
    }

    /** Abre el chat [jid] de WhatsApp situado en el mensaje [rowId]. Devuelve false si no se pudo. */
    @Suppress("DEPRECATION")
    fun openConversation(activity: Activity, jid: String, rowId: Long, sortId: Long?): Boolean = try {
        val name = activity.packageManager
            .getPackageInfo(activity.packageName, android.content.pm.PackageManager.GET_ACTIVITIES)
            .activities?.map { it.name }.orEmpty().let { names ->
                names.firstOrNull { it.endsWith(".Conversation") } ?: names.first { it.endsWith("Conversation") }
            }
        val intent = Intent(activity, activity.classLoader.loadClass(name)).apply {
            putExtra("jid", jid)
            if (sortId != null) putExtra("sort_id", sortId)
            putExtra("row_id", rowId)
            putExtra("start_t", android.os.SystemClock.uptimeMillis())
            putExtra("mat_entry_point", 64)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        activity.startActivity(intent)
        true
    } catch (t: Throwable) {
        log(t)
        false
    }

    fun toast(context: Context, text: String?, length: Int = Toast.LENGTH_SHORT) {
        if (text.isNullOrEmpty()) return
        val act = context as? Activity
        val show = { Toast.makeText(context, text, length).show() }
        if (act != null) act.runOnUiThread(show) else show()
    }

    fun log(t: Throwable) {
        XposedBridge.log("[WaToolkit] " + android.util.Log.getStackTraceString(t))
    }

    fun log(msg: String) {
        XposedBridge.log("[WaToolkit] $msg")
    }

    /** Color de texto de los diálogos del módulo (tema Material You). */
    fun textColor(context: Context): Int = Monet.palette(context).onSurface

    /** Nombre del contacto del teléfono para un número, si WhatsApp tiene permiso de contactos. */
    fun contactName(context: Context, phoneDigits: String): String? = try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode("+$phoneDigits")
        )
        context.contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
    } catch (_: Throwable) {
        null
    }

    /** Contactos del teléfono con número internacional (E.164) conocido: (nombre, solo dígitos). */
    fun deviceContacts(context: Context): List<Pair<String, String>> = try {
        val out = LinkedHashMap<String, String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
            ), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                val digits = c.getString(1)?.filter { it.isDigit() }?.takeIf { it.length in 7..15 } ?: continue
                out.putIfAbsent(digits, name)
            }
        }
        out.map { it.value to it.key }
    } catch (_: Throwable) {
        emptyList()
    }
}
