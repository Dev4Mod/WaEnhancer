package com.wmods.wppenhacer.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.features.customization.IosEmojiFont

/**
 * Non-interactive preview row for the iOS Emoji setting. Shows a row of emoji
 * (rendered with the picked iOS font when available) plus the emoji set label.
 */
class IosEmojiPreviewPreference(context: Context, attrs: AttributeSet?) :
    Preference(context, attrs) {

    init {
        isPersistent = false
        isSelectable = false
        layoutResource = R.layout.preference_ios_emoji_preview
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val row = holder.itemView.findViewById<TextView>(R.id.ios_emoji_preview_row)
        val caption = holder.itemView.findViewById<TextView>(R.id.ios_emoji_preview_caption)
        row.text = context.getString(R.string.ios_emoji_preview_row)
        caption.text = context.getString(R.string.ios_emoji_preview_caption)
        IosEmojiFont.from(
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString("ios_emoji_font", null)
        )?.let { row.typeface = it }
    }
}
