package com.wmods.wppenhacer.xposed.bridge.service

import android.content.Context
import android.os.Binder
import android.os.Process
import java.io.File

/**
 * Controla quién puede usar el puente (HookBinder / preferencias remotas).
 *
 * Solo se aceptan llamadas del propio módulo y de WhatsApp / WhatsApp Business.
 * Cualquier otra app instalada queda rechazada, aunque el servicio y los providers
 * estén exportados (necesario para que el proceso hookeado pueda alcanzarlos).
 */
object CallerGuard {

    private val ALLOWED_PACKAGES = arrayOf("com.whatsapp", "com.whatsapp.w4b")

    // Raíces de almacenamiento compartido permitidas (tras canonicalizar la ruta).
    private val ALLOWED_PATH_ROOTS = arrayOf(
        "/storage/",
        "/mnt/user/",
        "/mnt/pass_through/",
        "/mnt/runtime/",
        "/sdcard/",
        "/mnt/sdcard/"
    )

    /** True si el UID de la transacción en curso es el del módulo o el de WhatsApp. */
    @Suppress("DEPRECATION")
    fun isCallerAllowed(context: Context?): Boolean {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return true
        val pm = context?.packageManager ?: return false
        return ALLOWED_PACKAGES.any { pkg ->
            try {
                pm.getPackageUid(pkg, 0) == callingUid
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Resuelve [path] a su forma canónica (sin "..", ni enlaces simbólicos) y solo la
     * devuelve si cae en almacenamiento compartido. Rutas privadas (/data/...) o del
     * sistema se rechazan, de modo que el puente no exponga datos de otras apps ni
     * los del propio módulo.
     */
    fun resolveSharedPath(path: String): File? {
        if (path.isEmpty() || path.indexOf('\u0000') >= 0) return null
        val canonical = try {
            File(path).canonicalFile
        } catch (_: Exception) {
            return null
        }
        val canonicalPath = canonical.path
        val allowed = ALLOWED_PATH_ROOTS.any { root ->
            canonicalPath.startsWith(root) || canonicalPath == root.trimEnd('/')
        }
        return if (allowed) canonical else null
    }
}
