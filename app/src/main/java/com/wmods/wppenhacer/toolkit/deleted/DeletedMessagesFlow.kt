package com.wmods.wppenhacer.toolkit.deleted

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.toolkit.Host
import com.wmods.wppenhacer.toolkit.Monet
import com.wmods.wppenhacer.toolkit.Names
import com.wmods.wppenhacer.toolkit.S
import com.wmods.wppenhacer.toolkit.importer.ChatImporter
import java.io.File
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale

/** Pantalla "Mensajes borrados": tarjetas tabuladas (quién, grupo/chat, texto) con filtro y registro de texto opcional. */
class DeletedMessagesFlow(private val activity: Activity) {

    private class Row(
        val fields: List<Pair<String, String>>, val dateLine: String, val searchKey: String,
        val jid: String, val messageRowId: Long, val sortId: Long?
    )

    private var current: Dlg? = null

    private fun str(id: Int, vararg args: Any): String = S.get(id, *args)
    private fun dp(v: Int) = Host.dp(activity, v)
    private fun ui(block: () -> Unit) = activity.runOnUiThread { block() }

    fun start() {
        val progress = Dlg(activity).setTitle(str(S.deleted_menu)).setView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(16), dp(24), dp(16))
                addView(TextView(activity).apply {
                    text = str(S.deleted_loading)
                    setTextColor(Host.textColor(activity))
                })
                addView(
                    ProgressBar(activity).apply { isIndeterminate = true },
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        )
        progress.show()

