package com.wmods.wppenhacer.toolkit.importer

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.toolkit.Host
import com.wmods.wppenhacer.toolkit.Monet
import com.wmods.wppenhacer.toolkit.S
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Flujo de importación desde la interfaz de WhatsApp: lee el archivo elegido, pide el chat de
 * destino y quién eres tú, e importa con barra de progreso.
 */
class ImportChatFlow(private val activity: Activity, private val uri: Uri) {

    private class ChatChoice(
        val info: ChatImporter.ChatInfo?,
        val name: String,
        /** Texto que se muestra en las listas. */
        val label: String = name,
        /** JID del contacto cuando aún no tiene chat (se crea al importar). */
        val newJid: String? = null
    ) {
        val isGroup: Boolean get() = info?.isGroup == true
    }

    private var source: ChatImportSource? = null
    private var parsed: ParsedChat? = null
    private var chats: List<ChatChoice> = emptyList()
    private var fileName: String? = null

    // Estado de las opciones; se conserva si el diálogo se vuelve a mostrar.
    private var selectedChat: ChatChoice? = null
    private var newChatMode = false
    private var meIndex = 0
    private var orderChoice = 0 // 0 automático, 1 día/mes, 2 mes/día
    private var importMedia = true
    private var backup = true

    private val cancelled = AtomicBoolean(false)

    private fun str(id: Int, vararg args: Any): String = S.get(id, *args)
    private fun dp(v: Int) = Host.dp(activity, v)
    private fun ui(block: () -> Unit) = activity.runOnUiThread { block() }

    private fun dbFile() = File(Host.accountDataDir(activity), "databases/msgstore.db")
    private fun importer() = ChatImporter(dbFile(), Host.rootWhatsAppDir(activity))

    // ------------------------------------------------------------------ 1. leer el archivo

    fun start() {
        cleanOldWorkDirs()
        val progress = ProgressUi(str(S.import_chat_reading), cancelable = false)
        progress.show()
        Thread {
            try {
                fileName = queryDisplayName()
                val input = activity.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("openInputStream = null")
                val src = ChatImportSource.open(
                    input, File(activity.cacheDir, "wae_import_${System.currentTimeMillis()}")
                )
                source = src
                val chat = src.parse()
                if (chat.messages.isEmpty()) {
                    ui { progress.dismiss(); showMessage(str(S.import_chat_error_title), str(S.import_chat_error_empty) + "\n\n" + src.sample()); finish() }
                    return@Thread
                }
                parsed = chat
                contactNames = importer().contactNames(File(Host.accountDataDir(activity), "databases/wa.db"))
                chats = importer().listChats().map { ChatChoice(it, displayName(it)) } + loadNewContacts()
                selectedChat = guessChat()
                newChatMode = selectedChat?.newJid != null
                meIndex = guessMe(chat)
                ui { progress.dismiss(); showOptions() }
            } catch (e: Throwable) {
                Host.log(e)
                ui { progress.dismiss(); showFailure(e); finish() }
            }
        }.start()
    }

