package com.wmods.wppenhacer.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.topjohnwu.superuser.Shell
import com.wmods.wppenhacer.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern

object RootDiagnostics {

    private const val SEPOLICY_LOG_PATH = "/data/adb/lspd/log/verbose*.log"
    private const val HMA_CONFIG_GLOB = "/data/misc/hide_my_applist*/config.json"
    private const val HMA_ZYGISK_PATH = "/data/adb/modules/hma_oss_zygisk"
    private const val LSP_CONFIG_DB = "/data/adb/lspd/config/modules_config.db"

    private val SEPOLICY_PATTERN = Pattern.compile("(?i)sepolicy")
    private val ISSUE_PATTERN = Pattern.compile("(?i)error|invalid|failed")

    private val WHATSAPP_PACKAGES = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    private val WAENHANCER_PACKAGES = listOf(
        "com.wmods.wppenhacer",
        "com.wmods.wppenhacer.w4b"
    )

    enum class LogType { INFO, SUCCESS, WARNING, ERROR }

    data class LogEntry(val message: String, val type: LogType = LogType.INFO)

    interface Callback {
        fun onLog(entry: LogEntry)
    }

    fun runDiagnostics(context: Context, callback: Callback) {
        Shell.getShell { shell ->
            if (!shell.isRoot) {
                callback.onLog(
                    LogEntry(
                        context.getString(R.string.diag_root_denied),
                        LogType.ERROR
                    )
                )
                return@getShell
            }
            callback.onLog(LogEntry(context.getString(R.string.diag_root_granted), LogType.SUCCESS))

            checkSepolicy(context, callback)
            checkHideMyAppList(context, callback)
        }
    }

    private fun checkSepolicy(context: Context, callback: Callback) {
        callback.onLog(LogEntry(""))
        callback.onLog(LogEntry(context.getString(R.string.diag_sepolicy_checking)))

        val result = Shell.cmd("cat $SEPOLICY_LOG_PATH").exec()
        if (!result.isSuccess || result.out.isEmpty()) {
            callback.onLog(
                LogEntry(
                    context.getString(R.string.diag_sepolicy_not_found),
                    LogType.WARNING
                )
            )
            return
        }

        val foundLine = result.out.find { line ->
            SEPOLICY_PATTERN.matcher(line).find() && ISSUE_PATTERN.matcher(line).find()
        }

        if (foundLine == null) {
            callback.onLog(
                LogEntry(
                    context.getString(R.string.diag_sepolicy_no_issues),
                    LogType.SUCCESS
                )
            )
        } else {
            callback.onLog(LogEntry(context.getString(R.string.diag_sepolicy_found), LogType.ERROR))
            callback.onLog(LogEntry(foundLine.trim(), LogType.WARNING))
            callback.onLog(LogEntry(""))
            callback.onLog(
                LogEntry(
                    context.getString(R.string.diag_sepolicy_broken),
                    LogType.ERROR
                )
            )
        }
    }

    private fun checkHideMyAppList(context: Context, callback: Callback) {
        callback.onLog(LogEntry(""))
        callback.onLog(LogEntry(context.getString(R.string.diag_hma_checking)))

        if (!isHmaActive(context, callback)) {
            callback.onLog(
                LogEntry(
                    context.getString(R.string.diag_hma_not_active),
                    LogType.WARNING
                )
            )
            return
        }

        val result = Shell.cmd("cat $HMA_CONFIG_GLOB").exec()
        if (!result.isSuccess || result.out.isEmpty()) {
            callback.onLog(
                LogEntry(
                    context.getString(R.string.diag_hma_not_found),
                    LogType.WARNING
                )
            )
            return
        }

        val config = try {
            JSONObject(result.out.joinToString("\n"))
        } catch (e: Exception) {
            callback.onLog(
                LogEntry(
                    context.getString(R.string.diag_hma_invalid) + ": " + e.message,
                    LogType.ERROR
                )
            )
            return
        }

        val templates = config.optJSONObject("templates") ?: JSONObject()
        val scope = config.optJSONObject("scope") ?: JSONObject()

        val blockedTargets = WHATSAPP_PACKAGES.mapNotNull { pkg ->
            val scopeObj = scope.optJSONObject(pkg) ?: return@mapNotNull null
            if (isHmaBlockingWaEnhancer(scopeObj, templates)) pkg else null
        }

        if (blockedTargets.isEmpty()) {
            callback.onLog(
                LogEntry(
                    context.getString(R.string.diag_hma_no_blocks),
                    LogType.SUCCESS
                )
            )
        } else {
            callback.onLog(LogEntry(context.getString(R.string.diag_hma_blocked), LogType.ERROR))
            blockedTargets.forEach { callback.onLog(LogEntry("- $it", LogType.WARNING)) }
            callback.onLog(LogEntry(""))
            callback.onLog(LogEntry(context.getString(R.string.diag_hma_disable), LogType.ERROR))
        }
    }

