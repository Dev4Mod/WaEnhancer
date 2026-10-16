package com.wmods.wppenhacer.toolkit.export

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.toolkit.Host
import com.wmods.wppenhacer.toolkit.Monet
import com.wmods.wppenhacer.toolkit.Names
import com.wmods.wppenhacer.toolkit.S
import com.wmods.wppenhacer.toolkit.deleted.DeletedMessagesReader
import com.wmods.wppenhacer.toolkit.importer.ChatImporter
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.CancellationException

/** Herramienta "Exportar chat": elegir chat y formato, elegir dónde guardar y exportar. */
class ExportChatFlow(private val activity: Activity) {

    private class ChatRow(val info: ChatImporter.ChatInfo, val name: String, val key: String)

    private class Pending(
        val chat: ChatRow, val format: ChatExporter.Format, val media: Boolean, val names: Names, val flow: ExportChatFlow
    )

    companion object {
        const val REQUEST_SAVE = 0x5A18
        private var pending: Pending? = null

        /** Lo llama la función Toolkit cuando vuelve el selector de "guardar como". */
        fun onResult(resultCode: Int, data: Intent?) {
            val p = pending ?: return
            pending = null
            val uri = data?.data
            if (resultCode == Activity.RESULT_OK && uri != null) p.flow.run(p, uri)
        }
    }

    private fun dp(v: Int) = Host.dp(activity, v)
    private fun ui(block: () -> Unit) = activity.runOnUiThread { block() }
    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").trim()

    private val dbFile get() = File(Host.accountDataDir(activity), "databases/msgstore.db")