    private fun queryDisplayName(): String? = try {
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    /** Borra importaciones anteriores que quedaron a medias (p. ej. si se cerró WhatsApp). */
    private fun cleanOldWorkDirs() {
        try {
            val limit = System.currentTimeMillis() - 60 * 60 * 1000
            activity.cacheDir.listFiles { f -> f.isDirectory && f.name.startsWith("wae_import_") }
                ?.filter { it.lastModified() < limit }?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {
        }
    }

    private fun finish() {
        try {
            source?.close()
        } catch (_: Exception) {
        }
        source = null
    }

    // ------------------------------------------------------------------ nombres de chats

    /** Contactos sin chat: de la agenda de WhatsApp (wa.db) o, si no hay, de la del teléfono. */
    private fun loadNewContacts(): List<ChatChoice> {
        val imp = importer()
        var contacts = try {
            imp.listContactsWithoutChat(File(Host.accountDataDir(activity), "databases/wa.db"))
        } catch (e: Throwable) {
            Host.log(e)
            emptyList()
        }
        if (contacts.isEmpty()) {
            val taken = chats.mapNotNull { it.info?.rawJid }.toSet()
            contacts = Host.deviceContacts(activity)
                .map { (name, digits) -> ChatImporter.ContactInfo("$digits@s.whatsapp.net", name) }
                .filter { it.jid !in taken }
        }
        return contacts.map {
            ChatChoice(null, it.name, str(S.import_chat_new_suffix, it.name), it.jid)
        }
    }

    private var contactNames: Map<String, String> = emptyMap()

    private fun displayName(info: ChatImporter.ChatInfo): String {
        if (info.isGroup) {
            val subject = info.subject?.takeIf { it.isNotBlank() } ?: info.rawJid.substringBefore('@')
            return str(S.import_chat_group_suffix, subject)
        }
        // Nombre en la agenda de WhatsApp, por el JID del chat o por el número del mismo contacto.
        contactNames[info.rawJid]?.let { return it }
        info.phoneJid?.let { phone -> contactNames[phone]?.let { return it } }
        // Nombre en la agenda del teléfono.
        val phoneDigits = (info.phoneJid ?: info.rawJid.takeIf { it.endsWith("@s.whatsapp.net") })
            ?.substringBefore('@')
        if (phoneDigits != null) {
            Host.contactName(activity, phoneDigits)?.let { return it }
            return "+$phoneDigits"
        }
        return info.rawJid.substringBefore('@')
    }

    /** Minúsculas, sin acentos y con espacios simples, para buscar y comparar nombres. */
    private fun normalizeName(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[\\s\u00A0\u202F]+"), " ").trim()

    /** Intenta deducir el chat a partir del nombre del archivo ("Chat de WhatsApp con Ana.zip"). */
    private fun guessChat(): ChatChoice? {
        val raw = fileName ?: return null
        var base = raw.substringBeforeLast('.')
        for (prefix in listOf(
            "whatsapp chat with ", "whatsapp chat - ", "chat de whatsapp con ", "chat do whatsapp com ",
            "chat whatsapp avec ", "whatsapp-chat mit ", "chat whatsapp con ", "whatsapp chat met "
        )) {
            if (base.lowercase(Locale.ROOT).startsWith(prefix)) {
                base = base.substring(prefix.length)
                break
            }
        }
        val wanted = normalizeName(base)
        if (wanted.isEmpty()) return null
        val matches = chats.filter {
            normalizeName(it.name).removeSuffix(" (group)").removeSuffix(" (grupo)") == wanted
        }
        return matches.singleOrNull()
    }

    /** En un chat individual, tú eres el participante que no se llama como el contacto. */
    private fun guessMe(chat: ParsedChat): Int {
        val other = selectedChat?.takeIf { !it.isGroup }?.name ?: return 0
        if (chat.speakers.size != 2) return 0
        val i = chat.speakers.indexOfFirst { normalizeName(it) == normalizeName(other) }
        return if (i >= 0) 1 - i else 0
    }

    // ------------------------------------------------------------------ 2. opciones

    private fun orderLabel(o: DateOrder): String = when (o) {
        DateOrder.MONTH_DAY -> str(S.import_chat_order_mdy)
        DateOrder.YEAR_MONTH_DAY -> str(S.import_chat_order_ymd)
        else -> str(S.import_chat_order_dmy)
    }

    private fun summaryText(chat: ParsedChat): String {
        val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
        val from = df.format(Date(chat.messages.first().timestamp))
        val to = df.format(Date(chat.messages.last().timestamp))
        return str(
            S.import_chat_summary, chat.messages.size, chat.attachmentCount, from, to,
            chat.speakers.joinToString(", ")
        )
    }

    private fun label(text: String) = TextView(activity).apply {
        this.text = text
        textSize = 14f
        setTextColor(Host.textColor(activity))
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun showOptions() {
        val chat = parsed ?: return
        val src = source ?: return

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val summary = TextView(activity).apply {
            text = summaryText(chat)
            textSize = 14f
            setTextColor(Host.textColor(activity))
        }
        root.addView(summary)

        // Chat de destino: uno existente o uno nuevo con un contacto
        root.addView(label(str(S.import_chat_destination)))
        val modeGroup = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL }
        val rbExisting = RadioButton(activity).apply {
            id = View.generateViewId(); text = str(S.import_chat_mode_existing)
            setTextColor(Host.textColor(activity))
        }
        val rbNew = RadioButton(activity).apply {
            id = View.generateViewId(); text = str(S.import_chat_mode_new)
            setTextColor(Host.textColor(activity))
        }
        modeGroup.addView(rbExisting)
        modeGroup.addView(rbNew)
        modeGroup.check(if (newChatMode) rbNew.id else rbExisting.id)
        root.addView(modeGroup)

        val chatButton = Button(activity)
        fun refreshChatButton() {
            chatButton.text = selectedChat?.label
                ?: str(if (newChatMode) S.import_chat_pick_contact else S.import_chat_pick)
        }
        refreshChatButton()
        root.addView(chatButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        modeGroup.setOnCheckedChangeListener { _, checked ->
            newChatMode = checked == rbNew.id
            if (selectedChat != null && (selectedChat?.newJid != null) != newChatMode) selectedChat = null
            refreshChatButton()
        }

        // Quién eres tú
        root.addView(label(str(S.import_chat_me)))
        val meSpinner = Spinner(activity)
        meSpinner.adapter = ArrayAdapter(
            activity, android.R.layout.simple_spinner_dropdown_item, chat.speakers
        )
        meSpinner.setSelection(meIndex.coerceIn(0, chat.speakers.size - 1))
        root.addView(meSpinner)

        chatButton.setOnClickListener {
            showChatPicker { picked ->
                selectedChat = picked
                refreshChatButton()
                val guess = guessMe(chat)
                if (picked != null && !picked.isGroup && chat.speakers.size == 2) {
                    meSpinner.setSelection(guess)
                }
            }
        }

        // Orden de fechas
        root.addView(label(str(S.import_chat_date_order)))
        val orderSpinner = Spinner(activity)
        val orderItems = listOf(
            str(S.import_chat_order_auto, orderLabel(chat.dateOrder)),
            str(S.import_chat_order_dmy),
            str(S.import_chat_order_mdy)
        )
        orderSpinner.adapter = ArrayAdapter(
            activity, android.R.layout.simple_spinner_dropdown_item, orderItems
        )
        orderSpinner.setSelection(orderChoice)
        orderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (position == orderChoice) return
                orderChoice = position
                val order = when (position) {
                    1 -> DateOrder.DAY_MONTH
                    2 -> DateOrder.MONTH_DAY
                    else -> DateOrder.AUTO
                }
                try {
                    val reparsed = src.parse(order)
                    if (reparsed.messages.isNotEmpty()) {
                        parsed = reparsed
                        summary.text = summaryText(reparsed)
                    }
                } catch (e: Exception) {
                    Host.log(e)
                }
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(orderSpinner)

        // Opciones
        val mediaCheck = CheckBox(activity).apply {
            text = str(S.import_chat_media, src.mediaCount)
            isChecked = importMedia
            setTextColor(Host.textColor(activity))
            visibility = if (chat.attachmentCount > 0) View.VISIBLE else View.GONE
            setOnCheckedChangeListener { _, checked -> importMedia = checked }
        }
        root.addView(mediaCheck)
        val backupCheck = CheckBox(activity).apply {
            text = str(S.import_chat_backup)
            isChecked = backup
            setTextColor(Host.textColor(activity))
            setOnCheckedChangeListener { _, checked -> backup = checked }
        }
        root.addView(backupCheck)
        root.addView(TextView(activity).apply {
            text = str(S.import_chat_note)
            textSize = 12f
            alpha = 0.75f
            setTextColor(Host.textColor(activity))
            setPadding(0, dp(10), 0, dp(4))
        })

        Dlg(activity)
            .setTitle(str(S.import_chat))
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton(str(S.import_chat_action)) { _, _ ->
                meIndex = meSpinner.selectedItemPosition.coerceAtLeast(0)
                if (selectedChat == null) {
                    Host.toast(activity, str(S.import_chat_need_chat), Toast.LENGTH_SHORT)
                    showOptions()
                } else {
                    runImport()
                }
            }
            .setNegativeButton(activity.getString(android.R.string.cancel)) { _, _ -> finish() }
            .show()
    }

    /** Lista de chats con buscador. */
    private fun showChatPicker(onPick: (ChatChoice?) -> Unit) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val pool = chats.filter { (it.newJid != null) == newChatMode }
        val search = EditText(activity).apply {
            hint = str(if (newChatMode) S.import_chat_search_contact_hint else S.import_chat_search_hint)
            setSingleLine()
            setTextColor(Host.textColor(activity))
        }
        val shown = ArrayList(pool)
        val adapter = Monet.adapter(activity, shown.map { it.label })
        val list = ListView(activity).apply { this.adapter = adapter }
        layout.addView(search)
        layout.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val raw = s?.toString() ?: ""
                val q = normalizeName(raw)
                shown.clear()
                shown.addAll(pool.filter { q.isEmpty() || normalizeName(it.name).contains(q) })
                if (newChatMode) {
                    // Un número escrito a mano sirve aunque no esté en la agenda.
                    val digits = raw.filter { it.isDigit() }
                    if (digits.length in 7..15 && raw.all { it.isDigit() || it in "+ -()" }) {
                        shown.add(0, ChatChoice(null, "+$digits", str(S.import_chat_use_number, digits), "$digits@s.whatsapp.net"))
                    }
                }
                adapter.clear()
                adapter.addAll(shown.map { it.label })
            }
        })

        val dialog = Dlg(activity)
            .setTitle(str(S.import_chat_destination))
            .setView(layout)
            .setNegativeButton(activity.getString(android.R.string.cancel), null)
        list.setOnItemClickListener { _, _, position, _ ->
            onPick(shown.getOrNull(position))
            dialog.dismiss()
        }
        dialog.show()
    }

