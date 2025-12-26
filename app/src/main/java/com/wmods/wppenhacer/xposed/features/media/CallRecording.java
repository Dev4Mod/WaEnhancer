package com.wmods.wppenhacer.xposed.features.media;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CallRecording extends Feature {

    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private AudioRecord audioRecord;
    private RandomAccessFile randomAccessFile;
    private Thread recordingThread;
    private int payloadSize = 0;
    
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final short CHANNELS = 1;
    private static final short BITS_PER_SAMPLE = 16;

    public CallRecording(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("call_recording_enable", false)) {
            XposedBridge.log("WaEnhancer: Call Recording is disabled");
            return;
        }
        
        XposedBridge.log("WaEnhancer: Call Recording feature initializing...");

        // Hook call state changes using multiple approaches
        hookCallStateChanges();
    }

    private void hookCallStateChanges() {
        int hooksInstalled = 0;
        
        // Approach 1: Hook VoiceServiceEventCallback.fieldstatsReady (call end detection)
        try {
            var clsCallEventCallback = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "VoiceServiceEventCallback");
            if (clsCallEventCallback != null) {
                XposedBridge.log("WaEnhancer: Found VoiceServiceEventCallback: " + clsCallEventCallback.getName());
                XposedBridge.hookAllMethods(clsCallEventCallback, "fieldstatsReady", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("WaEnhancer: fieldstatsReady - Call Ended");
                        stopRecording();
                    }
                });
                hooksInstalled++;
            }
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: Could not hook VoiceServiceEventCallback: " + e.getMessage());
        }

        // Approach 2: Find VoipActivity using Unobfuscator
        try {
            var voipActivityClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.Contains, "VoipActivity");
            if (voipActivityClass != null && Activity.class.isAssignableFrom(voipActivityClass)) {
                hookVoipActivity(voipActivityClass);
                hooksInstalled++;
                XposedBridge.log("WaEnhancer: Hooked VoipActivity via dexkit: " + voipActivityClass.getName());
            }
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: Error finding VoipActivity via dexkit: " + e.getMessage());
        }
        
        // Approach 3: Try known class names with fallbacks
        String[] possibleClassNames = {
            "com.whatsapp.calling.ui.VoipActivityV2",
            "com.whatsapp.voipcalling.VoipActivityV2", 
            "com.whatsapp.voipcalling.VoipActivity",
            "com.whatsapp.calling.VoipActivity",
            "com.whatsapp.voip.VoipActivity"
        };
        
        for (String className : possibleClassNames) {
            try {
                var clazz = XposedHelpers.findClassIfExists(className, classLoader);
                if (clazz != null && Activity.class.isAssignableFrom(clazz)) {
                    hookVoipActivity(clazz);
                    hooksInstalled++;
                    XposedBridge.log("WaEnhancer: Hooked known class: " + className);
                }
            } catch (Throwable ignored) {}
        }
        
        // Approach 4: Hook Voip manager class methods
        try {
            var voipClass = WppCore.getVoipManagerClass(classLoader);
            XposedBridge.log("WaEnhancer: Found Voip manager: " + voipClass.getName());
            
            // Hook all methods to detect call start
            for (var method : voipClass.getDeclaredMethods()) {
                String methodName = method.getName().toLowerCase();
                if (methodName.contains("start") || methodName.contains("accept") || methodName.contains("answer")) {
                    try {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                XposedBridge.log("WaEnhancer: Voip." + method.getName() + " called");
                                startRecording();
                            }
                        });
                        hooksInstalled++;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: Could not hook Voip manager: " + e.getMessage());
        }
        
        // Approach 5: Hook CallInfo class for call state
        try {
            var callInfoClass = WppCore.getVoipCallInfoClass(classLoader);
            XposedBridge.log("WaEnhancer: Found CallInfo: " + callInfoClass.getName());
            XposedBridge.hookAllConstructors(callInfoClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("WaEnhancer: CallInfo created - call starting");
                    startRecording();
                }
            });
            hooksInstalled++;
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: Could not hook CallInfo: " + e.getMessage());
        }
        
        XposedBridge.log("WaEnhancer: Call Recording initialized with " + hooksInstalled + " hooks");
    }
    
    private void hookVoipActivity(Class<?> activityClass) {
        XposedBridge.hookAllMethods(activityClass, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("WaEnhancer: VoipActivity.onResume - Call Active");
                startRecording();
            }
        });
        
        XposedBridge.hookAllMethods(activityClass, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("WaEnhancer: VoipActivity.onDestroy - Call Ended");
                stopRecording();
            }
        });
        
        XposedBridge.hookAllMethods(activityClass, "onStop", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("WaEnhancer: VoipActivity.onStop");
            }
        });
    }

    private synchronized void startRecording() {
        if (isRecording.get()) {
            XposedBridge.log("WaEnhancer: Already recording, skipping");
            return;
        }
        
        try {
            // Check microphone permission
            if (ContextCompat.checkSelfPermission(FeatureLoader.mApp, Manifest.permission.RECORD_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                XposedBridge.log("WaEnhancer: No RECORD_AUDIO permission");
                Utils.showToast("WaEnhancer: No mic permission", android.widget.Toast.LENGTH_SHORT);
                return;
            }
            
            String packageName = FeatureLoader.mApp.getPackageName();
            String appName = packageName.contains("w4b") ? "WA Business" : "WhatsApp";
            
            // Get base path from preferences, or use root if MANAGE_EXTERNAL_STORAGE granted
            File parentDir;
            if (android.os.Environment.isExternalStorageManager()) {
                // Use root folder: /sdcard/WA Call Recordings/
                parentDir = new File(android.os.Environment.getExternalStorageDirectory(), "WA Call Recordings");
            } else {
                // Use path from settings or fallback to app files dir
                String settingsPath = prefs.getString("call_recording_path", null);
                if (settingsPath != null && !settingsPath.isEmpty()) {
                    parentDir = new File(settingsPath, "WA Call Recordings");
                } else {
                    parentDir = new File(FeatureLoader.mApp.getExternalFilesDir(null), "Recordings");
                }
            }
            
            // Folder structure: WA Call Recordings/[WhatsApp|WA Business]/Voice/
            File dir = new File(parentDir, appName + "/Voice");
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    XposedBridge.log("WaEnhancer: Failed to create directory: " + dir.getAbsolutePath());
                    // Fallback to app files dir
                    dir = new File(FeatureLoader.mApp.getExternalFilesDir(null), "Recordings/" + appName + "/Voice");
                    if (!dir.exists() && !dir.mkdirs()) {
                        Utils.showToast("WaEnhancer: Dir creation failed", android.widget.Toast.LENGTH_LONG);
                        return;
                    }
                }
            }
            
            String fileName = "Call_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".wav";
            File file = new File(dir, fileName);
            randomAccessFile = new RandomAccessFile(file, "rw");
            
            // Write placeholder WAV header (44 bytes)
            randomAccessFile.setLength(0);
            randomAccessFile.write(new byte[44]);
            
            // Calculate buffer size
            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            int bufferSize = Math.max(minBufferSize * 2, 8192);
            
            // Create AudioRecord with VOICE_COMMUNICATION source
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            );
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                XposedBridge.log("WaEnhancer: AudioRecord failed to initialize, trying MIC source");
                audioRecord.release();
                
                // Fallback to MIC source
                audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                );
                
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    XposedBridge.log("WaEnhancer: AudioRecord still failed to initialize");
                    Utils.showToast("WaEnhancer: AudioRecord init failed", android.widget.Toast.LENGTH_LONG);
                    return;
                }
            }
            
            isRecording.set(true);
            payloadSize = 0;
            
            audioRecord.startRecording();
            
            // Start recording thread
            final int finalBufferSize = bufferSize;
            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[finalBufferSize];
                while (isRecording.get()) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        try {
                            synchronized (CallRecording.this) {
                                if (randomAccessFile != null) {
                                    randomAccessFile.write(buffer, 0, read);
                                    payloadSize += read;
                                }
                            }
                        } catch (IOException e) {
                            XposedBridge.log(e);
                        }
                    }
                }
            }, "WaEnhancer-RecordingThread");
            recordingThread.start();
            
            XposedBridge.log("WaEnhancer: Recording started: " + file.getAbsolutePath());
            Utils.showToast("Recording: " + fileName, android.widget.Toast.LENGTH_SHORT);
            
        } catch (Exception e) {
            XposedBridge.log("WaEnhancer: startRecording error: " + e.getMessage());
            XposedBridge.log(e);
            Utils.showToast("Rec Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG);
        }
    }

    private synchronized void stopRecording() {
        if (!isRecording.get()) {
            return;
        }
        
        isRecording.set(false);
        
        try {
            // Wait for recording thread to finish
            if (recordingThread != null) {
                recordingThread.join(1000);
                recordingThread = null;
            }
            
            // Stop and release AudioRecord
            if (audioRecord != null) {
                try {
                    audioRecord.stop();
                } catch (Exception ignored) {}
                audioRecord.release();
                audioRecord = null;
            }
            
            // Write WAV header and close file
            if (randomAccessFile != null) {
                writeWavHeader();
                randomAccessFile.close();
                randomAccessFile = null;
            }
            
            XposedBridge.log("WaEnhancer: Recording stopped, size: " + payloadSize + " bytes");
            if (payloadSize > 1000) {
                Utils.showToast("Recording saved!", android.widget.Toast.LENGTH_SHORT);
            }
            
        } catch (Exception e) {
            XposedBridge.log("WaEnhancer: stopRecording error: " + e.getMessage());
            XposedBridge.log(e);
        }
    }

    private void writeWavHeader() throws IOException {
        long totalDataLen = payloadSize + 36;
        long byteRate = (long) SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8;
        
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
        header[22] = (byte) CHANNELS; header[23] = 0;
        header[24] = (byte) (SAMPLE_RATE & 0xff);
        header[25] = (byte) ((SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) ((SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) ((SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (CHANNELS * BITS_PER_SAMPLE / 8); header[33] = 0;
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
