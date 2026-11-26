package com.wmods.wppenhacer.xposed.features.media

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.core.view.isGone
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.utils.AnimationUtil
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AudioSpeedControl(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    private val audioSpeed = AtomicReference(0f)
    private val changed = AtomicBoolean(false)

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility", "DefaultLocale")
    override fun doHook() {
        val voicenoteSpeed = xprefs.getFloat("voicenote_speed", 2.0f)
        val hasCustomSpeed = voicenoteSpeed != 2.0f
        val speedControl = xprefs.getBoolean("audio_speed_control", true)
        if (!speedControl && !hasCustomSpeed) return

        Unobfuscator.loadPlaybackSpeed(classLoader).hook {
            before {
                val index = ReflectionUtils.findIndexOfType(args, Float::class.javaPrimitiveType!!)
                if (index == -1) return@before
                if (speedControl && changed.get()) {
                    args[index] = audioSpeed.get()
                } else if (hasCustomSpeed && args[index] as? Float == 2.0f) {
                    args[index] = voicenoteSpeed
                }
            }
        }

        val voicenoteClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "VoiceNoteProfileAvatarView"
        )
        val method = ReflectionUtils.findAllMethodsUsingFilter(voicenoteClass) { method1 ->
            method1.parameterCount == 4 && method1.parameterTypes[0] == Int::class.javaPrimitiveType && method1.returnType == Void.TYPE
        }
        method.last().hook {
            after {
                val view = instance as View
                val playback =
                    view.findViewById<TextView>(Utils.getID("fast_playback_overlay", "id"))
                if (speedControl && changed.get()) {
                    playback?.text = String.format("%.1f", audioSpeed.get()).replace(".", ",") + "×"
                } else if (hasCustomSpeed && args[0] as Int == 3) {
                    playback?.text = voicenoteSpeed.toString().replace(".", ",") + "×"
                }
            }
        }

        if (!speedControl) return

        ConversationItemListener.conversationListeners.add(
            object : ConversationItemListener.OnConversationItemListener() {
                override fun onItemBind(
                    fMessage: FMessageWpp,
                    view: ViewGroup,
                    position: Int,
                    convertView: View?
                ) {
                    try {
                        addSpeedSlider(view)
                    } catch (e: Throwable) {
                        logDebug(e)
                    }
                }
            }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addSpeedSlider(viewGroup: ViewGroup) {
        val existingSeekBar = viewGroup.findViewWithTag<View?>(TAG_AUDIO_SEEK)
        val audioPlayerView =
            viewGroup.findViewById<View?>(Utils.getID("conversation_row_audio_player_view", "id"))
        if (audioPlayerView == null) {
            val seekContainer = existingSeekBar?.parent as? View
            (seekContainer?.parent as? ViewGroup)?.removeView(seekContainer)
            return
        }
        if (existingSeekBar != null) return

        val fastPlaybackOverlay =
            viewGroup.findViewById<TextView?>(Utils.getID("fast_playback_overlay", "id")) ?: return
        val mainLayout = viewGroup.findViewById<ViewGroup>(Utils.getID("main_layout", "id")) ?: return
        val context = mainLayout.context

        val seekContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                Utils.dipToPixels(10),
                Utils.dipToPixels(6),
                Utils.dipToPixels(10),
                Utils.dipToPixels(6)
            )
            background = DesignUtils.getDrawable(R.drawable.audio_speed_container_bg)
            visibility = View.GONE
        }

        val speedIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                Utils.dipToPixels(20),
                Utils.dipToPixels(20)
            ).apply { rightMargin = Utils.dipToPixels(8) }
            setImageDrawable(
                DesignUtils.coloredDrawable(
                    DesignUtils.getDrawable(R.drawable.ic_audio_speed),
                    DesignUtils.getUnSeenColor()
                )
            )
        }
        seekContainer.addView(speedIcon)

        val seekBar = SeekBar(context).apply {
            tag = TAG_AUDIO_SEEK
            max = 50
            progress = 1
            splitTrack = false
            thumb = DesignUtils.getDrawable(R.drawable.audio_speed_seekbar_thumb)
            progressDrawable = DesignUtils.getDrawable(R.drawable.audio_speed_seekbar_progress)
            setPadding(
                Utils.dipToPixels(6),
                Utils.dipToPixels(10),
                Utils.dipToPixels(6),
                Utils.dipToPixels(10)
            )
            layoutParams = LinearLayout.LayoutParams(0, Utils.dipToPixels(32), 1f)
        }
        seekContainer.addView(seekBar)

        val showAnimation = AnimationUtil.getAnimation("fade_in")
        val hideAnimation = AnimationUtil.getAnimation("fade_out")
        hideAnimation?.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                seekContainer.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })

        seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                audioSpeed.set(0.1f + (progress * 0.1f))
                changed.set(true)
                try {
                    fastPlaybackOverlay.callOnClick()
                } catch (e: Exception) {
                    logDebug(e)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        viewGroup.findViewById<View>(Utils.getID("control_btn", "id"))?.setOnTouchListener { _, _ ->
            if (seekContainer.isGone) {
                changed.set(false)
            } else {
                audioSpeed.set(0.1f + (seekBar.progress * 0.1f))
                changed.set(true)
            }
            false
        }

        fastPlaybackOverlay.setOnLongClickListener {
            if (seekContainer.isGone) {
                seekContainer.clearAnimation()
                seekContainer.visibility = View.VISIBLE
                showAnimation?.let { seekContainer.startAnimation(it) }
            } else {
                changed.set(false)
                seekContainer.clearAnimation()
                if (hideAnimation != null) {
                    seekContainer.startAnimation(hideAnimation)
                } else {
                    seekContainer.visibility = View.GONE
                }
            }
            true
        }

        val conversationProfile = viewGroup.findViewById<View>(
            Utils.getID("conversation_row_voice_note_profile_avatar", "id")
        ) ?: return
        val conversationRowAudioPlayerView = conversationProfile.parent as? ViewGroup
        val index = mainLayout.indexOfChild(conversationRowAudioPlayerView)
        mainLayout.addView(seekContainer, index + 1)
    }

    override fun getPluginName(): String = "Audio Speed Control"

    companion object {
        private const val TAG_AUDIO_SEEK = "audio_seek"
    }
}
