package com.wmods.wppenhacer.toolkit

import android.content.Context

/** Resuelve JID → nombre: agenda de WhatsApp, agenda del teléfono o el número. */
class Names(
    private val context: Context?,
    private val waNames: Map<String, String>,
    private val phoneByLid: Map<String, String>
) {
    private val cache = HashMap<String, String>()

    fun of(jid: String?): String {
        if (jid == null) return "?"
        return cache.getOrPut(jid) { resolve(jid) }
    }

    private fun resolve(jid: String): String {
        waNames[jid]?.let { return it }
        val phone = phoneByLid[jid] ?: jid.takeIf { it.endsWith("@s.whatsapp.net") }
        phone?.let { p -> waNames[p]?.let { return it } }
        val digits = phone?.substringBefore('@')
        if (digits != null) {
            if (context != null) Host.contactName(context, digits)?.let { return it }
            return "+$digits"
        }
        return jid.substringBefore('@')
    }
}
