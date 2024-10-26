package com.wmods.wppenhacer.xposed.features.others;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.assemblyai.api.AssemblyAI;
import com.assemblyai.api.resources.transcripts.types.Transcript;
import com.assemblyai.api.resources.transcripts.types.TranscriptOptionalParams;
import com.assemblyai.api.resources.transcripts.types.TranscriptStatus;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.io.File;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class AudioTranscript extends Feature {


    public AudioTranscript(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("assemblyai", false) || TextUtils.isEmpty(prefs.getString("assemblyai_key", "")))
            return;


        var transcribeMethod = Unobfuscator.loadTranscribeMethod(classLoader);
        var unkTranscript = Unobfuscator.loadUnkTranscript(classLoader);

        XposedBridge.hookMethod(transcribeMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var pttTranscriptionRequest = param.args[0];
                var fieldFMessage = ReflectionUtils.getFieldByExtendType(pttTranscriptionRequest.getClass(), FMessageWpp.TYPE);
                var fmessageObj = fieldFMessage.get(pttTranscriptionRequest);
                var fmessage = new FMessageWpp(fmessageObj);
                File file = fmessage.getMediaFile();
                var callback = param.args[1];
                var onComplete = ReflectionUtils.findMethodUsingFilter(callback.getClass(), method -> method.getParameterCount() == 4);
                String transcript = runTranscript(file);
                var unkTranscriptInstance = unkTranscript.getField("A00").get(null);
                ReflectionUtils.callMethod(onComplete, callback, unkTranscriptInstance, fmessageObj, transcript, new ArrayList<>());
                param.setResult(null);
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