    // ------------------------------------------------------------------ 3. importar

    private fun runImport() {
        val chat = parsed ?: return
        val target = selectedChat ?: return
        val me = chat.speakers.getOrNull(meIndex) ?: return
        val options = ChatImporter.Options(target.info?.rowId ?: 0L, me, importMedia, backup, target.newJid)

        cancelled.set(false)
        val progress = ProgressUi(str(S.import_chat_reading), cancelable = true)
        progress.show()

        Thread {
            try {
                val result = importer().import(
                    chat, source, options,
                    { phase, done, total ->
                        ui {
                            when (phase) {
                                ChatImporter.Phase.BACKUP ->
                                    progress.update(str(S.import_chat_progress_backup, done), done, total)
                                ChatImporter.Phase.MEDIA ->
                                    progress.update(str(S.import_chat_progress_media, done, total), done, total)
                                ChatImporter.Phase.MESSAGES ->
                                    progress.update(str(S.import_chat_progress_messages, done, total), done, total)
                            }
                        }
                    },
                    { cancelled.get() }
                )
                ui { progress.dismiss(); showDone(result); finish() }
            } catch (e: Throwable) {
                if (e !is ChatImporter.ImportException || e.code != ChatImporter.Code.CANCELLED) Host.log(e)
                ui { progress.dismiss(); showFailure(e); finish() }
            }
        }.start()
    }

