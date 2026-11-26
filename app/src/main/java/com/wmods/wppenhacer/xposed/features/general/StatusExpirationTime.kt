package com.wmods.wppenhacer.xposed.features.general

import android.content.SharedPreferences
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.TextView
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.StatusItemWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

class StatusExpirationTime(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    companion object {
        private val STATUS_LIFETIME_MS = TimeUnit.HOURS.toMillis(24)
        private const val FIELD_EXPIRATION_SUFFIX = "wae_status_expiration_suffix"
        private const val SEPARATOR = " · "
    }

    override fun doHook() {
        if (!xprefs.getBoolean("status_expiration_time", true)) return

        val subtitleMethod = Unobfuscator.loadUnknownStatusPlaybackMethod(classLoader)
        val playbackViewClass = Unobfuscator.loadStatusPlaybackViewClass(classLoader)
        val timestampMethod = runCatching {
            Unobfuscator.loadStatusItemTimestampMethod(classLoader)
        }.onFailure { logDebug("Status timestamp getter not found, using FMessage", it) }
            .getOrNull()

        val fragmentClass = subtitleMethod.declaringClass
        val playbackViewField = ReflectionUtils.getFieldByType(fragmentClass, playbackViewClass)
            ?: throw NoSuchFieldException("StatusPlayback view holder field not found")
        val textViewFields = ReflectionUtils.getFieldsByType(playbackViewClass, TextView::class.java)
        val dateId = Utils.getID("date", "id")

        subtitleMethod.hook {
            after {
                val statusItem = args.getOrNull(0) ?: return@after
                val fragment = ReflectionUtils.getArg(args, fragmentClass, 0) ?: return@after
                val holder = playbackViewField.get(fragment) ?: return@after
                val dateView = textViewFields.firstNotNullOfOrNull { field ->
                    (field.get(holder) as? TextView)?.takeIf { it.id == dateId }
                } ?: return@after
                applySuffix(dateView, getPostedTimestamp(statusItem, timestampMethod))
            }
        }
    }

    private fun getPostedTimestamp(statusItem: Any, timestampMethod: Method?): Long {
        if (timestampMethod != null && timestampMethod.declaringClass.isInstance(statusItem)) {
            val value = runCatching { timestampMethod.invoke(statusItem) as? Long }.getOrNull()
            if (value != null && value > 0) return value
        }
        return StatusItemWpp.from(statusItem)?.fMessage?.timeStamp ?: -1L
    }

    private fun applySuffix(dateView: TextView, postedAt: Long) {
        val text = SpannableStringBuilder(dateView.text ?: "")
        val previousSuffix =
            ReflectionUtils.getAdditionalInstanceField(dateView, FIELD_EXPIRATION_SUFFIX) as? String
        val hadSuffix = previousSuffix != null && text.endsWith(previousSuffix)
        if (hadSuffix) text.delete(text.length - previousSuffix.length, text.length)

        val remaining = postedAt + STATUS_LIFETIME_MS - System.currentTimeMillis()
        if (postedAt <= 0 || remaining <= 0 || text.isBlank() || dateView.visibility != View.VISIBLE) {
            ReflectionUtils.setAdditionalInstanceField(dateView, FIELD_EXPIRATION_SUFFIX, null)
            if (hadSuffix) dateView.text = text
            return
        }

        val suffix = SEPARATOR + Utils.application.getString(
            R.string.status_expires_in,
            formatRemaining(remaining)
        )
        ReflectionUtils.setAdditionalInstanceField(dateView, FIELD_EXPIRATION_SUFFIX, suffix)
        dateView.text = text.append(suffix)
    }

    private fun formatRemaining(remainingMs: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs).coerceAtLeast(1)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    override fun getPluginName(): String = "Status Expiration Time"
}
