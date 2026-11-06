package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.graphics.Typeface
import android.widget.TextView
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.ViewHookDispatcher

/**
 * "iOS Emoji": makes the selected emoji font apply to EVERY emoji WhatsApp
 * draws, including the emoji picker / reaction tray / chat bubbles.
 *
 * The heavy lifting for text spans is done by [DefaultEmoji] (it already
 * replaces WA's sprite-based EmojiSpan rendering with real text). This feature
 * adds the plain TextView coverage used by the picker grid and reaction rows.
 */
class IosEmoji(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        if (!xprefs.getBoolean("ios_emoji", false)) return

        val typeface = IosEmojiFont.from(xprefs.getString("ios_emoji_font", null))
            ?: Typeface.DEFAULT

        ViewHookDispatcher.onAttachedToWindow(this) { view ->
            if (view !is TextView) return@onAttachedToWindow
            val name = runCatching {
                if (view.id != android.view.View.NO_ID) {
                    view.resources.getResourceEntryName(view.id)
                } else {
                    view.javaClass.name
                }
            }.getOrDefault(view.javaClass.name).lowercase()

            val isEmojiView = name.contains("emoji") || name.contains("reaction") ||
                    name.contains("sticker") || looksLikeEmoji(view.text)
            if (!isEmojiView) return@onAttachedToWindow

            runCatching { view.typeface = typeface }
        }
    }

    private fun looksLikeEmoji(text: CharSequence?): Boolean {
        if (text == null || text.isEmpty() || text.length > 16) return false
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            if (cp >= 0x1F000 || cp in 0x2600..0x27BF) return true
            i += Character.charCount(cp)
        }
        return false
    }

    override fun getPluginName(): String = "IosEmoji"
}
