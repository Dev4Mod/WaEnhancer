package com.wmods.wppenhacer.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.util.Base64
import android.util.Xml
import com.topjohnwu.superuser.Shell
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader

object StickerRestore {

    private val PACKAGES = listOf(FeatureLoader.PACKAGE_WPP, FeatureLoader.PACKAGE_BUSINESS)
    private const val DB_RELATIVE_PATH = "databases/stickers.db"
    private const val MSGSTORE_RELATIVE_PATH = "databases/msgstore.db"
    private const val STICKERS_RELATIVE_PATH = "files/Stickers"
    private const val ACTIVE_ACCOUNT_PATH = "app_account_switching/active_account"
    private const val MIME_WEBP = "image/webp"
    private const val MIME_LOTTIE = "application/was"
    private const val DEFAULT_SIZE = 512

    private const val SELECT_DEFAULTS = "SELECT plain_file_hash FROM stickers"
    private const val SELECT_STARRED = "SELECT plaintext_hash FROM starred_stickers"
    private const val SELECT_MEDIA =
        "SELECT file_hash, enc_file_hash, media_key, direct_path, mime_type, width, height " +
                "FROM message_media WHERE file_hash IS NOT NULL AND media_key IS NOT NULL " +
                "AND direct_path IS NOT NULL"
    private const val INSERT_STARRED =
        "INSERT OR IGNORE INTO starred_stickers (plaintext_hash, timestamp, url, enc_hash, " +
                "direct_path, mimetype, media_key, file_size, width, height, is_first_party, " +
                "is_avatar, is_fun_sticker, is_lottie, premium) " +
                "VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, 0)"

    data class StickerTarget(
        val packageName: String,
        val userId: Int,
        val dir: String,
        val accountId: String?,
        val name: String?,
        val phone: String?,
        val isActive: Boolean
    ) {
        val dbPath: String get() = "$dir/$DB_RELATIVE_PATH"
        val msgstorePath: String get() = "$dir/$MSGSTORE_RELATIVE_PATH"
        val stickersPath: String get() = "$dir/$STICKERS_RELATIVE_PATH"
    }

    data class Preview(val total: Int, val starred: Int, val toRestore: Int)

    val isRootAvailable: Boolean get() = rootShell() != null

    fun findTargets(): List<StickerTarget> {
        val dataDirs = PACKAGES.joinToString(" ") { "/data/user/*/$it" }
        val command =
            "for d in $dataDirs; do [ -d \"\$d\" ] || continue; " +
                    "for f in \"\$d\"/$DB_RELATIVE_PATH \"\$d\"/accounts/*/$DB_RELATIVE_PATH; do " +
                    "[ -f \"\$f\" ] && echo \"\$f\"; done; done"
        val output = exec(command).output ?: return emptyList()
        val activeAccounts = HashMap<String, String?>()
        return output.lines()
            .map { it.trim() }
            .filter { it.endsWith(DB_RELATIVE_PATH) }
            .mapNotNull { path -> parseTarget(path, activeAccounts) }
            .sortedWith(compareBy({ it.packageName }, { it.userId }, { !it.isActive }, { it.accountId }))
    }

    private fun parseTarget(path: String, activeAccounts: MutableMap<String, String?>): StickerTarget? {
        val dir = path.removeSuffix("/$DB_RELATIVE_PATH")
        val segments = dir.removePrefix("/data/user/").split("/")
        val userId = segments.getOrNull(0)?.toIntOrNull() ?: return null
        val packageName = segments.getOrNull(1)?.takeIf { it in PACKAGES } ?: return null
        val accountId = if (segments.getOrNull(2) == "accounts") segments.getOrNull(3) else null
        val baseDir = "/data/user/$userId/$packageName"
        val activeAccount = activeAccounts.getOrPut(baseDir) {
            exec("cat ${quote("$baseDir/$ACTIVE_ACCOUNT_PATH")}").output
                ?.trim()?.ifEmpty { null }
        }
        val prefs = readAccountPrefs(dir, packageName)
        return StickerTarget(
            packageName = packageName,
            userId = userId,
            dir = dir,
            accountId = accountId,
            name = prefs["push_name"]?.ifBlank { null },
            phone = resolvePhone(prefs),
            isActive = if (activeAccount == null) accountId == null else accountId == activeAccount
        )
    }

    private fun readAccountPrefs(dir: String, packageName: String): Map<String, String> {
        val values = HashMap<String, String>()
        readPrefs("$dir/shared_prefs/startup_prefs.xml", setOf("push_name"), values)
        readPrefs(
            "$dir/shared_prefs/${packageName}_preferences_light.xml",
            setOf("registration_jid", "cc", "ph"),
            values
        )
        return values
    }

