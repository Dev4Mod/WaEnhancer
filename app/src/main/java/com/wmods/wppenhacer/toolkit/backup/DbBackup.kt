package com.wmods.wppenhacer.toolkit.backup

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copias de seguridad de las bases de datos de WhatsApp (msgstore.db y wa.db).
 *
 * Cada copia es una carpeta `yyyyMMdd-HHmmss[-prerestore]` dentro de [backupRoot].
 * Restaurar sustituye los archivos por renombrado atómico: la base abierta por WhatsApp
 * queda apuntando al archivo viejo, así que no puede estropear la restaurada.
 */
class DbBackup(
    /** Carpeta de datos de la cuenta (la que contiene "databases/"). */
    private val dataDir: File,
    private val backupRoot: File,
    private val keep: Int = 8
) {
    class NoSpaceException : IOException("No hay espacio suficiente")

    data class Backup(val dir: File, val createdAt: Long, val sizeBytes: Long, val hasWaDb: Boolean, val safety: Boolean)

    private val msgstore get() = File(dataDir, "databases/msgstore.db")
    private val waDb get() = File(dataDir, "databases/wa.db")

    private val stampFormat get() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)

    fun create(safety: Boolean = false): Backup {
        if (!msgstore.exists()) throw IOException("No existe msgstore.db")
        val need = (msgstore.length() + (if (waDb.exists()) waDb.length() else 0L)) * 12 / 10
        backupRoot.mkdirs()
        if (backupRoot.usableSpace < need) throw NoSpaceException()

        checkpoint(msgstore)
        if (waDb.exists()) checkpoint(waDb)

        var stamp = stampFormat.format(Date()) + if (safety) "-prerestore" else ""
        var dir = File(backupRoot, stamp)
        var n = 1
        while (dir.exists()) dir = File(backupRoot, "$stamp-${n++}")
        if (!dir.mkdirs()) throw IOException("No se pudo crear ${dir.path}")
        try {
            msgstore.copyTo(File(dir, "msgstore.db"))
            if (waDb.exists()) waDb.copyTo(File(dir, "wa.db"))
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
        val made = describe(dir)!!
        prune()
        return made
    }

    fun list(): List<Backup> =
        (backupRoot.listFiles { f -> f.isDirectory } ?: emptyArray())
            .mapNotNull { describe(it) }
            .sortedWith(compareByDescending<Backup> { it.createdAt }.thenByDescending { it.dir.name })

    fun delete(b: Backup) {
        b.dir.deleteRecursively()
    }

    /**
     * Restaura [b]. Antes hace una copia de seguridad del estado actual (devuelta) por si
     * hay que deshacerlo. Después hay que reiniciar WhatsApp.
     */
    fun restore(b: Backup, includeWaDb: Boolean = true): Backup {
        val src = File(b.dir, "msgstore.db")
        if (!src.exists()) throw IOException("La copia no contiene msgstore.db")
        val safety = create(safety = true)
        replace(src, msgstore)
        val waSrc = File(b.dir, "wa.db")
        if (includeWaDb && waSrc.exists()) replace(waSrc, waDb)
        return safety
    }

    // ------------------------------------------------------------------ internos

    private fun replace(src: File, target: File) {
        val stage = File(target.parentFile, target.name + ".restore")
        try {
            src.copyTo(stage, overwrite = true)
            Files.move(stage.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Throwable) {
            stage.delete()
            throw e
        }
        // El WAL y los archivos de memoria compartida pertenecen a la base anterior.
        for (suffix in listOf("-wal", "-shm", "-journal")) File(target.path + suffix).delete()
    }

    private fun checkpoint(db: File) {
        try {
            SQLiteDatabase.openDatabase(db.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).close()
            }
        } catch (_: Exception) {
        }
    }

    private fun describe(dir: File): Backup? {
        val ms = File(dir, "msgstore.db")
        if (!ms.exists()) return null
        val wa = File(dir, "wa.db")
        val stamp = dir.name.take(15)
        val created = try { stampFormat.parse(stamp)?.time } catch (_: Exception) { null } ?: dir.lastModified()
        return Backup(dir, created, ms.length() + (if (wa.exists()) wa.length() else 0L), wa.exists(), "prerestore" in dir.name)
    }

    private fun prune() {
        list().drop(keep).forEach { delete(it) }
    }
}
