package com.wmods.wppenhacer.xposed.features.scheduling

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** What a scheduled entry carries. */
enum class MessageKind { TEXT, MEDIA, STICKER }

/** Where an entry is in its lifecycle. */
enum class DeliveryState { PENDING, SENDING, SENT, FAILED }

/**
 * One scheduled send.
 *
 * [jid] is a full WhatsApp JID (`<msisdn>@s.whatsapp.net` or `<id>@g.us`), not a bare
 * phone number, because the sender resolves it through `WppCore.createUserJid` and
 * that path already understands groups.
 */
data class ScheduledMessage(
    val id: Long = 0L,
    val jid: String,
    val displayName: String,
    val kind: MessageKind,
    val text: String? = null,
    val mediaUri: String? = null,
    val mime: String? = null,
    val triggerAt: Long,
    val state: DeliveryState = DeliveryState.PENDING,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun preview(): String = when (kind) {
        MessageKind.TEXT -> text.orEmpty().replace('\n', ' ').take(70)
        MessageKind.MEDIA -> "Media · ${mime ?: "file"}"
        MessageKind.STICKER -> "Sticker"
    }
}

/**
 * Storage for schedules, kept in WhatsApp's own data directory because this code runs
 * inside the WhatsApp process.
 *
 * Deliberately plain SQLite rather than Room: the module's Room database carries its
 * own schema and migrations, and a new entity there would mean touching generated DAOs
 * for a table that is completely independent of everything else.
 */
class ScheduledMessageStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE schedules (
                _id          INTEGER PRIMARY KEY AUTOINCREMENT,
                jid          TEXT    NOT NULL,
                display_name TEXT    NOT NULL DEFAULT '',
                kind         TEXT    NOT NULL,
                text         TEXT,
                media_uri    TEXT,
                mime         TEXT,
                trigger_at   INTEGER NOT NULL,
                state        TEXT    NOT NULL DEFAULT 'PENDING',
                last_error   TEXT,
                created_at   INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_state_trigger ON schedules(state, trigger_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(m: ScheduledMessage): Long {
        val cv = ContentValues().apply {
            put("jid", m.jid)
            put("display_name", m.displayName)
            put("kind", m.kind.name)
            put("text", m.text)
            put("media_uri", m.mediaUri)
            put("mime", m.mime)
            put("trigger_at", m.triggerAt)
            put("state", m.state.name)
            put("last_error", m.lastError)
            put("created_at", m.createdAt)
        }
        return writableDatabase.insert("schedules", null, cv)
    }

    fun updateState(id: Long, state: DeliveryState, error: String? = null) {
        val cv = ContentValues().apply {
            put("state", state.name)
            put("last_error", error)
        }
        writableDatabase.update("schedules", cv, "_id = ?", arrayOf(id.toString()))
    }

    /**
     * Moves a row from PENDING to SENDING and reports whether *this* caller won.
     *
     * The transition has to be a single conditional UPDATE rather than a read
     * followed by a write: the alarm broadcast and the activity-resume sweep can
     * both fire for the same id at the same moment, and a check-then-set would
     * let both pass and send the message twice.
     */
    fun claim(id: Long): Boolean {
        val cv = ContentValues().apply { put("state", DeliveryState.SENDING.name) }
        return writableDatabase.update(
            "schedules",
            cv,
            "_id = ? AND state = 'PENDING'",
            arrayOf(id.toString())
        ) == 1
    }

    /**
     * Rewrites an existing row from the editor.
     *
     * The state is forced back to PENDING and the error cleared: a row that is being
     * edited has not been sent yet, and if it had previously failed the old error
     * would otherwise still be shown against the new contents.
     */
    fun update(m: ScheduledMessage) {
        val cv = ContentValues().apply {
            put("jid", m.jid)
            put("display_name", m.displayName)
            put("kind", m.kind.name)
            put("text", m.text)
            put("media_uri", m.mediaUri)
            put("mime", m.mime)
            put("trigger_at", m.triggerAt)
            put("state", DeliveryState.PENDING.name)
            putNull("last_error")
        }
        writableDatabase.update("schedules", cv, "_id = ?", arrayOf(m.id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("schedules", "_id = ?", arrayOf(id.toString()))
    }

    fun get(id: Long): ScheduledMessage? =
        readableDatabase.rawQuery("SELECT * FROM schedules WHERE _id = ?", arrayOf(id.toString()))
            .use { if (it.moveToFirst()) it.toMessage() else null }

    fun pending(): List<ScheduledMessage> =
        readableDatabase.rawQuery(
            "SELECT * FROM schedules WHERE state = 'PENDING' ORDER BY trigger_at ASC", null
        ).use { c -> c.mapToMessages() }

    fun pendingFor(jid: String): List<ScheduledMessage> =
        readableDatabase.rawQuery(
            "SELECT * FROM schedules WHERE state = 'PENDING' AND jid = ? ORDER BY trigger_at ASC",
            arrayOf(jid)
        ).use { c -> c.mapToMessages() }

    fun all(): List<ScheduledMessage> =
        readableDatabase.rawQuery(
            "SELECT * FROM schedules ORDER BY trigger_at DESC", null
        ).use { c -> c.mapToMessages() }

    /** Rows left mid-flight by a process kill; retried when the process comes back. */
    fun interrupted(): List<ScheduledMessage> =
        readableDatabase.rawQuery(
            "SELECT * FROM schedules WHERE state = 'SENDING'", null
        ).use { c -> c.mapToMessages() }

    private fun android.database.Cursor.mapToMessages(): List<ScheduledMessage> {
        val out = ArrayList<ScheduledMessage>(count)
        while (moveToNext()) out.add(toMessage())
        return out
    }

    private fun android.database.Cursor.toMessage() = ScheduledMessage(
        id = getLong(getColumnIndexOrThrow("_id")),
        jid = getString(getColumnIndexOrThrow("jid")),
        displayName = getString(getColumnIndexOrThrow("display_name")),
        kind = runCatching { MessageKind.valueOf(getString(getColumnIndexOrThrow("kind"))) }
            .getOrDefault(MessageKind.TEXT),
        text = strOrNull("text"),
        mediaUri = strOrNull("media_uri"),
        mime = strOrNull("mime"),
        triggerAt = getLong(getColumnIndexOrThrow("trigger_at")),
        state = runCatching { DeliveryState.valueOf(getString(getColumnIndexOrThrow("state"))) }
            .getOrDefault(DeliveryState.PENDING),
        lastError = strOrNull("last_error"),
        createdAt = getLong(getColumnIndexOrThrow("created_at"))
    )

    private fun android.database.Cursor.strOrNull(col: String): String? {
        val i = getColumnIndexOrThrow(col)
        return if (isNull(i)) null else getString(i)
    }

    companion object {
        private const val DB_NAME = "wae_scheduled_messages.db"
        private const val DB_VERSION = 1

        @Volatile
        private var instance: ScheduledMessageStore? = null

        fun getInstance(context: Context): ScheduledMessageStore =
            instance ?: synchronized(this) {
                instance ?: ScheduledMessageStore(context).also { instance = it }
            }
    }
}