    private fun readPrefs(file: String, keys: Set<String>, into: MutableMap<String, String>) {
        val xml = exec("cat ${quote(file)}").output ?: return
        runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "string") continue
                val key = parser.getAttributeValue(null, "name") ?: continue
                if (key in keys) into[key] = parser.nextText()
            }
        }
    }

    private fun resolvePhone(prefs: Map<String, String>): String? {
        prefs["registration_jid"]?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?.let { return "+$it" }
        val cc = prefs["cc"].orEmpty()
        val ph = prefs["ph"].orEmpty()
        return if (cc.isNotBlank() && ph.isNotBlank()) "+$cc $ph" else null
    }

    private class StickerFile(
        val hash: String,
        val isLottie: Boolean,
        val size: Long,
        val modifiedMs: Long,
        val width: Int,
        val height: Int
    )

    private class MediaInfo(
        val encHash: String?,
        val mediaKey: String,
        val directPath: String,
        val mimeType: String?,
        val width: Int,
        val height: Int
    )

    fun preview(context: Context, target: StickerTarget): Preview {
        val files = listStickerFiles(target)
        val db = copyToCache(context, target.dbPath, "stickers.db")
        return openDatabase(db).use { database ->
            val defaults = database.hashes(SELECT_DEFAULTS)
            val starred = database.hashes(SELECT_STARRED)
            val candidates = files.filterNot { it.hash in defaults }
            val alreadyStarred = candidates.count { it.hash in starred }
            Preview(candidates.size, alreadyStarred, candidates.size - alreadyStarred)
        }.also { clearCache(context) }
    }

    fun restore(context: Context, target: StickerTarget): Int {
        runRoot("am force-stop --user ${target.userId} ${target.packageName}")
        val files = listStickerFiles(target)
        val db = copyToCache(context, target.dbPath, "stickers.db")
        val restored = openDatabase(db).use { database ->
            val skip = database.hashes(SELECT_DEFAULTS) + database.hashes(SELECT_STARRED)
            val pending = files.filterNot { it.hash in skip }
            if (pending.isEmpty()) return@use 0
            val media = readMediaInfo(context, target, pending.mapTo(HashSet()) { it.hash })
            database.beginTransaction()
            val count = try {
                database.compileStatement(INSERT_STARRED).use { statement ->
                    pending.sumOf { file -> insert(statement, file, media[file.hash]) }
                }.also { database.setTransactionSuccessful() }
            } finally {
                database.endTransaction()
            }
            database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            database.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
            count
        }
        if (restored > 0) {
            val source = quote(target.dbPath)
            val backup = quote(target.dbPath + ".wae_bak")
            runRoot(
                "am force-stop --user ${target.userId} ${target.packageName}; " +
                        "{ [ -f $backup ] || cp -p $source $backup; } && " +
                        "cat ${quote(db.absolutePath)} > $source && " +
                        "rm -f ${quote(target.dbPath + "-wal")} ${quote(target.dbPath + "-shm")}"
            )
        }
        clearCache(context)
        return restored
    }

    private fun insert(statement: SQLiteStatement, file: StickerFile, media: MediaInfo?): Int {
        statement.clearBindings()
        statement.bindString(1, file.hash)
        statement.bindLong(2, file.modifiedMs)
        statement.bindNullable(3, media?.encHash)
        statement.bindNullable(4, media?.directPath)
        statement.bindString(5, media?.mimeType ?: if (file.isLottie) MIME_LOTTIE else MIME_WEBP)
        statement.bindNullable(6, media?.mediaKey)
        statement.bindLong(7, file.size)
        statement.bindLong(8, (media?.width?.takeIf { it > 0 } ?: file.width).toLong())
        statement.bindLong(9, (media?.height?.takeIf { it > 0 } ?: file.height).toLong())
        statement.bindLong(10, if (file.isLottie) 1 else 0)
        return statement.executeUpdateDelete()
    }

    private fun SQLiteStatement.bindNullable(index: Int, value: String?) {
        if (value == null) bindNull(index) else bindString(index, value)
    }

    private fun listStickerFiles(target: StickerTarget): List<StickerFile> {
        val command = "(cd ${quote(target.stickersPath)} 2>/dev/null && " +
                "for f in *.webp *.was; do [ -f \"\$f\" ] || continue; " +
                "echo \"\$f|\$(stat -c %s:%Y \"./\$f\")|\$(head -c 30 \"./\$f\" | base64)\"; done)"
        val output = exec(command).output ?: return emptyList()
        return output.lines().mapNotNull { line -> parseStickerFile(line.trim()) }
    }

    private fun parseStickerFile(line: String): StickerFile? {
        val parts = line.split("|")
        if (parts.size < 2) return null
        val name = parts[0]
        val isLottie = name.endsWith(".was")
        val hash = name.substringBeforeLast(".").replace('-', '/')
        if (hash.length != 44) return null
        val size = parts[1].substringBefore(":").toLongOrNull() ?: return null
        val modified = parts[1].substringAfter(":").toLongOrNull() ?: 0L
        val header = parts.getOrNull(2)?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
        val dimensions = if (isLottie) null else header?.let(::webpDimensions)
        return StickerFile(
            hash = hash,
            isLottie = isLottie,
            size = size,
            modifiedMs = modified * 1000,
            width = dimensions?.first ?: DEFAULT_SIZE,
            height = dimensions?.second ?: DEFAULT_SIZE
        )
    }

    private fun webpDimensions(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 30 || String(b, 0, 4) != "RIFF" || String(b, 8, 4) != "WEBP") return null
        fun u8(i: Int) = b[i].toInt() and 0xff
        fun le24(i: Int) = u8(i) or (u8(i + 1) shl 8) or (u8(i + 2) shl 16)
        return when (String(b, 12, 4)) {
            "VP8X" -> (le24(24) + 1) to (le24(27) + 1)
            "VP8 " -> ((u8(26) or (u8(27) shl 8)) and 0x3fff) to ((u8(28) or (u8(29) shl 8)) and 0x3fff)
            "VP8L" -> {
                val bits = le24(21) or (u8(24) shl 24)
                ((bits and 0x3fff) + 1) to (((bits shr 14) and 0x3fff) + 1)
            }

            else -> null
        }
    }

    private fun readMediaInfo(
        context: Context,
        target: StickerTarget,
        hashes: Set<String>
    ): Map<String, MediaInfo> = runCatching {
        val db = copyToCache(context, target.msgstorePath, "msgstore.db")
        val result = HashMap<String, MediaInfo>()
        openDatabase(db).use { database ->
            database.rawQuery(SELECT_MEDIA, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val hash = cursor.getString(0)
                    if (hash !in hashes || hash in result) continue
                    result[hash] = MediaInfo(
                        encHash = cursor.getString(1),
                        mediaKey = Base64.encodeToString(cursor.getBlob(2), Base64.NO_WRAP),
                        directPath = cursor.getString(3),
                        mimeType = cursor.getString(4),
                        width = cursor.getInt(5),
                        height = cursor.getInt(6)
                    )
                }
            }
        }
        result
    }.getOrDefault(emptyMap())

    private fun copyToCache(context: Context, source: String, name: String): File {
        val workDir = File(context.cacheDir, "sticker_restore").apply { mkdirs() }
        val db = File(workDir, name).apply { delete(); createNewFile() }
        val wal = File(workDir, "$name-wal").apply { delete(); createNewFile() }
        File(workDir, "$name-shm").delete()
        runRoot(
            "cat ${quote(source)} > ${quote(db.absolutePath)} && " +
                    "if [ -f ${quote("$source-wal")} ]; then " +
                    "cat ${quote("$source-wal")} > ${quote(wal.absolutePath)}; fi"
        )
        if (wal.length() == 0L) wal.delete()
        if (db.length() == 0L) throw IllegalStateException("Unable to copy $source")
        return db
    }

    private fun clearCache(context: Context) {
        File(context.cacheDir, "sticker_restore").deleteRecursively()
    }

    private fun openDatabase(file: File): SQLiteDatabase = SQLiteDatabase.openDatabase(
        file.absolutePath,
        null,
        SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    )

    private fun SQLiteDatabase.hashes(sql: String): Set<String> =
        rawQuery(sql, null).use { cursor ->
            HashSet<String>().apply { while (cursor.moveToNext()) cursor.getString(0)?.let(::add) }
        }

    private fun runRoot(command: String) {
        val result = exec(command)
        if (result.code != 0) {
            throw IllegalStateException(result.error ?: "Root command failed (${result.code})")
        }
    }

    private class ShellOutput(val code: Int, val output: String?, val error: String?)

    private var shell: Shell? = null

    @Synchronized
    private fun rootShell(): Shell? {
        shell?.takeIf { it.isAlive }?.let { return it }
        return runCatching { Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).build() }
            .getOrNull()
            ?.takeIf { it.isRoot }
            .also { shell = it }
    }

    private fun exec(command: String): ShellOutput {
        val shell = rootShell() ?: return ShellOutput(-1, null, "Root access denied or not available")
        val result = shell.newJob().add(command).to(ArrayList(), ArrayList()).exec()
        return ShellOutput(
            result.code,
            result.out.joinToString("\n").trim().ifEmpty { null },
            result.err.joinToString("\n").trim().ifEmpty { null }
        )
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
