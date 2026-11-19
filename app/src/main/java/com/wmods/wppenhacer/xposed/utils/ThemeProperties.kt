package com.wmods.wppenhacer.xposed.utils

import java.util.Properties

internal object ThemeProperties {
    // Later sources override matching globals from earlier sources.
    fun parse(vararg sources: String): Properties = Properties().apply {
        for (source in sources) {
            val block = Regex("^\\s*/\\*([\\s\\S]*?)\\*/").find(source) ?: continue
            for (line in block.groupValues[1].lineSequence()) {
                val separator = line.indexOf('=')
                if (separator <= 0) continue
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim().removeSurrounding("\"")
                if (key.isNotEmpty() && value.isNotEmpty()) setProperty(key, value)
            }
        }
    }
}
