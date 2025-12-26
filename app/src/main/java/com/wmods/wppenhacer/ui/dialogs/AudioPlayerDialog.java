package com.wmods.wppenhacer.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.wmods.wppenhacer.R;

import java.io.File;
import java.io.IOException;

/**
 * A custom dialog for playing audio files in-app
 */
public class AudioPlayerDialog extends Dialog {
    
    private MediaPlayer mediaPlayer;
    private Handler handler;
    private Runnable updateRunnable;
    
    private SeekBar seekBar;
    private ImageButton btnPlayPause;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private TextView tvTitle;
    
    private boolean isPlaying = false;
    
    public AudioPlayerDialog(Context context, File audioFile) {
        super(context, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog);
        
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_audio_player, null);
        setContentView(view);
        
        // Set dialog window to have proper width
        if (getWindow() != null) {
            getWindow().setLayout(
                (int)(context.getResources().getDisplayMetrics().widthPixels * 0.9),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        // Initialize views
        seekBar = view.findViewById(R.id.seekBar);
        btnPlayPause = view.findViewById(R.id.btn_play_pause);
        tvCurrentTime = view.findViewById(R.id.tv_current_time);
        tvTotalTime = view.findViewById(R.id.tv_total_time);
        tvTitle = view.findViewById(R.id.tv_title);
        ImageButton btnClose = view.findViewById(R.id.btn_close);
        
        // Set title
        tvTitle.setText(audioFile.getName());
        
        // Initialize MediaPlayer
        handler = new Handler(Looper.getMainLooper());
        
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.prepare();
            
            int duration = mediaPlayer.getDuration();
            seekBar.setMax(duration);
            tvTotalTime.setText(formatTime(duration));
            tvCurrentTime.setText(formatTime(0));
            
            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                btnPlayPause.setImageResource(R.drawable.ic_play);
                seekBar.setProgress(0);
                tvCurrentTime.setText(formatTime(0));
                mediaPlayer.seekTo(0);
            });
            
        } catch (IOException e) {
            e.printStackTrace();
            dismiss();
            return;
        }
        
        // Set up click listeners
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnClose.setOnClickListener(v -> dismiss());
        
        // Set up seekbar listener
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    tvCurrentTime.setText(formatTime(progress));
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Update runnable
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && isPlaying) {
                    int currentPosition = mediaPlayer.getCurrentPosition();
                    seekBar.setProgress(currentPosition);
                    tvCurrentTime.setText(formatTime(currentPosition));
                    handler.postDelayed(this, 100);
                }
            }
        };
        
        // Start playing automatically
        togglePlayPause();
        
        // Handle dialog dismiss
        setOnDismissListener(dialog -> releasePlayer());
    }
    
    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        
        if (isPlaying) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(R.drawable.ic_play);
            handler.removeCallbacks(updateRunnable);
        } else {
            mediaPlayer.start();
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            handler.post(updateRunnable);
        }
        isPlaying = !isPlaying;
    }
    
    private void releasePlayer() {
        if (handler != null) {
            handler.removeCallbacks(updateRunnable);
        }
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
    
    private String formatTime(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes >= 60) {
            int hours = minutes / 60;
            minutes = minutes % 60;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }
}
