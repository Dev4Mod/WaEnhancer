package com.wmods.wppenhacer.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.wmods.wppenhacer.R
import java.io.File
import java.io.IOException
import java.util.Locale

class AudioPlayerDialog(context: Context, audioFile: File) :
    Dialog(context, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog) {

    private var mediaPlayer: MediaPlayer? = null
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    private val seekBar: SeekBar
    private val btnPlayPause: ImageButton
    private val tvCurrentTime: TextView
    private val tvTotalTime: TextView
    private val tvTitle: TextView

    private var isPlaying = false
    private var isPrepared = false

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_audio_player, null)
        setContentView(view)

        window?.let { win ->
            val displayMetrics = context.resources.displayMetrics
            win.setLayout(
                (displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            win.setBackgroundDrawableResource(android.R.color.transparent)
        }

        seekBar = view.findViewById(R.id.seekBar)
        btnPlayPause = view.findViewById(R.id.btn_play_pause)
        tvCurrentTime = view.findViewById(R.id.tv_current_time)
        tvTotalTime = view.findViewById(R.id.tv_total_time)
        tvTitle = view.findViewById(R.id.tv_title)
        val btnClose = view.findViewById<ImageButton>(R.id.btn_close)

        tvTitle.text = audioFile.name
        btnPlayPause.isEnabled = false

        try {
            val mp = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnPreparedListener { player ->
                    isPrepared = true
                    btnPlayPause.isEnabled = true
                    val duration = player.duration
                    seekBar.max = duration
                    tvTotalTime.text = formatTime(duration)
                    tvCurrentTime.text = formatTime(0)
                    togglePlayPause()
                }
                setOnCompletionListener {
                    this@AudioPlayerDialog.isPlaying = false
                    btnPlayPause.setImageResource(R.drawable.ic_play)
                    seekBar.progress = 0
                    tvCurrentTime.text = formatTime(0)
                    it.seekTo(0)
                }
            }
            mediaPlayer = mp
            mp.prepareAsync()
        } catch (e: IOException) {
            e.printStackTrace()
            dismiss()
        }

        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnClose.setOnClickListener { dismiss() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer != null && isPrepared) {
                    mediaPlayer?.seekTo(progress)
                    tvCurrentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        updateRunnable = object : Runnable {
            override fun run() {
                val mp = mediaPlayer
                if (mp != null && isPlaying && isPrepared) {
                    val currentPosition = mp.currentPosition
                    seekBar.progress = currentPosition
                    tvCurrentTime.text = formatTime(currentPosition)
                    handler.postDelayed(this, 100)
                }
            }
        }

        setOnDismissListener { releasePlayer() }
    }

    private fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (!isPrepared) return

        if (isPlaying) {
            mp.pause()
            btnPlayPause.setImageResource(R.drawable.ic_play)
            updateRunnable?.let { handler.removeCallbacks(it) }
        } else {
            mp.start()
            btnPlayPause.setImageResource(R.drawable.ic_pause)
            updateRunnable?.let { handler.post(it) }
        }
        isPlaying = !isPlaying
    }

    private fun releasePlayer() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.stop()
            }
            mp.release()
        }
        mediaPlayer = null
        isPrepared = false
    }

    private fun formatTime(millis: Int): String {
        var seconds = millis / 1000
        var minutes = seconds / 60
        seconds %= 60

        return if (minutes >= 60) {
            val hours = minutes / 60
            minutes %= 60
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }
}
