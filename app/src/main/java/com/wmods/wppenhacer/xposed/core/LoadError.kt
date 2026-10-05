package com.wmods.wppenhacer.xposed.core

import com.wmods.wppenhacer.BuildConfig

internal class LoadError(
    val pluginName: String,
    private val whatsAppVersion: String?,
    private val message: String?,
    private val errorDetail: String
) {
    val summary get() = "$pluginName - $message"

    override fun toString() = """
        pluginName='$pluginName'
        moduleVersion='${BuildConfig.VERSION_NAME}'
        whatsAppVersion='$whatsAppVersion'
        Message=$message
        error='$errorDetail'
    """.trimIndent()

    companion object {
        fun from(pluginName: String, whatsAppVersion: String?, error: Throwable) = LoadError(
            pluginName = pluginName,
            whatsAppVersion = whatsAppVersion,
            message = error.message,
            errorDetail = error.stackTrace
                .filterNot { it.className.startsWith("android") || it.className.startsWith("com.android") }
                .joinToString(prefix = "[", postfix = "]")
        )
    }
}
