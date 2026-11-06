package com.wmods.wppenhacer.xposed.features.customization

import android.graphics.Typeface
import com.wmods.wppenhacer.xposed.utils.Utils

/**
 * Loads the user supplied iOS/Apple emoji font once and shares it between the
 * Xposed hooks and the settings preview.
 *
 * Apple's emoji font is proprietary, so it is not bundled: the user picks their
 * own .ttf/.ttc in the "iOS emoji font" setting. When no font is chosen we fall
 * back to the system emoji font, which still replaces WhatsApp's stock sprite
 * emoji everywhere (chat, reactions and the emoji picker).
 */
object IosEmojiFont {

    @Volatile
    private var cachedPath: String? = null

    @Volatile
    private var cachedTypeface: Typeface? = null

    @JvmStatic
    fun from(path: String?): Typeface? {
        if (path.isNullOrBlank()) return null
        return synchronized(this) {
            if (path == cachedPath) {
                cachedTypeface
            } else {
                val font = runCatching { Typeface.createFromFile(path) }.getOrNull()
                cachedPath = path
                cachedTypeface = font
                font
            }
        }
    }

    /** Only call this from the hooked WhatsApp process, where Utils.xprefs is set. */
    @JvmStatic
    fun fromPrefs(): Typeface? = runCatching {
        from(Utils.xprefs.getString("ios_emoji_font", null))
    }.getOrNull()
}