    private fun showDone(r: ChatImporter.Result) {
        val extra = StringBuilder()
        if (r.mediaFailed > 0) extra.append(str(S.import_chat_done_media_failed, r.mediaFailed))
        if (r.skippedNotOlder > 0) extra.append(str(S.import_chat_done_skipped, r.skippedNotOlder))
        if (r.chatCreated) {
            val last = parsed?.messages?.lastOrNull()?.timestamp
            if (last != null) {
                extra.append(str(S.import_chat_done_created, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(last))))
            }
        }
        r.backupFile?.let { extra.append(str(S.import_chat_done_backup, it.name)) }
        Dlg(activity)
            .setTitle(str(S.import_chat_done_title))
            .setMessage(str(S.import_chat_done_message, r.imported, r.importedMedia, extra.toString()))
            .setPositiveButton(str(S.import_chat_restart_now)) { _, _ -> Host.restart(activity) }
            .setNegativeButton(str(S.import_chat_later), null)
            .show()
    }

    private fun showFailure(e: Throwable) {
        if (e is ChatImporter.ImportException && e.code == ChatImporter.Code.CANCELLED) {
            Host.toast(activity, str(S.import_chat_cancelled), Toast.LENGTH_LONG)
            return
        }
        val text = when {
            e is ChatImporter.ImportException -> when (e.code) {
                ChatImporter.Code.DB_MISSING -> str(S.import_chat_error_db_missing)
                ChatImporter.Code.CHAT_NOT_FOUND -> str(S.import_chat_error_chat_missing)
                ChatImporter.Code.NOTHING_TO_IMPORT -> str(S.import_chat_error_nothing)
                ChatImporter.Code.NO_SPACE_DB -> str(S.import_chat_error_space_db)
                ChatImporter.Code.NO_SPACE_MEDIA -> str(S.import_chat_error_space_media)
                else -> e.message ?: e.javaClass.simpleName
            }
            e is ChatImportSource.InvalidExportException -> str(S.import_chat_error_empty)
            else -> str(S.import_chat_error_open, e.message ?: e.javaClass.simpleName)
        }
        showMessage(str(S.import_chat_error_title), text)
    }

    private fun showMessage(title: String, message: String) {
        Dlg(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(activity.getString(android.R.string.ok), null)
            .show()
    }

    /** Diálogo de progreso con botón de cancelar opcional. */
    private inner class ProgressUi(text: String, cancelable: Boolean) {
        private val label = TextView(activity).apply {
            this.text = text
            setTextColor(Host.textColor(activity))
        }
        private val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            max = 100
        }
        private val dialog: Dlg

        init {
            val layout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(16), dp(24), dp(8))
                addView(label)
                addView(bar, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            dialog = Dlg(activity).setTitle(str(S.import_chat)).setView(layout)
            if (cancelable) {
                dialog.setNegativeButton(activity.getString(android.R.string.cancel)) { _, _ -> cancelled.set(true) }
            }
        }

        fun show() {
            dialog.create().apply {
                setCancelable(false)
                setCanceledOnTouchOutside(false)
            }
            dialog.show()
        }

        fun update(text: String, done: Int, total: Int) {
            label.text = text
            if (total > 0) {
                bar.isIndeterminate = false
                bar.progress = (done.toLong() * 100 / total).toInt()
            }
        }

        fun dismiss() = dialog.dismiss()
    }
}
