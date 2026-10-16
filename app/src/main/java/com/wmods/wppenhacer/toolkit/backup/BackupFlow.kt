package com.wmods.wppenhacer.toolkit.backup

import android.app.Activity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.toolkit.Host
import com.wmods.wppenhacer.toolkit.Monet
import com.wmods.wppenhacer.toolkit.S
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Pantalla "Copias de seguridad": crear, restaurar y borrar copias de las bases de datos. */
class BackupFlow(private val activity: Activity) {

    private var current: Dlg? = null

    private fun dp(v: Int) = Host.dp(activity, v)
    private fun ui(block: () -> Unit) = activity.runOnUiThread { block() }

    private val backupRoot: File get() = Host.backupRoot(activity)

    private fun store() = DbBackup(Host.accountDataDir(activity), backupRoot)

    private val df get() = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    private fun size(bytes: Long): String =
        if (bytes >= 1 shl 20) "%.1f MB".format(bytes / 1048576.0) else "${bytes / 1024} KB"

    fun start() {
        val backups = try { store().list() } catch (_: Throwable) { emptyList() }
        val color = Host.textColor(activity)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        layout.addView(Button(activity).apply {
            text = S.get(S.backup_create)
            setOnClickListener { create() }
        })
        layout.addView(TextView(activity).apply {
            text = S.get(S.backup_location, backupRoot.path.substringAfter("/Android/", backupRoot.path))
            textSize = 11f
            setTextColor(color)
            alpha = 0.6f
            setPadding(0, dp(4), 0, dp(8))
        })
        if (backups.isEmpty()) {
            layout.addView(TextView(activity).apply {
                text = S.get(S.backup_empty)
                setTextColor(color)
                setPadding(0, dp(8), 0, dp(8))
            })
        } else {
            val labels = backups.map {
                df.format(Date(it.createdAt)) + "  ·  " + size(it.sizeBytes) +
                        if (it.safety) "  ·  " + S.get(S.backup_safety_tag) else ""
            }
            layout.addView(
                ListView(activity).apply {
                    adapter = Monet.adapter(activity, labels)
                    setOnItemClickListener { _, _, pos, _ -> actions(backups[pos]) }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300))
            )
        }
        current = Dlg(activity).setTitle(S.get(S.backup_menu)).setView(layout)
            .setPositiveButton(S.get(S.deleted_close), null).also { it.show() }
    }

    private fun reload() {
        current?.dismiss()
        start()
    }

    private fun create() {
        Host.toast(activity, S.get(S.backup_creating))
        Thread {
            try {
                val b = store().create()
                ui { Host.toast(activity, S.get(S.backup_created, size(b.sizeBytes))); reload() }
            } catch (e: Throwable) {
                fail(e)
            }
        }.start()
    }

    private fun actions(b: DbBackup.Backup) {
        val date = df.format(Date(b.createdAt))
        Dlg(activity).setTitle(S.get(S.backup_actions_title, date))
            .setMessage(date + "  ·  " + size(b.sizeBytes))
            .setPositiveButton(S.get(S.backup_restore)) { _, _ -> confirmRestore(b, date) }
            .setNegativeButton(S.get(S.backup_delete)) { _, _ ->
                Thread {
                    try {
                        store().delete(b)
                        ui { Host.toast(activity, S.get(S.backup_deleted)); reload() }
                    } catch (e: Throwable) {
                        fail(e)
                    }
                }.start()
            }.show()
    }

    private fun confirmRestore(b: DbBackup.Backup, date: String) {
        Dlg(activity).setTitle(S.get(S.backup_restore))
            .setMessage(S.get(S.backup_restore_confirm, date))
            .setPositiveButton(S.get(S.backup_restore)) { _, _ ->
                Thread {
                    try {
                        store().restore(b)
                        ui {
                            Host.toast(activity, S.get(S.backup_restored), android.widget.Toast.LENGTH_LONG)
                            Host.restart(activity)
                        }
                    } catch (e: Throwable) {
                        fail(e)
                    }
                }.start()
            }
            .setNegativeButton(activity.getString(android.R.string.cancel), null).show()
    }

    private fun fail(e: Throwable) {
        Host.log(e)
        val msg = if (e is DbBackup.NoSpaceException) S.get(S.backup_no_space) else e.message ?: e.javaClass.simpleName
        ui { Host.toast(activity, S.get(S.backup_error, msg), android.widget.Toast.LENGTH_LONG) }
    }
}
