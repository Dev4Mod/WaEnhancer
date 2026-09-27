package com.wmods.wppenhacer.model

import java.io.Serializable

data class ContactPickerResult(
    val jid: String,
    val fullName: String
) : Serializable {
    fun toContactData(): ContactData = ContactData(fullName, jid)

    // Keep the Java record accessors available to existing integrations.
    fun jid(): String = jid
    fun fullName(): String = fullName
}
