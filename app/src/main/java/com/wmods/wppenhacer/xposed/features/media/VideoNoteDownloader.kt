package com.wmods.wppenhacer.xposed.features.media

import android.content.SharedPreferences
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.concurrent.CompletableFuture

class VideoNoteDownloader(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    companion object {
        private const val MEDIA_TYPE_VIDEO_NOTE = 81
        private const val TAG_VIDEO_DOWNLOAD_BUTTON = "video_note_download_button"
    }

    override fun doHook() {
        if (!xprefs.getBoolean("downloadvideonote", false)) return

        ConversationItemListener.conversationListeners.add(
            object : ConversationItemListener.OnConversationItemListener() {
                override fun onItemBind(
                    fMessage: FMessageWpp,
                    view: ViewGroup,
                    position: Int,
                    convertView: View?
                ) {
                    bindDownloadButton(fMessage, view)
                }
            }
        )
    }

    private fun bindDownloadButton(fMessage: FMessageWpp, group: ViewGroup) {
        var buttonImage = group.findViewWithTag<ImageView>(TAG_VIDEO_DOWNLOAD_BUTTON)
        if (fMessage.mediaType != MEDIA_TYPE_VIDEO_NOTE) {
            buttonImage?.visibility = View.GONE
            return
        }
        buttonImage?.visibility = View.VISIBLE
        if (buttonImage == null) {
            val buttonView = group.findViewById<View>(Utils.getID("action_button", "id")) ?: return
            val parent = buttonView.parent as? ViewGroup ?: return
            val linearLayout = LinearLayout(group.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = buttonView.layoutParams
            }
            parent.removeView(buttonView)
            linearLayout.addView(buttonView)
            parent.addView(linearLayout)
            buttonImage = ImageView(group.context).apply {
                setImageResource(R.drawable.download)
                layoutParams =
                    LinearLayout.LayoutParams(Utils.dipToPixels(28), Utils.dipToPixels(28))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(Utils.dipToPixels(5), 0, 0, 0)
                tag = TAG_VIDEO_DOWNLOAD_BUTTON
            }
            linearLayout.addView(buttonImage)
        }
        buttonImage.setOnClickListener {
            CompletableFuture.runAsync { saveVideoNote(fMessage) }
        }
    }

    private fun saveVideoNote(fMessage: FMessageWpp) {
        val file = fMessage.mediaFile ?: return
        val dest = Utils.getDestination("Video Notes")
        val fileExtension = file.absolutePath.substring(file.absolutePath.lastIndexOf(".") + 1)
        val name = Utils.generateName(fMessage.key.remoteJid, fileExtension)
        var savedTo = dest
        val error = Utils.copyFile(file, dest, name) { savedTo = it }
        if (TextUtils.isEmpty(error)) {
            Utils.showToast(
                Utils.application.getString(R.string.saved_to) + savedTo,
                Toast.LENGTH_LONG
            )
        } else {
            Utils.showToast(
                Utils.application.getString(R.string.error_when_saving_try_again) + ":" + error,
                Toast.LENGTH_LONG
            )
        }
    }

    override fun getPluginName(): String = "Video Note Downloader"
}
