package com.wmods.wppenhacer.xposed.features.others;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.DebugUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class AudioTranscript extends Feature {


    public AudioTranscript(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("assemblyai", false) || TextUtils.isEmpty(prefs.getString("assemblyai_key", "")))
            return;

        var transcribeMethod = Unobfuscator.loadTranscribeMethod(classLoader);
        var unkTranscriptClass = Unobfuscator.loadUnkTranscript(classLoader);
        Class<?> TranscriptionSegmentClass = Unobfuscator.loadTranscriptSegment(classLoader);

        XposedBridge.hookMethod(transcribeMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                DebugUtils.debugArgs(param.args);
                var pttTranscriptionRequest = param.args[0];
                var fieldFMessage = ReflectionUtils.getFieldByExtendType(pttTranscriptionRequest.getClass(), FMessageWpp.TYPE);
                var fmessageObj = fieldFMessage.get(pttTranscriptionRequest);
                var fmessage = new FMessageWpp(fmessageObj);
                File file = fmessage.getMediaFile();
                var callback = param.args[1];
                var mEnglishInstance = ReflectionUtils.getFieldByExtendType(unkTranscriptClass, unkTranscriptClass).get(null);
                var onComplete = ReflectionUtils.findMethodUsingFilter(callback.getClass(), method -> method.getParameterCount() == 4);
                String transcript = runTranscript(file);
                var segments = new ArrayList<>();
                var words = transcript.split(" ");
                var totalLength = 0;
                for (var word : words) {
                    segments.add(XposedHelpers.newInstance(TranscriptionSegmentClass, totalLength, word.length(), 100, -1, -1));
                    totalLength += word.length() + 1;
                }
                ReflectionUtils.callMethod(onComplete, callback, mEnglishInstance, fmessageObj, transcript, segments);
                param.setResult(null);
            }
        });

    }

    private String runTranscript(File fileOpus) throws Exception {
        String apiKey = prefs.getString("assemblyai_key", "");
        if (TextUtils.isEmpty(apiKey)) {
            return "API key not provided";
        }

        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = RequestBody.create(fileOpus, MediaType.parse("application/octet-stream"));

        Request uploadRequest = new Request.Builder()
                .url("https://api.assemblyai.com/v2/upload")
                .addHeader("Authorization", apiKey)
                .post(requestBody)
                .build();

        try (okhttp3.Response response = client.newCall(uploadRequest).execute()) {
            if (!response.isSuccessful()) {
                return "Failed to upload audio: " + response.code();
            }

            JSONObject uploadResult = new JSONObject(response.body().string());
            String audioUrl = uploadResult.getString("upload_url");

            JSONObject transcriptionJson = new JSONObject();
            transcriptionJson.put("audio_url", audioUrl);
//            transcriptionJson.put("language_code", Locale.getDefault().getDisplayLanguage());
            transcriptionJson.put("language_detection", true);

            Request transcribeRequest = new Request.Builder()
                    .url("https://api.assemblyai.com/v2/transcript")
                    .addHeader("Authorization", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(transcriptionJson.toString(), MediaType.parse("application/json")))
                    .build();

            try (okhttp3.Response transcribeResponse = client.newCall(transcribeRequest).execute()) {
                if (!transcribeResponse.isSuccessful()) {
                    return "Failed to start transcription: " + transcribeResponse.code();
                }

                JSONObject transcribeResult = new JSONObject(transcribeResponse.body().string());
                String transcriptId = transcribeResult.getString("id");

                String status = "processing";

                while ("processing".equals(status) || "queued".equals(status)) {
                    Thread.sleep(1000);

                    Request checkRequest = new Request.Builder()
                            .url("https://api.assemblyai.com/v2/transcript/" + transcriptId)
                            .addHeader("Authorization", apiKey)
                            .build();

                    try (okhttp3.Response checkResponse = client.newCall(checkRequest).execute()) {
                        if (!checkResponse.isSuccessful()) {
                            return "Failed to check transcription status: " + checkResponse.code();
                        }

                        JSONObject checkResult = new JSONObject(checkResponse.body().string());
                        status = checkResult.getString("status");

                        if ("completed".equals(status)) {
                            return checkResult.getString("text");
                        } else if ("error".equals(status)) {
                            return "Transcription error: " + checkResult.optString("error", "Unknown error");
                        }
                    }
                }
                return "Transcription failed";
            }
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Audio Transcript";
    }
}
