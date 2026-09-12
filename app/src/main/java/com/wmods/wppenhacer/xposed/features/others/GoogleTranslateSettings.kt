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
            (parts[1] != "auto" && parts[1] !in languages) || parts[2] !in languages) return null
        return GoogleTranslationConfig(parts[0] == "on", parts[1], parts[2])
    }

    fun legacy(value: String?): GoogleTranslationConfig? = when {
        value == "off" -> GoogleTranslationConfig()
        value in languages -> GoogleTranslationConfig(true, "auto", value!!)
        else -> null
    }

    fun resolve(local: String?, global: String?, legacyLocal: String?, legacyGlobal: String?) =
        decode(local) ?: legacy(legacyLocal) ?: decode(global) ?: legacy(legacyGlobal) ?: GoogleTranslationConfig()

    /** Preserve full group IDs (including hyphens); discard status/channel/broadcast IDs. */
    fun normalizeChatId(raw: String?): String? {
        val jid = raw?.replaceFirst("\\.[\\d:]+@".toRegex(), "@") ?: return null
        val name = jid.substringBefore('@', "")
        if (name.isEmpty()) return null
        return jid.takeIf { it.substringAfter('@', "") in setOf("g.us", "s.whatsapp.net", "lid") }
    }
}
