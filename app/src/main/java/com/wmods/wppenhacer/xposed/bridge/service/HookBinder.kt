package com.wmods.wppenhacer.xposed.bridge.service

import android.os.ParcelFileDescriptor
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.xposed.bridge.WaeIIFace
import java.io.File
import java.io.FileNotFoundException

object HookBinder : WaeIIFace.Stub() {

    private fun callerAllowed(): Boolean {
        return try {
            CallerGuard.isCallerAllowed(App.instance)
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolve(path: String): File? {
        if (!callerAllowed()) return null
        return CallerGuard.resolveSharedPath(path)
    }

    override fun openFile(path: String, create: Boolean): ParcelFileDescriptor? {
        val file = resolve(path) ?: return null
        if (!file.exists() && create) {
            try {
                file.createNewFile()
            } catch (_: Exception) {
                return null
            }
        }
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
        } catch (_: FileNotFoundException) {
            null
        }
    }

    override fun createDir(path: String): Boolean {
        val file = resolve(path) ?: return false
        return file.mkdirs()
    }

    override fun exists(path: String): Boolean {
        val file = resolve(path) ?: return false
        return file.exists()
    }

    override fun listFiles(path: String): List<File> {
        val file = resolve(path) ?: return emptyList()
        return file.listFiles()?.toList() ?: emptyList()
    }
}
