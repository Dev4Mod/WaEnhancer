package com.wmods.wppenhacer.utils

import android.content.Context
import android.content.pm.PackageManager
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
    private const val LSP_LOG_GLOB = "/data/adb/lspd/log/*.log"

    private val SEPOLICY_PATTERN = Pattern.compile("(?i)sepolicy")
    private val ISSUE_PATTERN = Pattern.compile("(?i)error|invalid|failed|not loaded properly")
    private val DENYLIST_LOG_PATTERN = Pattern.compile("\\[([^\\]]+)] is on the denylist")

    private val WHATSAPP_PACKAGES = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    private val WAENHANCER_PACKAGES = listOf(
        "com.wmods.wppenhacer",
        "com.wmods.wppenhacer.w4b"
    )

    enum class LogType { INFO, SUCCESS, WARNING, ERROR }

    enum class FixResult { FIXED, NOTHING_TO_FIX, FAILED }

    data class LogEntry(val message: String, val type: LogType = LogType.INFO)

    fun interface Callback {
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
            checkMagiskDenylist(context, callback)
            checkHideMyAppList(context, callback)
        }
    }

    /**
     * Removes WaEnhancer from the Hide My App List blocks applied to WhatsApp/Business.
     *
     * The HMA service keeps its config in memory and only reads the file on boot, and its binder
     * is only delivered to the HMA manager app, so the file is edited directly and the device
     * has to be restarted for the change to take effect.
     */
    fun fixHideMyAppList(context: Context, callback: Callback): FixResult {
        val configPath = Shell.cmd("ls -d $HMA_CONFIG_GLOB 2>/dev/null").exec()
            .out.firstOrNull { it.isNotBlank() }?.trim()
        if (configPath == null) {
            callback.onLog(LogEntry(context.getString(R.string.diag_hma_not_found), LogType.ERROR))
            return FixResult.FAILED
        }

        val original = Shell.cmd("cat $configPath").exec()
        val config = try {
            if (!original.isSuccess) throw IllegalStateException("cat failed")
            JSONObject(original.out.joinToString("\n"))
        } catch (e: Exception) {
            callback.onLog(
                LogEntry(
                    context.getString(R.string.diag_hma_invalid) + ": " + e.message,
                    LogType.ERROR
                )
            )
            return FixResult.FAILED
        }

        val templates = config.optJSONObject("templates") ?: JSONObject()
        val scope = config.optJSONObject("scope") ?: JSONObject()
        var changed = false
        var presetBlocked = false
        val ignoredForPresets = presetIgnoredPackages(config)

        val waPackages = getInstalledWaEnhancerPackages(context)

        getInstalledWhatsappPackages(context).forEach { pkg ->
            val scopeObj = scope.optJSONObject(pkg) ?: return@forEach
            if (!isHmaBlockingWaEnhancer(scopeObj, templates, ignoredForPresets, waPackages)) {
                return@forEach
            }
            val removed = removeBlocks(scopeObj, templates, ignoredForPresets, waPackages)
            val preset = scopeObj.optJSONArray("applyPresets")?.toStringList()
                ?.any { it.equals("xposed", ignoreCase = true) } == true
            presetBlocked = presetBlocked || preset
            if (removed || preset) {
                changed = true
                callback.onLog(LogEntry("- $pkg", LogType.SUCCESS))
            }
        }

        // The "xposed" preset hides every module, so exempt WaEnhancer from presets globally
        if (presetBlocked) {
            val ignored = JSONArray(ignoredForPresets)
            waPackages.filter { it !in ignoredForPresets }.forEach { ignored.put(it) }
            config.put("ignoredPackagesForPresets", ignored)
        }

        if (!changed) return FixResult.NOTHING_TO_FIX

        val backupPath = "$configPath.wae.bak"
        val tmpFile = File(context.cacheDir, "hma_config_fixed.json")
        try {
            tmpFile.writeText(config.toString())

            // Overwrite in place (cat >) to keep the owner, mode and SELinux context of the file
            val write = Shell.cmd(
                "cp -p $configPath $backupPath",
                "cat ${tmpFile.absolutePath} > $configPath"
            ).exec()
            val written = Shell.cmd("cat $configPath").exec()
            val valid = write.isSuccess && written.isSuccess && runCatching {
                JSONObject(written.out.joinToString("\n"))
            }.isSuccess
            if (!valid) {
                Shell.cmd("cat $backupPath > $configPath").exec()
                callback.onLog(LogEntry(context.getString(R.string.diag_hma_fix_failed), LogType.ERROR))
                return FixResult.FAILED
            }
        } catch (e: Exception) {
            callback.onLog(
                LogEntry(
                    context.getString(R.string.diag_hma_fix_failed) + ": " + e.message,
                    LogType.ERROR
                )
            )
            return FixResult.FAILED
        } finally {
            tmpFile.delete()
        }

        return FixResult.FIXED
    }

    fun rebootDevice() {
        Shell.cmd("am start -a android.intent.action.REBOOT || svc power reboot").submit()
    }

    /**
     * Makes [waPackages] visible to this WhatsApp scope entry. Returns true if it changed the
     * entry. Blocks caused by the "xposed" preset are handled globally by the caller.
     */
    private fun removeBlocks(
        scopeObj: JSONObject,
        templates: JSONObject,
        ignoredForPresets: List<String>,
        waPackages: List<String>
    ): Boolean {
        var changed = false
        val useWhitelist = scopeObj.optBoolean("useWhitelist", false)

        // The list that hides an app in the current mode
        val hidingKey = if (useWhitelist) "extraOppositeAppList" else "extraAppList"
        scopeObj.optJSONArray(hidingKey)?.let { list ->
            val kept = list.toStringList().filter { it !in waPackages }
            if (kept.size != list.length()) {
                scopeObj.put(hidingKey, JSONArray(kept))
                changed = true
            }
        }

        // Only unapply the templates from this app, other apps may still depend on them
        if (!useWhitelist) {
            val applied = scopeObj.optJSONArray("applyTemplates")?.toStringList() ?: emptyList()
            val keptTemplates = applied.filter { name ->
                val appList = templates.optJSONObject(name)?.optJSONArray("appList")
                    ?.toStringList() ?: return@filter true
                appList.none { it in waPackages }
            }
            if (keptTemplates.size != applied.size) {
                scopeObj.put("applyTemplates", JSONArray(keptTemplates))
                changed = true
            }
        }

        // Whitelist mode hides everything that is not listed, so list WaEnhancer explicitly
        val stillHidden = waPackages.filter {
            isHiddenFromApp(scopeObj, templates, ignoredForPresets, it)
        }
        if (useWhitelist && stillHidden.isNotEmpty()) {
            val list = scopeObj.optJSONArray("extraAppList") ?: JSONArray()
            val current = list.toStringList()
            stillHidden.filter { it !in current }.forEach { list.put(it) }
            scopeObj.put("extraAppList", list)
            changed = true
        }

        return changed
    }

    /**
     * Removes WhatsApp/Business from the Magisk DenyList and force-stops them, so the next launch
     * is no longer unmounted by Magisk and the Zygisk modules (LSPosed) can load again.
     */
    fun fixMagiskDenylist(context: Context, callback: Callback): FixResult {
        val listed = if (isMagiskInstalled()) getDenylistedWhatsappPackages(context) else null
        if (listed == null) {
            callback.onLog(LogEntry(context.getString(R.string.diag_denylist_fix_failed), LogType.ERROR))
            return FixResult.FAILED
        }
        if (listed.isEmpty()) return FixResult.NOTHING_TO_FIX

        var failed = false
        listed.forEach { pkg ->
            if (Shell.cmd("magisk --denylist rm $pkg").exec().isSuccess) {
                Shell.cmd("am force-stop $pkg").exec()
                callback.onLog(LogEntry("- $pkg", LogType.SUCCESS))
            } else {
                failed = true
            }
        }
        return if (failed) FixResult.FAILED else FixResult.FIXED
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

    private fun isMagiskInstalled(): Boolean =
        Shell.cmd("magisk -v 2>/dev/null").exec().out.any { it.contains("MAGISK", true) }

    /**
     * Installed WhatsApp packages that have any process on the Magisk DenyList, or null when the
     * list could not be read.
     */
    private fun getDenylistedWhatsappPackages(context: Context): List<String>? {
        val result = Shell.cmd("magisk --denylist ls").exec()
        if (!result.isSuccess) return null
        // Each line is "package|process"
        val listed = result.out.map { it.substringBefore('|').trim() }.toSet()
        return getInstalledWhatsappPackages(context).filter { it in listed }
    }

    private fun checkMagiskDenylist(context: Context, callback: Callback) {
        val installedTargets = getInstalledWhatsappPackages(context)
        if (installedTargets.isEmpty() || !isMagiskInstalled()) return

        callback.onLog(LogEntry(""))
        callback.onLog(LogEntry(context.getString(R.string.diag_denylist_checking)))

        // Magisk only logs "[process] is on the denylist" when the DenyList is enforced
        val logLines = Shell.cmd("cat $LSP_LOG_GLOB 2>/dev/null").exec().out.filter { line ->
            val matcher = DENYLIST_LOG_PATTERN.matcher(line)
            matcher.find() && matcher.group(1)!!.substringBefore(':') in installedTargets
        }

        val enforced = Shell.cmd("magisk --denylist status").exec().isSuccess
        val listed = getDenylistedWhatsappPackages(context)
        val blocked = if (listed != null) {
            if (enforced) listed else emptyList()
        } else {
            // Fall back to the log when the list cannot be read
            installedTargets.filter { pkg ->
                logLines.any { it.contains("[$pkg]") || it.contains("[$pkg:") }
            }
        }

        if (blocked.isEmpty()) {
            callback.onLog(
                LogEntry(context.getString(R.string.diag_denylist_no_blocks), LogType.SUCCESS)
            )
            return
        }

        callback.onLog(LogEntry(context.getString(R.string.diag_denylist_blocked), LogType.ERROR))
        blocked.forEach { callback.onLog(LogEntry("- $it", LogType.WARNING)) }
        logLines.lastOrNull()?.let { callback.onLog(LogEntry(it.trim(), LogType.WARNING)) }
        callback.onLog(LogEntry(""))
        callback.onLog(LogEntry(context.getString(R.string.diag_denylist_disable), LogType.ERROR))
    }

    private fun checkHideMyAppList(context: Context, callback: Callback) {
        callback.onLog(LogEntry(""))
        callback.onLog(LogEntry(context.getString(R.string.diag_hma_checking)))

        if (!isHmaActive(context, callback)) {
            callback.onLog(
                LogEntry(
                    context.getString(R.string.diag_hma_not_active),
                    LogType.SUCCESS
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

        val waPackages = getInstalledWaEnhancerPackages(context)
        val ignored = presetIgnoredPackages(config)
        val blockedTargets = getInstalledWhatsappPackages(context).mapNotNull { pkg ->
            val scopeObj = scope.optJSONObject(pkg) ?: return@mapNotNull null
            if (isHmaBlockingWaEnhancer(scopeObj, templates, ignored, waPackages)) pkg else null
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

    private fun presetIgnoredPackages(config: JSONObject): List<String> =
        config.optJSONArray("ignoredPackagesForPresets")?.toStringList() ?: emptyList()

    /**
     * Mirrors HMAService.shouldHide for a WhatsApp scope entry and one WaEnhancer package.
     */
    private fun isHiddenFromApp(
        scopeObj: JSONObject,
        templates: JSONObject,
        ignoredForPresets: List<String>,
        waPackage: String
    ): Boolean {
        val useWhitelist = scopeObj.optBoolean("useWhitelist", false)

        val extraAppList = scopeObj.optJSONArray("extraAppList")?.toStringList() ?: emptyList()
        val extraOppositeAppList =
            scopeObj.optJSONArray("extraOppositeAppList")?.toStringList() ?: emptyList()

        if (waPackage in extraAppList) return !useWhitelist
        if (waPackage in extraOppositeAppList) return useWhitelist

        val appliedTemplates =
            scopeObj.optJSONArray("applyTemplates")?.toStringList() ?: emptyList()
        for (templateName in appliedTemplates) {
            val template = templates.optJSONObject(templateName) ?: continue
            val appList = template.optJSONArray("appList")?.toStringList() ?: emptyList()
            if (waPackage in appList) return !useWhitelist
        }

        val appliedPresets = scopeObj.optJSONArray("applyPresets")?.toStringList() ?: emptyList()
        if (waPackage !in ignoredForPresets &&
            appliedPresets.any { it.equals("xposed", ignoreCase = true) }
        ) return true

        // Whitelist mode hides everything that was not explicitly unhidden
        return useWhitelist
    }

    private fun isHmaBlockingWaEnhancer(
        scopeObj: JSONObject,
        templates: JSONObject,
        ignoredForPresets: List<String>,
        waPackages: List<String>
    ) = waPackages.any { isHiddenFromApp(scopeObj, templates, ignoredForPresets, it) }

    private fun getInstalledWhatsappPackages(context: Context): List<String> =
        filterInstalled(context, WHATSAPP_PACKAGES)

    private fun getInstalledWaEnhancerPackages(context: Context): List<String> =
        filterInstalled(context, WAENHANCER_PACKAGES).ifEmpty { listOf(context.packageName) }

    private fun filterInstalled(context: Context, packages: List<String>): List<String> {
        val pm = context.packageManager
        return packages.filter { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            optString(i, null)?.let { list.add(it) }
        }
        return list
    }
}
