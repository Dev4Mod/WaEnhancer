package com.wmods.wppenhacer.xposed.features.media;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Environment;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CallRecording extends Feature {

    private boolean isRecording = false;
    private RandomAccessFile randomAccessFile;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String outputDir;
    private int payloadSize = 0;
    private int sampleRate = 16000; // Default, updated from hook
    private short channels = 1;
    private final short bitsPerSample = 16;

    public CallRecording(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("call_recording_enable", false)) return;
        outputDir = prefs.getString("call_recording_path", Environment.getExternalStorageDirectory() + "/Music/WaEnhancer/Recordings");

        // Hook AudioRecord Constructor to get Sample Rate / Channels
        XposedBridge.hookAllConstructors(AudioRecord.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                int source = (int) param.args[0];
                XposedBridge.log("WaEnhancer: AudioRecord Source " + source);
                if (source == android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION) {
                   sampleRate = (int) param.args[1];
                   int channelConfig = (int) param.args[2];
                   channels = (short) (channelConfig == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1);
                   startRecording();
                }
            }
        });

        XposedHelpers.findAndHookMethod(AudioRecord.class, "startRecording", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                startRecording();
            }
        });

        XposedHelpers.findAndHookMethod(AudioRecord.class, "stop", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                stopRecording();
            }
        });

         XposedHelpers.findAndHookMethod(AudioRecord.class, "release", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
               stopRecording();
            }
        });

        XposedHelpers.findAndHookMethod(AudioRecord.class, "read", byte[].class, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isRecording || randomAccessFile == null) return;
                int result = (int) param.getResult();
                if (result > 0) {
                    byte[] data = (byte[]) param.args[0];
                    writeAsync(data, 0, result);
                }
            }
        });
        
        // Note: For now, we are capturing the generic VOICE_COMMUNICATION source. 
        // Hooking AudioTrack for the downlink is possible but requires synchronizing two streams 
        // which often drift. The Microphone source in 'VOICE_COMMUNICATION' mode often includes
        // the other party's audio on many modern devices due to echo cancellation loopback. 
        // If users report only one-sided audio, we will implement the dual-file capture strategy.
    }

    private synchronized void startRecording() {
        if (isRecording) return;
        try {
            String packageName = com.wmods.wppenhacer.xposed.core.FeatureLoader.mApp.getPackageName();
            String appName = packageName.contains("w4b") ? "WA Business" : "WhatsApp";
            
            File parentDir;
            if (Environment.isExternalStorageManager()) {
                 parentDir = new File(Environment.getExternalStorageDirectory(), "WA Call Recordings");
            } else {
                 // Fallback to safe external storage (Android/data/com.whatsapp/files/Recordings)
                 parentDir = new File(com.wmods.wppenhacer.xposed.core.FeatureLoader.mApp.getExternalFilesDir(null), "Recordings");
            }
            
            File dir = new File(parentDir, appName + "/Audio");
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    XposedBridge.log("WaEnhancer: Failed to create directory: " + dir.getAbsolutePath());
                    Utils.showToast("WaEnhancer: RW Error " + dir.getAbsolutePath(), android.widget.Toast.LENGTH_LONG);
                    return;
                }
            }
            
            String fileName = "Call_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".wav";
            File file = new File(dir, fileName);
            randomAccessFile = new RandomAccessFile(file, "rw");
            
            randomAccessFile.setLength(0); 
            randomAccessFile.write(new byte[44]); 
            
            isRecording = true;
            payloadSize = 0;
            XposedBridge.log("WaEnhancer: Recording started: " + file.getAbsolutePath());
            Utils.showToast("rec: " + file.getName(), android.widget.Toast.LENGTH_SHORT);
        } catch (Exception e) {
            XposedBridge.log(e);
            Utils.showToast("Rec Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG);
        }
    }

    private synchronized void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        try {
            if (randomAccessFile != null) {
                writeWavHeader();
                randomAccessFile.close();
            }
        } catch (IOException e) {
            XposedBridge.log(e);
        }
        randomAccessFile = null;
    }

    private void writeAsync(byte[] data, int offset, int length) {
        executor.execute(() -> {
            try {
                if (randomAccessFile != null) {
                    randomAccessFile.write(data, offset, length);
                    payloadSize += length;
                }
            } catch (IOException e) {
                XposedBridge.log(e);
            }
        });
    }

    private void writeWavHeader() throws IOException {
        long totalDataLen = payloadSize + 36;
        long byteRate = (long) sampleRate * channels * bitsPerSample / 8;
        
        randomAccessFile.seek(0);
        byte[] header = new byte[44];
        
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * bitsPerSample / 8); header[33] = 0;
        header[34] = 16; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (payloadSize & 0xff);
        header[41] = (byte) ((payloadSize >> 8) & 0xff);
        header[42] = (byte) ((payloadSize >> 16) & 0xff);
        header[43] = (byte) ((payloadSize >> 24) & 0xff);

        randomAccessFile.write(header);
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Call Recording";
    }
}
