package com.wmods.wppenhacer.xposed.features.others;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.assemblyai.api.AssemblyAI;
import com.assemblyai.api.resources.transcripts.types.Transcript;
import com.assemblyai.api.resources.transcripts.types.TranscriptOptionalParams;
import com.assemblyai.api.resources.transcripts.types.TranscriptStatus;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.io.File;
import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AudioTranscript extends Feature {


    public AudioTranscript(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("assemblyai", false) || TextUtils.isEmpty(prefs.getString("assemblyai_key", "")))
            return;

        var transcribeMethod = Unobfuscator.loadTranscribeMethod(classLoader);
        var mediaClass = Unobfuscator.loadStatusDownloadMediaClass(classLoader);
        var fileField = Unobfuscator.loadStatusDownloadFileField(classLoader);

        XposedBridge.hookMethod(transcribeMethod, new XC_MethodHook() {
            private Unhook unhooked;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var pttTranscriptionRequest = param.args[0];
                var message = pttTranscriptionRequest.getClass().getField("A01").get(pttTranscriptionRequest);
                var field = mediaClass.getField("A01");
                var mediaObj = field.get(message);
                File file = (File) fileField.get(mediaObj);

                unhooked = XposedHelpers.findAndHookMethod("com.whatsapp.unity.UnityLib", classLoader, "transcribeAudio", classLoader.loadClass("java.lang.String"), classLoader.loadClass("java.lang.String"), classLoader.loadClass("java.lang.String"), classLoader.loadClass("com.whatsapp.unity.UnityTranscriptionListener"), new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        var handleResult = param.args[3];
                        var methodComplete = ReflectionUtils.findMethodUsingFilter(handleResult.getClass(), method -> method.getName().equals("onComplete"));
                        var methodOnError = ReflectionUtils.findMethodUsingFilter(handleResult.getClass(), method -> method.getName().equals("onError"));
                        var methodOnSegmentResult = ReflectionUtils.findMethodUsingFilter(handleResult.getClass(), method -> method.getName().equals("onSegmentResult"));
                        try {
                            String transcript = runTranscript(file);
                            ReflectionUtils.callMethod(methodOnSegmentResult, handleResult, transcript, 1, 0);
                            ReflectionUtils.callMethod(methodComplete, handleResult, new HashMap<>());
                        } catch (Exception e) {
                            ReflectionUtils.callMethod(methodOnError, handleResult, 2);
                        }
                        param.setResult(null);
                    }
                });
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (unhooked != null) unhooked.unhook();
            }
        });

    }

    private String runTranscript(File fileOpus) throws Exception {

        AssemblyAI client = AssemblyAI.builder().apiKey(prefs.getString("assemblyai_key", "")).build();

        var params = TranscriptOptionalParams.builder().languageDetection(true).build();

        Transcript transcript = client.transcripts().transcribe(fileOpus, params);

        if (transcript.getStatus().equals(TranscriptStatus.ERROR)) {
            return transcript.getError().get();
        }
        return transcript.getText().get();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Audio Transcript";
    }
}
