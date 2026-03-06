package com.wmods.wppenhacer.xposed.features.media;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.wmods.wppenhacer.xposed.bridge.WaeIIFace;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CallRecording extends Feature {

    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean isCallConnected = new AtomicBoolean(false);
    private final AtomicReference<AudioRecord> audioRecordRef = new AtomicReference<>();
    private final AtomicReference<Thread> recordingThreadRef = new AtomicReference<>();
    private final AtomicReference<ParcelFileDescriptor> outputPfdRef = new AtomicReference<>();
    private final AtomicReference<FileOutputStream> outputStreamRef = new AtomicReference<>();
    private final AtomicReference<File> outputFileRef = new AtomicReference<>();
    private final AtomicReference<String> currentPhoneNumber = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> delayedStartFuture = new AtomicReference<>();
    private final AtomicLong payloadSize = new AtomicLong(0L);
    private final ScheduledExecutorService delayedStartScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "WaEnhancer-CallDelayedStart");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean permissionGranted = new AtomicBoolean(false);

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
            var clsCallEventCallback = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith,
                    "VoiceServiceEventCallback");
            if (clsCallEventCallback != null) {
                XposedBridge.log("WaEnhancer: Found VoiceServiceEventCallback: " + clsCallEventCallback.getName());

                try {
                    XposedBridge.hookAllMethods(clsCallEventCallback, "fieldstatsReady", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            handleCallEnded("fieldstatsReady");
                        }
                    });
                    hooksInstalled++;
                } catch (Throwable e) {
                    XposedBridge.log("WaEnhancer: Could not hook fieldstatsReady: " + e.getMessage());
                }

                XposedBridge.hookAllMethods(clsCallEventCallback, "soundPortCreated", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log("WaEnhancer: soundPortCreated - will record after 3s");
                        extractPhoneNumberFromCallback(param.thisObject);
                        isCallConnected.set(true);
                        scheduleDelayedStart();
                    }
                });
                hooksInstalled++;
            }
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: Could not hook VoiceServiceEventCallback: " + e.getMessage());
        }

        // Hook VoipActivity onDestroy for call end
        try {
            var voipActivityClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.Contains,
                    "VoipActivity");
            if (voipActivityClass != null && Activity.class.isAssignableFrom(voipActivityClass)) {
                XposedBridge.log("WaEnhancer: Found VoipActivity: " + voipActivityClass.getName());

                XposedBridge.hookAllMethods(voipActivityClass, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        handleCallEnded("VoipActivity.onDestroy");
                    }
                });
                hooksInstalled++;
            }
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: Could not hook VoipActivity: " + e.getMessage());
        }

        XposedBridge.log("WaEnhancer: Call Recording initialized with " + hooksInstalled + " hooks");
    }

    private void handleCallEnded(@NonNull String reason) {
        XposedBridge.log("WaEnhancer: Call ended by " + reason);
        isCallConnected.set(false);
        cancelDelayedStart();
        stopRecording();
    }

    private void scheduleDelayedStart() {
        cancelDelayedStart();
        ScheduledFuture<?> future = delayedStartScheduler.schedule(() -> {
            if (!isCallConnected.get()) {
                XposedBridge.log("WaEnhancer: Delayed start cancelled, call not connected");
                return;
            }
            if (isRecording.get()) {
                XposedBridge.log("WaEnhancer: Delayed start ignored, already recording");
                return;
            }
            startRecording();
        }, 3, TimeUnit.SECONDS);
        delayedStartFuture.set(future);
    }

    private void cancelDelayedStart() {
        ScheduledFuture<?> future = delayedStartFuture.getAndSet(null);
        if (future != null) {
            future.cancel(true);
        }
    }

    private void extractPhoneNumberFromCallback(Object callback) {
        try {
            Object callInfo = XposedHelpers.callMethod(callback, "getCallInfo");
            if (callInfo == null)
                return;

            Object peerJid = XposedHelpers.getObjectField(callInfo, "peerJid");
            var userJid = new FMessageWpp.UserJid(peerJid);
            if (!userJid.isNull()) {
                String phone = "+" + userJid.getPhoneNumber();
                currentPhoneNumber.set(phone);
                XposedBridge.log("WaEnhancer: Found phone from UserJid: " + phone);
                return;
            }
            Object participantsObj = XposedHelpers.getObjectField(callInfo, "participants");
            if (participantsObj instanceof Map participants) {
                for (Object key : participants.keySet()) {
                    var userJid2 = new FMessageWpp.UserJid(key);
                    if (!userJid2.isNull()) {
                        String phone = "+" + userJid2.getPhoneNumber();
                        currentPhoneNumber.set(phone);
                        XposedBridge.log("WaEnhancer: Found phone from single participant: " + phone);
                        return;
                    }
                }
            }
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: extractPhoneNumber error: " + e.getMessage());
        }
    }

    private void grantVoiceCallPermission() {
        if (permissionGranted.get())
            return;

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

            permissionGranted.set(true);
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: grantVoiceCallPermission error: " + e.getMessage());
        }
    }

    private synchronized void startRecording() {
        if (isRecording.get()) {
            XposedBridge.log("WaEnhancer: Already recording");
            return;
        }

        String phoneNumber = currentPhoneNumber.get();
        if (!shouldRecord(phoneNumber)) {
            XposedBridge.log("WaEnhancer: Skipping recording due to privacy settings for: " + phoneNumber);
            return;
        }

        if (!isCallConnected.get()) {
            XposedBridge.log("WaEnhancer: Skipping recording, call is not connected");
            return;
        }

        AudioRecord selectedAudioRecord = null;
        try {
            if (ContextCompat.checkSelfPermission(FeatureLoader.mApp,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                XposedBridge.log("WaEnhancer: No RECORD_AUDIO permission");
                return;
            }

            WaeIIFace bridge = WppCore.getClientBridge();
            String packageName = FeatureLoader.mApp.getPackageName();
            String appName = packageName.contains("w4b") ? "WA Business" : "WhatsApp";
            String settingsPath = prefs.getString("call_recording_path",
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
            File parentDir = new File(settingsPath, "WA Call Recordings");
            File appDir = new File(parentDir, appName);

            if (!appDir.exists() && !appDir.mkdirs()) {
                boolean dirCreated = bridge.createDir(appDir.getAbsolutePath());
                if (!dirCreated && !appDir.exists()) {
                    throw new IOException("Could not create output directory: " + appDir.getAbsolutePath());
                }
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = (phoneNumber != null && !phoneNumber.isEmpty())
                    ? "Call_" + phoneNumber.replaceAll("[^+0-9]", "") + "_" + timestamp + ".wav"
                    : "Call_" + timestamp + ".wav";

            File file = new File(appDir, fileName);
            ParcelFileDescriptor parcelFileDescriptor = bridge.openFile(file.getAbsolutePath(), true);
            if (parcelFileDescriptor == null) {
                throw new IOException("Bridge openFile returned null for " + file.getAbsolutePath());
            }
            FileOutputStream outputStream = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());
            outputStream.write(new byte[44]);
            outputStream.flush();

            outputFileRef.set(file);
            outputPfdRef.set(parcelFileDescriptor);
            outputStreamRef.set(outputStream);

            boolean useRoot = prefs.getBoolean("call_recording_use_root", false);
            if (useRoot) {
                grantVoiceCallPermission();
            }

            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (minBufferSize <= 0) {
                minBufferSize = SAMPLE_RATE;
            }
            int bufferSize = minBufferSize * 6;
            XposedBridge.log("WaEnhancer: Buffer: " + bufferSize + ", useRoot: " + useRoot);

            int[] audioSources = new int[]{MediaRecorder.AudioSource.VOICE_CALL, MediaRecorder.AudioSource.VOICE_UPLINK,
                    MediaRecorder.AudioSource.VOICE_DOWNLINK, 6, MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.MIC};
            String[] sourceNames = new String[]{"VOICE_CALL", "VOICE_UPLINK", "VOICE_DOWNLINK", "VOICE_RECOGNITION",
                    "VOICE_COMMUNICATION", "MIC"};

            String usedSource = "none";

            for (int i = 0; i < audioSources.length; i++) {
                try {
                    XposedBridge.log("WaEnhancer: Trying " + sourceNames[i]);
                    @SuppressLint("MissingPermission")
                    AudioRecord testRecord = new AudioRecord(audioSources[i], SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT,
                            bufferSize);

                    if (testRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        selectedAudioRecord = testRecord;
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

            if (selectedAudioRecord == null) {
                XposedBridge.log("WaEnhancer: All audio sources failed");
                closeOutputResources(false);
                return;
            }

            audioRecordRef.set(selectedAudioRecord);
            if (!isRecording.compareAndSet(false, true)) {
                selectedAudioRecord.release();
                audioRecordRef.set(null);
                closeOutputResources(false);
                return;
            }

            payloadSize.set(0L);
            selectedAudioRecord.startRecording();
            XposedBridge.log("WaEnhancer: Recording started (" + usedSource + "): " + file.getAbsolutePath());

            final int finalBufferSize = bufferSize;
            Thread recordThread = new Thread(() -> {
                byte[] buffer = new byte[finalBufferSize];
                XposedBridge.log("WaEnhancer: Recording thread started");

                while (isRecording.get()) {
                    try {
                        AudioRecord record = audioRecordRef.get();
                        FileOutputStream stream = outputStreamRef.get();
                        if (record == null || stream == null) {
                            break;
                        }

                        int read = record.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            stream.write(buffer, 0, read);
                            payloadSize.addAndGet(read);
                        } else if (read < 0) {
                            XposedBridge.log("WaEnhancer: Audio read error code: " + read);
                            break;
                        }
                    } catch (IOException e) {
                        XposedBridge.log("WaEnhancer: Recording write error: " + e.getMessage());
                        break;
                    } catch (Throwable t) {
                        XposedBridge.log("WaEnhancer: Recording thread error: " + t.getMessage());
                        break;
                    }
                }
                XposedBridge.log("WaEnhancer: Recording thread ended, bytes: " + payloadSize.get());
            }, "WaEnhancer-RecordingThread");
            recordingThreadRef.set(recordThread);
            recordThread.start();

            if (prefs.getBoolean("call_recording_toast", false)) {
                Utils.showToast("Recording started", Toast.LENGTH_SHORT);
            }

        } catch (Exception e) {
            XposedBridge.log("WaEnhancer: startRecording error: " + e.getMessage());
            isRecording.set(false);
            if (selectedAudioRecord != null) {
                try {
                    selectedAudioRecord.release();
                } catch (Throwable ignored) {
                }
            }
            audioRecordRef.set(null);
            recordingThreadRef.set(null);
            closeOutputResources(true);
            payloadSize.set(0L);
        }
    }

    private synchronized void stopRecording() {
        cancelDelayedStart();
        if (!isRecording.getAndSet(false))
            return;

        try {
            AudioRecord audioRecord = audioRecordRef.getAndSet(null);
            if (audioRecord != null) {
                try {
                    audioRecord.stop();
                } catch (Exception ignored) {
                }
                audioRecord.release();
            }

            Thread recordingThread = recordingThreadRef.getAndSet(null);
            if (recordingThread != null && recordingThread != Thread.currentThread()) {
                recordingThread.join(2000);
            }

            long writtenBytes = payloadSize.get();
            boolean saved = finalizeRecordingFile(writtenBytes);
            File outputFile = outputFileRef.getAndSet(null);

            XposedBridge.log("WaEnhancer: Recording stopped, size: " + writtenBytes +
                    ", file=" + (outputFile != null ? outputFile.getAbsolutePath() : "unknown"));

            if (prefs.getBoolean("call_recording_toast", false)) {
                Utils.showToast(saved ? "Recording saved!" : "Recording failed", Toast.LENGTH_SHORT);
            }

            currentPhoneNumber.set(null);
            payloadSize.set(0L);
        } catch (Exception e) {
            XposedBridge.log("WaEnhancer: stopRecording error: " + e.getMessage());
            closeOutputResources(false);
            outputFileRef.set(null);
        }
    }

    private boolean finalizeRecordingFile(long writtenBytes) {
        FileOutputStream stream = outputStreamRef.getAndSet(null);
        ParcelFileDescriptor pfd = outputPfdRef.getAndSet(null);
        File outputFile = outputFileRef.get();
        if (stream == null) {
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (IOException ignored) {
                }
            }
            return false;
        }

        boolean success = writtenBytes > 0;
        try {
            writeWavHeader(stream, writtenBytes);
            stream.flush();
        } catch (IOException e) {
            XposedBridge.log("WaEnhancer: Failed to write WAV header: " + e.getMessage());
            success = false;
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (IOException ignored) {
                }
            }
        }

        if (success && outputFile != null) {
            Utils.scanFile(outputFile);
        }
        return success;
    }

    private void closeOutputResources(boolean deleteOutputFile) {
        FileOutputStream stream = outputStreamRef.getAndSet(null);
        ParcelFileDescriptor pfd = outputPfdRef.getAndSet(null);
        File outputFile = outputFileRef.getAndSet(null);

        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
        if (pfd != null) {
            try {
                pfd.close();
            } catch (IOException ignored) {
            }
        }

        if (deleteOutputFile && outputFile != null && outputFile.exists() && !outputFile.delete()) {
            XposedBridge.log("WaEnhancer: Could not delete incomplete recording: " + outputFile.getAbsolutePath());
        }
    }

    private void writeWavHeader(@NonNull FileOutputStream stream, long dataSize) throws IOException {
        long clampedDataSize = Math.min(dataSize, 0xFFFFFFFFL);
        long totalDataLen = clampedDataSize + 36;
        long byteRate = (long) SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8;

        stream.getChannel().position(0);
        byte[] header = new byte[44];

        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;
        header[21] = 0;
        header[22] = (byte) CHANNELS;
        header[23] = 0;
        header[24] = (byte) (SAMPLE_RATE & 0xff);
        header[25] = (byte) ((SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) ((SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) ((SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (CHANNELS * BITS_PER_SAMPLE / 8);
        header[33] = 0;
        header[34] = 16;
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (clampedDataSize & 0xff);
        header[41] = (byte) ((clampedDataSize >> 8) & 0xff);
        header[42] = (byte) ((clampedDataSize >> 16) & 0xff);
        header[43] = (byte) ((clampedDataSize >> 24) & 0xff);

        stream.write(header);
    }

    private boolean shouldRecord(String phoneNumber) {
        try {
            int mode = Integer.parseInt(prefs.getString("call_recording_mode", "0"));
            if (mode == 0)
                return true; // Record All

            String blacklist = prefs.getString("call_recording_blacklist", "[]");
            String whitelist = prefs.getString("call_recording_whitelist", "[]");

            if (mode == 2) { // Exclude Selected (Blacklist)
                if (phoneNumber == null)
                    return true;
                String cleanPhone = phoneNumber.replaceAll("[^0-9]", "");
                return !isNumberInList(cleanPhone, blacklist);
            } else if (mode == 3) { // Include Selected (Whitelist)
                if (whitelist.equals("[]") || whitelist.isEmpty())
                    return false;
                if (phoneNumber == null)
                    return false;
                String cleanPhone = phoneNumber.replaceAll("[^0-9]", "");
                return isNumberInList(cleanPhone, whitelist);
            } else if (mode == 1) { // Record Unknown Only
                return true;
            }
        } catch (Exception e) {
            XposedBridge.log("WaEnhancer: shouldRecord check error: " + e.getMessage());
        }
        return true;
    }

    private boolean isNumberInList(String phone, String jsonList) {
        if (TextUtils.isEmpty(jsonList) || jsonList.equals("[]"))
            return false;
        try {
            String content = jsonList.substring(1, jsonList.length() - 1);
            if (content.isEmpty())
                return false;

            String[] numbers = content.split(", ");
            for (String num : numbers) {
                String cleanNum = num.trim().replaceAll("[^0-9]", "");
                if (cleanNum.equals(phone))
                    return true;
            }
        } catch (Exception e) {
            XposedBridge.log("WaEnhancer: Error parsing list: " + e.getMessage());
        }
        return false;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Call Recording";
    }
}
