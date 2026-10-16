package com.wmods.wppenhacer.toolkit.backup

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.toolkit.Host
import com.wmods.wppenhacer.toolkit.Monet
import com.wmods.wppenhacer.toolkit.Names
import com.wmods.wppenhacer.toolkit.S
import com.wmods.wppenhacer.toolkit.deleted.DeletedMessagesReader
import com.wmods.wppenhacer.toolkit.importer.ChatImporter
import java.io.File
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Herramienta "Recuperar chat": elige una copia antigua, un chat de ella y trae los mensajes que faltan. */
class RecoverChatFlow(private val activity: Activity) {

    private class Source(val label: String, val file: File)
    private class Row(val chat: ChatRecovery.BackupChat, val name: String, val key: String)

    private fun dp(v: Int) = Host.dp(activity, v)
    private fun ui(block: () -> Unit) = activity.runOnUiThread { block() }
    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").trim()

    private val dataDir get() = Host.accountDataDir(activity)
    private val currentDb get() = File(dataDir, "databases/msgstore.db")
    private val recovery get() = ChatRecovery(currentDb, Host.rootWhatsAppDir(activity))

    fun start() {
        val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val sources = ArrayList<Source>()
        try {
            DbBackup(dataDir, Host.backupRoot(activity)).list().forEach {
                sources += Source(df.format(Date(it.createdAt)) + if (it.safety) "  ·  " + S.get(S.backup_safety_tag) else "", File(it.dir, "msgstore.db"))
            }
        } catch (_: Throwable) {
        }
        // Copias que hace el importador antes de importar un chat.
        File(dataDir, "databases").listFiles { f -> f.name.startsWith("msgstore.db.pre-import-") && f.name.endsWith(".bak") }
            ?.sortedByDescending { it.name }?.forEach {
                sources += Source(df.format(Date(it.lastModified())) + "  ·  " + S.get(S.recover_source_preimport), it)
            }
        if (sources.isEmpty()) {
            Dlg(activity).setTitle(S.get(S.recover_menu)).setMessage(S.get(S.recover_no_sources))
                .setPositiveButton(S.get(S.deleted_close), null).show()
            return
        }
        val list = ListView(activity).apply {
            adapter = Monet.adapter(activity, sources.map { it.label })
        }
        val dlg = Dlg(activity).setTitle(S.get(S.recover_pick_source)).setView(
            LinearLayout(activity).apply {
                setPadding(dp(20), dp(8), dp(20), dp(8))
                addView(list, ViewGroup.LayoutParams.MATCH_PARENT, dp(320))
            }
        ).setNegativeButton(activity.getString(android.R.string.cancel), null)
        list.setOnItemClickListener { _, _, pos, _ -> dlg.dismiss(); loadChats(sources[pos]) }
        dlg.show()
    }

    // ------------------------------------------------------------------ chats de la copia

    private fun loadChats(src: Source) {
        val progress = busy(S.get(S.recover_loading))
        progress.show()
        Thread {
            try {
                val importer = ChatImporter(currentDb, Host.rootWhatsAppDir(activity))
                val lids = HashMap<String, String>()
                lids.putAll(DeletedMessagesReader(currentDb).phoneByLid())
                try { lids.putAll(DeletedMessagesReader(src.file).phoneByLid()) } catch (_: Throwable) {}
                val names = Names(activity, importer.contactNames(File(dataDir, "databases/wa.db")), lids)
                val rows = recovery.listChats(src.file).map {
                    val n = if (it.isGroup) (it.subject?.takeIf { s -> s.isNotBlank() } ?: it.rawJid.substringBefore('@'))
                    else names.of(it.rawJid)
                    Row(it, n, normalize(n))
                }
                ui { progress.dismiss(); pickChat(src, rows) }
            } catch (e: Throwable) {
                Host.log(e)
                ui { progress.dismiss(); Host.toast(activity, S.get(S.recover_error, e.message ?: e.javaClass.simpleName)) }
            }
        }.start()
    }