    private fun isHmaActive(context: Context, callback: Callback): Boolean {
        // Zygisk variant
        val zygiskResult = Shell.cmd(
            "[ -d $HMA_ZYGISK_PATH ] && [ ! -f $HMA_ZYGISK_PATH/disable ] && echo active"
        ).exec()
        if (zygiskResult.out.any { it == "active" }) {
            callback.onLog(
                LogEntry(
                    context.getString(R.string.diag_hma_zygisk_active),
                    LogType.SUCCESS
                )
            )
            return true
        }

        // LSPosed variant
        val lspResult = Shell.cmd("[ -f $LSP_CONFIG_DB ] && echo exists").exec()
        if (lspResult.out.any { it == "exists" }) {
            val cacheFile = File(context.cacheDir, "hma_lsposed_config.db")
            val walFile = File(context.cacheDir, "hma_lsposed_config.db-wal")
            val shmFile = File(context.cacheDir, "hma_lsposed_config.db-shm")
            val journalFile = File(context.cacheDir, "hma_lsposed_config.db-journal")

            listOf(cacheFile, walFile, shmFile, journalFile).forEach { it.delete() }

            Shell.cmd(
                "cp $LSP_CONFIG_DB ${cacheFile.absolutePath} && " +
                        "cp $LSP_CONFIG_DB-wal ${walFile.absolutePath} 2>/dev/null; " +
                        "cp $LSP_CONFIG_DB-shm ${shmFile.absolutePath} 2>/dev/null; " +
                        "cp $LSP_CONFIG_DB-journal ${journalFile.absolutePath} 2>/dev/null; " +
                        "chmod 777 ${cacheFile.absolutePath} ${walFile.absolutePath} ${shmFile.absolutePath} ${journalFile.absolutePath} 2>/dev/null"
            ).exec()

            if (cacheFile.exists() && isHmaInLsposedDb(cacheFile)) {
                callback.onLog(
                    LogEntry(
                        context.getString(R.string.diag_hma_lsposed_active),
                        LogType.SUCCESS
                    )
                )
                return true
            }
        }

        return false
    }

    private fun isHmaInLsposedDb(dbFile: File): Boolean {
        var db: SQLiteDatabase? = null
        return try {
            db =
                SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery(
                "SELECT 1 FROM modules WHERE module_pkg_name LIKE ? AND enabled = 1 LIMIT 1",
                arrayOf("%hidemyapplist%")
            )
            val found = cursor.moveToFirst()
            cursor.close()
            found
        } catch (e: Exception) {
            false
        } finally {
            db?.close()
        }
    }

    private fun isHmaBlockingWaEnhancer(scopeObj: JSONObject, templates: JSONObject): Boolean {
        val useWhitelist = scopeObj.optBoolean("useWhitelist", false)

        val extraAppList = scopeObj.optJSONArray("extraAppList")?.toStringList() ?: emptyList()
        val extraOppositeAppList =
            scopeObj.optJSONArray("extraOppositeAppList")?.toStringList() ?: emptyList()

        if (useWhitelist) {
            if (extraOppositeAppList.any { it in WAENHANCER_PACKAGES }) return true
        } else {
            if (extraAppList.any { it in WAENHANCER_PACKAGES }) return true
        }

        val appliedTemplates =
            scopeObj.optJSONArray("applyTemplates")?.toStringList() ?: emptyList()
        for (templateName in appliedTemplates) {
            val template = templates.optJSONObject(templateName) ?: continue
            val templateIsWhitelist = template.optBoolean("isWhitelist", false)
            val appList = template.optJSONArray("appList")?.toStringList() ?: emptyList()

            if (!templateIsWhitelist && appList.any { it in WAENHANCER_PACKAGES }) {
                return true
            }
        }

        val appliedPresets = scopeObj.optJSONArray("applyPresets")?.toStringList() ?: emptyList()
        return appliedPresets.any { it.equals("xposed", ignoreCase = true) }
    }

    private fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            optString(i, null)?.let { list.add(it) }
        }
        return list
    }
}
