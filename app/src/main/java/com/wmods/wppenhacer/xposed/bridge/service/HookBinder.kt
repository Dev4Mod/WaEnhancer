package com.wmods.wppenhacer.xposed.bridge.service

import android.os.ParcelFileDescriptor
import com.wmods.wppenhacer.xposed.bridge.WaeIIFace
import java.io.File
import java.io.FileNotFoundException

object HookBinder : WaeIIFace.Stub() {

    override fun openFile(path: String, create: Boolean): ParcelFileDescriptor? {
        val file = File(path)
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
        val file = File(path)
        return file.mkdirs()
    }

    override fun exists(path: String): Boolean {
        return File(path).exists()
    }

    override fun listFiles(path: String): List<File> {
        return File(path).listFiles()?.toList() ?: emptyList()
    }
}