    private fun busy(text: String) = Dlg(activity).setTitle(S.get(S.recover_menu)).setView(
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
            addView(TextView(activity).apply { this.text = text; setTextColor(Host.textColor(activity)) })
            addView(ProgressBar(activity).apply { isIndeterminate = true })
        }
    )

    private fun pickChat(src: Source, rows: List<Row>) {
        if (rows.isEmpty()) {
            Host.toast(activity, S.get(S.export_no_chats)); return
        }
        val shown = ArrayList(rows)
        fun label(r: Row) = S.get(S.recover_chat_item, r.name, r.chat.messages)
        val adapter = Monet.adapter(activity, shown.map { label(it) })
        val filter = EditText(activity).apply {
            hint = S.get(S.export_search_hint)
            setSingleLine()
            setTextColor(Host.textColor(activity))
        }
        val list = ListView(activity).apply { this.adapter = adapter }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            addView(filter)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380)))
        }
        val dlg = Dlg(activity).setTitle(S.get(S.recover_pick_chat)).setView(layout)
            .setNegativeButton(activity.getString(android.R.string.cancel), null)
        list.setOnItemClickListener { _, _, pos, _ -> dlg.dismiss(); preview(src, shown[pos]) }
        filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = normalize(s?.toString() ?: "")
                shown.clear()
                shown.addAll(rows.filter { q.isEmpty() || it.key.contains(q) })
                adapter.clear()
                adapter.addAll(shown.map { label(it) })
            }
        })
        dlg.show()
    }

    // ------------------------------------------------------------------ confirmar y recuperar

    private fun preview(src: Source, row: Row) {
        val progress = busy(S.get(S.recover_loading))
        progress.show()
        Thread {
            try {
                val p = recovery.preview(src.file, row.chat.rowId)
                ui {
                    progress.dismiss()
                    if (p.toRestore <= 0) {
                        Dlg(activity).setTitle(S.get(S.recover_menu)).setMessage(S.get(S.recover_nothing))
                            .setPositiveButton(S.get(S.deleted_close), null).show()
                    } else {
                        Dlg(activity).setTitle(S.get(S.recover_menu))
                            .setMessage(S.get(S.recover_confirm, p.toRestore, row.name, p.present))
                            .setPositiveButton(S.get(S.recover_go)) { _, _ -> run(src, row) }
                            .setNegativeButton(activity.getString(android.R.string.cancel), null).show()
                    }
                }
            } catch (e: Throwable) {
                Host.log(e)
                ui { progress.dismiss(); Host.toast(activity, S.get(S.recover_error, e.message ?: e.javaClass.simpleName)) }
            }
        }.start()
    }

    private fun run(src: Source, row: Row) {
        val text = TextView(activity).apply { setTextColor(Host.textColor(activity)) }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        text.text = S.get(S.recover_working, 0, 0)
        val cancelled = AtomicBoolean(false)
        val dlg = Dlg(activity).setTitle(S.get(S.recover_menu)).setView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(16), dp(24), dp(16))
                addView(text)
                addView(bar, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        ).setNegativeButton(activity.getString(android.R.string.cancel)) { _, _ -> cancelled.set(true) }
        dlg.show()
        Thread {
            try {
                // Copia previa del estado actual, por si hay que deshacer.
                DbBackup(dataDir, Host.backupRoot(activity)).create(safety = true)
                val r = recovery.recover(src.file, row.chat.rowId, { done, total ->
                    ui {
                        text.text = S.get(S.recover_working, done, total)
                        bar.progress = if (total > 0) done * 100 / total else 0
                    }
                }, { cancelled.get() })
                ui {
                    dlg.dismiss()
                    val msg = S.get(S.recover_done, r.restored) +
                            (if (r.chatCreated) S.get(S.recover_done_created) else "") +
                            (if (r.mediaMissing > 0) S.get(S.recover_done_media, r.mediaMissing) else "") +
                            S.get(S.recover_restart_hint)
                    Dlg(activity).setTitle(S.get(S.recover_menu)).setMessage(msg)
                        .setPositiveButton(S.get(S.update_restart)) { _, _ -> Host.restart(activity) }
                        .setNegativeButton(S.get(S.deleted_close), null).show()
                }
            } catch (e: CancellationException) {
                ui { dlg.dismiss(); Host.toast(activity, S.get(S.export_cancelled)) }
            } catch (e: Throwable) {
                Host.log(e)
                val msg = if (e is DbBackup.NoSpaceException) S.get(S.backup_no_space) else e.message ?: e.javaClass.simpleName
                ui { dlg.dismiss(); Host.toast(activity, S.get(S.recover_error, msg), android.widget.Toast.LENGTH_LONG) }
            }
        }.start()
    }
}