        Thread {
            try {
                val dataDir = Host.accountDataDir(activity)
                val reader = DeletedMessagesReader(File(dataDir, "databases/msgstore.db"))
                val result = reader.list()
                val resolver = Names(
                    activity,
                    ChatImporter(File(dataDir, "databases/msgstore.db"), Host.rootWhatsAppDir(activity))
                        .contactNames(File(dataDir, "databases/wa.db")),
                    reader.phoneByLid()
                )
                val lids = reader.phoneByLid()
                val rows = result.entries.map { describe(it, resolver, lids) }
                val logStatus = try { DeletedLog(File(dataDir, "databases/msgstore.db")).status() } catch (_: Throwable) { null }
                ui { progress.dismiss(); show(rows, result.source, logStatus) }
            } catch (e: Throwable) {
                Host.log(e)
                ui { progress.dismiss(); Host.toast(activity, e.message ?: e.javaClass.simpleName) }
            }
        }.start()
    }

    // ------------------------------------------------------------------ una fila

    private fun describe(e: DeletedMessagesReader.Entry, names: Names, lids: Map<String, String>): Row {
        val chatName = if (e.chatIsGroup) {
            e.chatSubject?.takeIf { it.isNotBlank() } ?: e.chatJid.substringBefore('@')
        } else names.of(e.chatJid)

        val fields = ArrayList<Pair<String, String>>()
        val keys = StringBuilder(chatName)
        val who: String
        var author: String? = null
        when {
            e.deletedByAdmin -> {
                val admin = names.of(e.adminJid)
                keys.append(' ').append(admin)
                who = str(S.deleted_who_admin, admin)
                author = if (e.fromMe) str(S.deleted_you) else e.authorJid?.let { names.of(it) }
            }
            e.fromMe -> who = str(S.deleted_you)
            e.authorJid != null -> who = names.of(e.authorJid)
            else -> who = "?"
        }
        author?.let { keys.append(' ').append(it) }
        if (!e.deletedByAdmin && e.authorJid != null) keys.append(' ').append(who)

        fields += str(S.deleted_col_who) to who
        fields += (if (e.chatIsGroup) str(S.deleted_col_group) else str(S.deleted_col_chat)) to chatName
        if (author != null) fields += str(S.deleted_col_author) to author
        val text = e.text?.takeIf { it.isNotBlank() }
        fields += str(S.deleted_col_text) to (text ?: str(S.deleted_text_unavailable))
        if (text != null) keys.append(' ').append(text)

        val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val parts = ArrayList<String>()
        if (e.sentAt > 0) parts += str(S.deleted_col_sent) + " " + df.format(Date(e.sentAt))
        if (e.deletedAt != null) parts += str(S.deleted_col_date) + " " + df.format(Date(e.deletedAt))
        return Row(
            fields, parts.joinToString("  ·  "), normalize(keys.toString()),
            lids[e.chatJid] ?: e.chatJid, e.messageRowId, e.sortId
        )
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").trim()

    // ------------------------------------------------------------------ lista

    private fun card(row: Row): View {
        val color = Host.textColor(activity)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }
        val table = TableLayout(activity).apply { isShrinkAllColumns = false; isStretchAllColumns = false }
        for ((label, value) in row.fields) {
            val tr = TableRow(activity)
            tr.addView(TextView(activity).apply {
                text = label
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color)
                alpha = 0.7f
                setPadding(0, dp(2), dp(12), dp(2))
                minWidth = dp(92)
            })
            tr.addView(TextView(activity).apply {
                text = value
                textSize = 14f
                setTextColor(color)
                setPadding(0, dp(2), 0, dp(2))
                setTextIsSelectable(false)
            }, TableRow.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            table.addView(tr, TableLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        table.setColumnShrinkable(1, true)
        table.setColumnStretchable(1, true)
        box.addView(table)
        if (row.dateLine.isNotEmpty()) {
            box.addView(TextView(activity).apply {
                text = row.dateLine
                textSize = 11f
                setTextColor(color)
                alpha = 0.6f
                setPadding(0, dp(4), 0, 0)
            })
        }
        return box
    }

    private fun show(rows: List<Row>, source: DeletedMessagesReader.Source, log: DeletedLog.Status?) {
        val color = Host.textColor(activity)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        // Estado del registro de texto
        if (log != null) {
            layout.addView(TextView(activity).apply {
                text = if (log.enabled) str(S.log_banner_on, log.messages) else str(S.log_banner_off)
                textSize = 12f
                setTextColor(color)
            })
            layout.addView(Button(activity).apply {
                text = if (log.enabled) str(S.log_disable) else str(S.log_enable)
                setOnClickListener { if (log.enabled) disableLog() else confirmEnableLog() }
            })
        }
        if (source == DeletedMessagesReader.Source.MESSAGE_TYPE) {
            layout.addView(TextView(activity).apply {
                text = str(S.deleted_note_fallback)
                textSize = 12f
                setTextColor(color)
                setPadding(0, 0, 0, dp(8))
            })
        }
        if (rows.isEmpty()) {
            layout.addView(TextView(activity).apply {
                text = str(S.deleted_empty)
                setTextColor(color)
                setPadding(0, dp(8), 0, dp(8))
            })
            current = Dlg(activity).setTitle(str(S.deleted_title)).setView(layout)
                .setPositiveButton(str(S.deleted_close), null).also { it.show() }
            return
        }
        val filter = EditText(activity).apply {
            hint = str(S.deleted_filter_hint)
            setSingleLine()
            setTextColor(color)
        }
        val shown = ArrayList(rows)
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = card(shown[position])
        }
        layout.addView(TextView(activity).apply {
            text = str(S.deleted_tap_hint)
            textSize = 11f
            setTextColor(Monet.palette(activity).onSurfaceVariant)
            setPadding(0, 0, 0, dp(4))
        })
        layout.addView(filter)
        layout.addView(
            ListView(activity).apply {
                this.adapter = adapter
                setOnItemClickListener { _, _, pos, _ -> openMessage(shown[pos]) }
                divider = android.graphics.drawable.ColorDrawable(Monet.palette(activity).outline and 0x00FFFFFF or 0x40000000)
                dividerHeight = dp(1)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(400))
        )
        filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = normalize(s?.toString() ?: "")
                shown.clear()
                shown.addAll(rows.filter { q.isEmpty() || it.searchKey.contains(q) })
                adapter.notifyDataSetChanged()
            }
        })

        current = Dlg(activity).setTitle(str(S.deleted_title)).setView(layout)
            .setPositiveButton(str(S.deleted_close), null).also { it.show() }
    }

    private fun openMessage(row: Row) {
        if (Host.openConversation(activity, row.jid, row.messageRowId, row.sortId)) current?.dismiss()
        else Host.toast(activity, str(S.deleted_open_error))
    }

    // ------------------------------------------------------------------ registro de texto

    private fun logFile() = DeletedLog(File(Host.accountDataDir(activity), "databases/msgstore.db"))

    private fun confirmEnableLog() {
        Dlg(activity).setTitle(str(S.log_confirm_title)).setMessage(str(S.log_confirm_message))
            .setPositiveButton(str(S.log_enable)) { _, _ ->
                Thread {
                    try {
                        val n = logFile().enable()
                        ui { Host.toast(activity, str(S.log_enabled_toast, n)); current?.dismiss(); start() }
                    } catch (e: Throwable) {
                        Host.log(e)
                        ui { Host.toast(activity, str(S.log_error, e.message ?: e.javaClass.simpleName)) }
                    }
                }.start()
            }
            .setNegativeButton(activity.getString(android.R.string.cancel), null).show()
    }

    private fun disableLog() {
        Thread {
            try {
                logFile().disable()
                ui { Host.toast(activity, str(S.log_disabled_toast)); current?.dismiss(); start() }
            } catch (e: Throwable) {
                Host.log(e)
                ui { Host.toast(activity, str(S.log_error, e.message ?: e.javaClass.simpleName)) }
            }
        }.start()
    }
}
