package com.wmods.wppenhacer.xposed.features.others

/** Pure settings model: opening/editing dialogs never mutates persisted settings. */
internal data class GoogleTranslationConfig(
    val enabled: Boolean = false,
    val source: String = "auto",
    val target: String = "en"
) {
    fun encode() = "${if (enabled) "on" else "off"}|$source|$target"
}

internal object GoogleTranslateSettings {
    private val languages = GoogleTranslateLanguages.entries.map { it.first }.toSet()

    fun decode(value: String?): GoogleTranslationConfig? {
        val parts = value?.split('|') ?: return null
        if (parts.size != 3 || parts[0] !in setOf("on", "off") ||
            (parts[1] != "auto" && parts[1] !in languages) || parts[2] !in languages
        ) return null
        return GoogleTranslationConfig(parts[0] == "on", parts[1], parts[2])
    }

    fun legacy(value: String?): GoogleTranslationConfig? = when {
        value == "off" -> GoogleTranslationConfig()
        value in languages -> GoogleTranslationConfig(true, "auto", value!!)
        else -> null
    }

    fun resolve(local: String?, global: String?, legacyLocal: String?, legacyGlobal: String?) =
        decode(local) ?: legacy(legacyLocal) ?: decode(global) ?: legacy(legacyGlobal)
        ?: GoogleTranslationConfig()

    /** Preserve full group IDs (including hyphens); discard status/channel/broadcast IDs. */
    fun normalizeChatId(raw: String?): String? {
        val jid = raw?.replaceFirst("\\.[\\d:]+@".toRegex(), "@") ?: return null
        val name = jid.substringBefore('@', "")
        if (name.isEmpty()) return null
        return jid.takeIf { it.substringAfter('@', "") in setOf("g.us", "s.whatsapp.net", "lid") }
    }
    
    /**
     * Extract raw JID string from WhatsApp JID object (moved from GoogleTranslateChatUi)
     */
    fun rawJid(jid: Any?): String? = runCatching {
        normalizeChatId(
            com.wmods.wppenhacer.xposed.utils.ReflectionUtils.callMethod(
                jid,
                "getRawString"
            ) as? String
        )
    }.getOrNull()
    
    /**
     * Get chat ID from WhatsApp JID, converting to phone JID for individuals (moved from GoogleTranslateChatUi)
     */
    fun chatId(jid: Any?): String? {
        val raw = rawJid(jid) ?: return null
        // GroupJid must never be passed through a person/LID conversion.
        if (raw.endsWith("@g.us")) return raw
        return rawJid(com.wmods.wppenhacer.xposed.core.WppCore.getPhoneJidFromUserJid(jid)) ?: raw
    }
    
    /**
     * Extract chat ID from message using conversation JID (moved from GoogleTranslateChatUi)
     */
    fun messageChat(message: com.wmods.wppenhacer.xposed.core.components.FMessageWpp): String? {
        // Read the conversation JID, never the participant/sender JID.
        val jid = runCatching {
            com.wmods.wppenhacer.xposed.utils.ReflectionUtils.getObjectField(
                message.key.thisObject,
                "A00"
            )
        }.getOrNull()
        return chatId(jid) ?: chatId(message.key.remoteJid.phoneJid)
        ?: chatId(message.key.remoteJid.userJid)
    }
    
    /**
     * Resolve translation config for a chat ID
     */
    fun config(chat: String?, prefs: android.content.SharedPreferences): GoogleTranslationConfig {
        val key: (String?) -> String = { "google_translation_v3_" + (it ?: "default") }
        val legacyKey: (String?) -> String = { "google_translation_v2_" + (it ?: "default") }
        return resolve(
            chat?.let { prefs.getString(key(it), null) },
            prefs.getString(key(null), null),
            chat?.let { prefs.getString(legacyKey(it), null) },
            prefs.getString(legacyKey(null), null)
        )
    }
    
    /**
     * Resolve translation config for a message using the same logic as the UI.
     * Returns normalized chat ID and config (per-chat, then global, then legacy).
     */
    fun resolveForMessage(
        fmessage: com.wmods.wppenhacer.xposed.core.components.FMessageWpp,
        prefs: android.content.SharedPreferences
    ): Pair<String?, GoogleTranslationConfig> {
        val chatId = messageChat(fmessage)
        val config = config(chatId, prefs)
        return Pair(chatId, config)
    }
}
