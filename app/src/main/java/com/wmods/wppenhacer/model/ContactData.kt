package com.wmods.wppenhacer.model

import java.io.Serializable

class ContactData(
    val name: String?,
    val jid: String?
) : Serializable {
    fun getDisplayName(): String = when {
        !name.isNullOrEmpty() -> name
        jid != null -> jid.split("@")[0]
        else -> ""
    }
}
