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
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private final AtomicBoolean isCallConnected = new AtomicBoolean(false);
    private AudioRecord audioRecord;
    private RandomAccessFile randomAccessFile;
    private Thread recordingThread;
    private int payloadSize = 0;
    private volatile String currentPhoneNumber = null;
    private static boolean permissionGranted = false;
    
    private static final int SAMPLE_RATE = 48000;
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
        hookCallStateChanges();
    }

    private void hookCallStateChanges() {
        int hooksInstalled = 0;
        
        try {
            var clsCallEventCallback = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "VoiceServiceEventCallback");
            if (clsCallEventCallback != null) {
                XposedBridge.log("WaEnhancer: Found VoiceServiceEventCallback: " + clsCallEventCallback.getName());
                
                // Hook ALL methods to discover which ones fire during call
                for (Method method : clsCallEventCallback.getDeclaredMethods()) {
                    final String methodName = method.getName();
                    try {
                        XposedBridge.hookAllMethods(clsCallEventCallback, methodName, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                XposedBridge.log("WaEnhancer: VoiceCallback." + methodName + "()");
                                
                                // Handle call end
                                if (methodName.equals("fieldstatsReady")) {
                                    isCallConnected.set(false);
                                    stopRecording();
                                }
                            }
                        });
                        hooksInstalled++;
                    } catch (Throwable ignored) {}
                }
                
                // Hook soundPortCreated with 3 second delay to wait for call connection
                XposedBridge.hookAllMethods(clsCallEventCallback, "soundPortCreated", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("WaEnhancer: soundPortCreated - will record after 3s");
                        extractPhoneNumberFromCallback(param.thisObject);
                        
                        final Object callback = param.thisObject;
                        new Thread(() -> {
                            try {
                                Thread.sleep(3000);
                                if (!isRecording.get()) {
                                    XposedBridge.log("WaEnhancer: Starting recording after delay");
                                    extractPhoneNumberFromCallback(callback);
                                    isCallConnected.set(true);
                                    startRecording();
                                }
                            } catch (Exception e) {
                                XposedBridge.log("WaEnhancer: Delay error: " + e.getMessage());
                            }
                        }).start();
                    }
                });
            }
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: Could not hook VoiceServiceEventCallback: " + e.getMessage());
        }

        // Hook VoipActivity onDestroy for call end
        try {
            var voipActivityClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.Contains, "VoipActivity");
            if (voipActivityClass != null && Activity.class.isAssignableFrom(voipActivityClass)) {
                XposedBridge.log("WaEnhancer: Found VoipActivity: " + voipActivityClass.getName());
                
                XposedBridge.hookAllMethods(voipActivityClass, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("WaEnhancer: VoipActivity.onDestroy");
                        isCallConnected.set(false);
                        stopRecording();
                    }
                });
                hooksInstalled++;
            }
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: Could not hook VoipActivity: " + e.getMessage());
        }
        
        XposedBridge.log("WaEnhancer: Call Recording initialized with " + hooksInstalled + " hooks");
    }
    
    private void extractPhoneNumberFromCallback(Object callback) {
        try {
            Object callInfo = XposedHelpers.callMethod(callback, "getCallInfo");
            if (callInfo == null) return;
            
            // Try to get peerJid and resolve LID to phone number
            try {
                Object peerJid = XposedHelpers.getObjectField(callInfo, "peerJid");
                if (peerJid != null) {
                    String peerStr = peerJid.toString();
                    XposedBridge.log("WaEnhancer: peerJid = " + peerStr);
                    
                    // Check if it's a LID format
                    if (peerStr.contains("@lid")) {
                        // Try to get phone from the Jid object
                        try {
                            Object userMethod = XposedHelpers.callMethod(peerJid, "getUser");
                            XposedBridge.log("WaEnhancer: peerJid.getUser() = " + userMethod);
                        } catch (Throwable ignored) {}
                        
                        // Try toPhoneNumber or similar
                        try {
                            Object phone = XposedHelpers.callMethod(peerJid, "toPhoneNumber");
                            if (phone != null) {
                                currentPhoneNumber = "+" + phone.toString();
                                XposedBridge.log("WaEnhancer: Found phone from toPhoneNumber: " + currentPhoneNumber);
                                return;
                            }
                        } catch (Throwable ignored) {}
                    }
                    
                    // Check if it's already a phone number format
                    if (peerStr.contains("@s.whatsapp.net") || peerStr.contains("@c.us")) {
                        String number = peerStr.split("@")[0];
                        if (number.matches("\\d{6,15}")) {
                            currentPhoneNumber = "+" + number;
                            XposedBridge.log("WaEnhancer: Found phone: " + currentPhoneNumber);
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            
            // Search participants map for phone numbers
            try {
                Object participants = XposedHelpers.getObjectField(callInfo, "participants");
                if (participants != null) {
                    XposedBridge.log("WaEnhancer: Participants = " + participants.toString());
                    
                    if (participants instanceof java.util.Map) {
                        java.util.Map<?, ?> map = (java.util.Map<?, ?>) participants;
                        for (Object key : map.keySet()) {
                            String keyStr = key.toString();
                            XposedBridge.log("WaEnhancer: Participant key = " + keyStr);
                            
                            // Check if key contains phone number
                            if (keyStr.contains("@s.whatsapp.net") || keyStr.contains("@c.us")) {
                                String number = keyStr.split("@")[0];
                                if (number.matches("\\d{6,15}")) {
                                    // Skip if it's the self number (creatorJid)
                                    Object creatorJid = XposedHelpers.getObjectField(callInfo, "creatorJid");
                                    if (creatorJid != null && keyStr.equals(creatorJid.toString())) {
                                        continue;
                                    }
                                    currentPhoneNumber = "+" + number;
                                    XposedBridge.log("WaEnhancer: Found phone from participants: " + currentPhoneNumber);
                                    return;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
            
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: extractPhoneNumber error: " + e.getMessage());
        }
    }

    private void grantVoiceCallPermission() {
        if (permissionGranted) return;
        
        try {
            String packageName = FeatureLoader.mApp.getPackageName();
            XposedBridge.log("WaEnhancer: Granting CAPTURE_AUDIO_OUTPUT via root");
            
            String[] commands = {
                "pm grant " + packageName + " android.permission.CAPTURE_AUDIO_OUTPUT",
                "appops set " + packageName + " RECORD_AUDIO allow",
            };
            
            for (String cmd : commands) {
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                    int exitCode = process.waitFor();
                    XposedBridge.log("WaEnhancer: " + cmd + " exit: " + exitCode);
                } catch (Exception e) {
                    XposedBridge.log("WaEnhancer: Root failed: " + e.getMessage());
                }
            }
            
            permissionGranted = true;
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: grantVoiceCallPermission error: " + e.getMessage());
        }
    }

    private synchronized void startRecording() {
        if (isRecording.get()) {
            XposedBridge.log("WaEnhancer: Already recording");
            return;
        }
        
        try {
            if (ContextCompat.checkSelfPermission(FeatureLoader.mApp, Manifest.permission.RECORD_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                XposedBridge.log("WaEnhancer: No RECORD_AUDIO permission");
                return;
            }
            
            String packageName = FeatureLoader.mApp.getPackageName();
            String appName = packageName.contains("w4b") ? "WA Business" : "WhatsApp";
            
            File parentDir;
            if (android.os.Environment.isExternalStorageManager()) {
                parentDir = new File(android.os.Environment.getExternalStorageDirectory(), "WA Call Recordings");
            } else {
                String settingsPath = prefs.getString("call_recording_path", null);
                if (settingsPath != null && !settingsPath.isEmpty()) {
                    parentDir = new File(settingsPath, "WA Call Recordings");
                } else {
                    parentDir = new File(FeatureLoader.mApp.getExternalFilesDir(null), "Recordings");
                }
            }
            
            File dir = new File(parentDir, appName + "/Voice");
            if (!dir.exists() && !dir.mkdirs()) {
                dir = new File(FeatureLoader.mApp.getExternalFilesDir(null), "Recordings/" + appName + "/Voice");
                dir.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = (currentPhoneNumber != null && !currentPhoneNumber.isEmpty()) 
                ? "Call_" + currentPhoneNumber.replaceAll("[^+0-9]", "") + "_" + timestamp + ".wav"
                : "Call_" + timestamp + ".wav";
            
            File file = new File(dir, fileName);
            randomAccessFile = new RandomAccessFile(file, "rw");
            randomAccessFile.setLength(0);
            randomAccessFile.write(new byte[44]);
            
            boolean useRoot = prefs.getBoolean("call_recording_use_root", false);
            if (useRoot) {
                grantVoiceCallPermission();
            }
            
            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            int bufferSize = minBufferSize * 6;
            XposedBridge.log("WaEnhancer: Buffer: " + bufferSize + ", useRoot: " + useRoot);
            
            int[] audioSources = useRoot 
                ? new int[]{MediaRecorder.AudioSource.VOICE_CALL, 6, MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC}
                : new int[]{6, MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC};
            String[] sourceNames = useRoot
                ? new String[]{"VOICE_CALL", "VOICE_RECOGNITION", "VOICE_COMMUNICATION", "MIC"}
                : new String[]{"VOICE_RECOGNITION", "VOICE_COMMUNICATION", "MIC"};
            
            audioRecord = null;
            String usedSource = "none";
            
            for (int i = 0; i < audioSources.length; i++) {
                try {
                    XposedBridge.log("WaEnhancer: Trying " + sourceNames[i]);
                    AudioRecord testRecord = new AudioRecord(audioSources[i], SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
                    if (testRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        audioRecord = testRecord;
                        usedSource = sourceNames[i];
                        XposedBridge.log("WaEnhancer: SUCCESS " + sourceNames[i]);
                        break;
                    }
                    testRecord.release();
                    XposedBridge.log("WaEnhancer: FAILED " + sourceNames[i]);
                } catch (Throwable t) {
                    XposedBridge.log("WaEnhancer: Exception " + sourceNames[i] + ": " + t.getMessage());
                }
            }
            
            if (audioRecord == null) {
                XposedBridge.log("WaEnhancer: All audio sources failed");
                return;
            }
            
            isRecording.set(true);
            payloadSize = 0;
            audioRecord.startRecording();
            XposedBridge.log("WaEnhancer: Recording started (" + usedSource + "): " + file.getAbsolutePath());
            
            final int finalBufferSize = bufferSize;
            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[finalBufferSize];
                XposedBridge.log("WaEnhancer: Recording thread started");
                
                while (isRecording.get() && audioRecord != null) {
                    try {
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            synchronized (CallRecording.this) {
                                if (randomAccessFile != null) {
                                    randomAccessFile.write(buffer, 0, read);
                                    payloadSize += read;
                                }
                            }
                        } else if (read < 0) {
                            break;
                        }
                    } catch (IOException e) {
                        break;
                    }
                }
                XposedBridge.log("WaEnhancer: Recording thread ended, bytes: " + payloadSize);
            }, "WaEnhancer-RecordingThread");
            recordingThread.start();
            
            if (prefs.getBoolean("call_recording_toast", true)) {
                Utils.showToast("Recording started", android.widget.Toast.LENGTH_SHORT);
            }
            
        } catch (Exception e) {
            XposedBridge.log("WaEnhancer: startRecording error: " + e.getMessage());
        }
    }

    private synchronized void stopRecording() {
        if (!isRecording.get()) return;
        
        isRecording.set(false);
        
        try {
            if (audioRecord != null) {
                try { audioRecord.stop(); } catch (Exception ignored) {}
                audioRecord.release();
                audioRecord = null;
            }
            
            if (recordingThread != null) {
                recordingThread.join(2000);
                recordingThread = null;
            }
            
            if (randomAccessFile != null) {
                writeWavHeader();
                randomAccessFile.close();
                randomAccessFile = null;
            }
            
            XposedBridge.log("WaEnhancer: Recording stopped, size: " + payloadSize);
            
            if (prefs.getBoolean("call_recording_toast", true)) {
                Utils.showToast(payloadSize > 1000 ? "Recording saved!" : "Recording failed", android.widget.Toast.LENGTH_SHORT);
            }
            
            currentPhoneNumber = null;
        } catch (Exception e) {
            XposedBridge.log("WaEnhancer: stopRecording error: " + e.getMessage());
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