    fun start() {
        val progress = Dlg(activity).setTitle(S.get(S.export_menu)).setView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(16), dp(24), dp(16))
                addView(TextView(activity).apply {
                    text = S.get(S.export_loading)
                    setTextColor(Host.textColor(activity))
                })
                addView(ProgressBar(activity).apply { isIndeterminate = true })
            }
        )
        progress.show()
        Thread {
            try {
                val dataDir = Host.accountDataDir(activity)
                val importer = ChatImporter(dbFile, Host.rootWhatsAppDir(activity))
                val names = Names(
                    activity, importer.contactNames(File(dataDir, "databases/wa.db")),
                    DeletedMessagesReader(dbFile).phoneByLid()
                )
                val rows = importer.listChats().map {
                    val n = if (it.isGroup) (it.subject?.takeIf { s -> s.isNotBlank() } ?: it.rawJid.substringBefore('@'))
                    else names.of(it.rawJid)
                    ChatRow(it, n, normalize(n))
                }
                ui { progress.dismiss(); pickChat(rows, names) }
            } catch (e: Throwable) {
                Host.log(e)
                ui { progress.dismiss(); Host.toast(activity, S.get(S.export_error, e.message ?: e.javaClass.simpleName)) }
            }
        }.start()
    }

    // ------------------------------------------------------------------ elegir chat

    private fun pickChat(rows: List<ChatRow>, names: Names) {
        if (rows.isEmpty()) {
            Host.toast(activity, S.get(S.export_no_chats)); return
        }
        val shown = ArrayList(rows)
        val adapter = Monet.adapter(activity, shown.map { it.name })
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
        val dlg = Dlg(activity).setTitle(S.get(S.export_pick_chat)).setView(layout)
            .setNegativeButton(activity.getString(android.R.string.cancel), null)
        list.setOnItemClickListener { _, _, pos, _ -> dlg.dismiss(); options(shown[pos], names) }
        filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = normalize(s?.toString() ?: "")
                shown.clear()
                shown.addAll(rows.filter { q.isEmpty() || it.key.contains(q) })
                adapter.clear()
                adapter.addAll(shown.map { it.name })
            }
        })
        dlg.show()
    }

    // ------------------------------------------------------------------ opciones

    private fun options(chat: ChatRow, names: Names) {
        val color = Host.textColor(activity)
        val group = RadioGroup(activity)
        val formats = listOf(
            ChatExporter.Format.TXT to S.get(S.export_fmt_txt),
            ChatExporter.Format.HTML to S.get(S.export_fmt_html),
            ChatExporter.Format.CSV to S.get(S.export_fmt_csv)
        )
        formats.forEachIndexed { i, (_, label) ->
            group.addView(RadioButton(activity).apply {
                id = 1000 + i
                text = label
                setTextColor(color)
            })
        }
        group.check(1000)
        val media = CheckBox(activity).apply {
            text = S.get(S.export_include_media)
            isChecked = true
            setTextColor(color)
        }
        group.setOnCheckedChangeListener { _, id ->
            val csv = formats[id - 1000].first == ChatExporter.Format.CSV
            media.isEnabled = !csv
        }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
            addView(TextView(activity).apply {
                text = chat.name
                textSize = 16f
                setTextColor(color)
                setPadding(0, 0, 0, dp(8))
            })
            addView(group)
            addView(media)
        }
        Dlg(activity).setTitle(S.get(S.export_options_title)).setView(layout)
            .setPositiveButton(S.get(S.export_go)) { _, _ ->
                val format = formats[group.checkedRadioButtonId - 1000].first
                askLocation(Pending(chat, format, media.isChecked && format != ChatExporter.Format.CSV, names, this))
            }
            .setNegativeButton(activity.getString(android.R.string.cancel), null).show()
    }

    private fun askLocation(p: Pending) {
        pending = p
        val name = ChatExporter.suggestedName(p.chat.name, p.format, p.media)
        val mime = when {
            ChatExporter.isZip(p.format, p.media) -> "application/zip"
            p.format == ChatExporter.Format.HTML -> "text/html"
            p.format == ChatExporter.Format.CSV -> "text/csv"
            else -> "text/plain"
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, name)
        }
        try {
            activity.startActivityForResult(intent, REQUEST_SAVE)
        } catch (e: Throwable) {
            pending = null
            Host.log(e)
            Host.toast(activity, S.get(S.export_error, e.message ?: e.javaClass.simpleName))
        }
    }

    // ------------------------------------------------------------------ exportar

    private fun run(p: Pending, uri: Uri) {
        val text = TextView(activity).apply { setTextColor(Host.textColor(activity)) }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
            addView(text)
            addView(bar, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        text.text = S.get(S.export_working, 0, 0)
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val dlg = Dlg(activity).setTitle(S.get(S.export_menu)).setView(layout)
            .setNegativeButton(activity.getString(android.R.string.cancel)) { _, _ -> cancelled.set(true) }
        dlg.show()

        Thread {
            try {
                val labels = ChatExporter.Labels(
                    me = S.get(S.export_label_me),
                    deleted = S.get(S.export_label_deleted),
                    deletedWithText = S.get(S.export_label_deleted_text, "%1\$s"),
                    mediaOmitted = S.get(S.export_label_omitted),
                    fileAttached = S.get(S.export_label_attached)
                )
                val exporter = ChatExporter(dbFile, Host.rootWhatsAppDir(activity))
                val out = activity.contentResolver.openOutputStream(uri) ?: error("No se pudo abrir el destino")
                val stats = out.use {
                    exporter.export(
                        p.chat.info.rowId, p.chat.info.rawJid, p.chat.name, p.format, p.media, it, labels,
                        { jid -> p.names.of(jid) }, { cancelled.get() },
                        ChatExporter.Progress { done, total ->
                            ui {
                                text.text = S.get(S.export_working, done, total)
                                bar.progress = if (total > 0) done * 100 / total else 0
                            }
                        }
                    )
                }
                ui {
                    dlg.dismiss()
                    val note = if (stats.attachmentsMissing > 0) S.get(S.export_missing_note, stats.attachmentsMissing) else ""
                    Dlg(activity).setTitle(S.get(S.export_menu))
                        .setMessage(S.get(S.export_done, stats.messages, stats.attachments) + note)
                        .setPositiveButton(S.get(S.deleted_close), null).show()
                }
            } catch (e: CancellationException) {
                try { DocumentsContract.deleteDocument(activity.contentResolver, uri) } catch (_: Throwable) {}
                ui { dlg.dismiss(); Host.toast(activity, S.get(S.export_cancelled)) }
            } catch (e: Throwable) {
                Host.log(e)
                try { DocumentsContract.deleteDocument(activity.contentResolver, uri) } catch (_: Throwable) {}
                ui {
                    dlg.dismiss()
                    Host.toast(activity, S.get(S.export_error, e.message ?: e.javaClass.simpleName), android.widget.Toast.LENGTH_LONG)
                }
            }
        }.start()
    }
}
