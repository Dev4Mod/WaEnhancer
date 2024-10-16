package com.wmods.wppenhacer.xposed.features.others;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import org.json.JSONArray;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GoogleTranslate extends Feature {

    private final OkHttpClient client = new OkHttpClient();

    public GoogleTranslate(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("google_translate", false)) return;

        var checkSupportLanguage = Unobfuscator.loadCheckSupportLanguage(classLoader);

        XposedBridge.hookMethod(checkSupportLanguage, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = "pt";
                param.args[1] = "en";
            }
        });

        Class<?> translatorClazz = XposedHelpers.findClass("com.whatsapp.messagetranslation.UnityMessageTranslation", classLoader);

        var pre21Method = XposedHelpers.findMethodExactIfExists(translatorClazz, "translate", String.class);
        if (pre21Method != null) {
            XposedHelpers.findAndHookMethod(translatorClazz, pre21Method.getName(), String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    var texto = (String) param.args[0];
                    var currentMethod = (Method) param.method;
                    var unityTranslationResultClass = currentMethod.getReturnType();
                    var translation = translateGoogle(texto, Locale.getDefault().getLanguage()).get();
                    return unityTranslationResultClass.getConstructor(String.class, float.class, int.class).newInstance(translation, 1, 0);
                }
            });
        }

        var newMethod = XposedHelpers.findMethodExactIfExists(translatorClazz, "translate", List.class);
        if (newMethod != null) {
            XposedHelpers.findAndHookMethod(translatorClazz, "translate", List.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    var list = (List) param.args[0];
                    var translated = new ArrayList<String>();

                    for (var texto : list) {
                        var translation = translateGoogle((String) texto, Locale.getDefault().getLanguage()).get();
                        translated.add(translation);
                    }

                    var currentMethod = (Method) param.method;
                    var unityTranslationResultClass = currentMethod.getReturnType();
                    return unityTranslationResultClass.getConstructor(String[].class, float.class, int.class).newInstance(translated.toArray(new String[0]), 1, 0);
                }
            });
        }

        if (pre21Method == null && newMethod == null) throw new Exception("GoogleTranslate method not found");
    }

    public CompletableFuture<String> translateGoogle(String text, String languageDest) {
        CompletableFuture<String> future = new CompletableFuture<>();
        String url;
        try {
            url = String.format(
                    "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=auto&tl=%s&q=%s",
                    languageDest,
                    URLEncoder.encode(text, "UTF-8")
            );
        } catch (Exception e) {
            future.completeExceptionally(new RuntimeException("Erro ao codificar a URL: " + e.getMessage()));
            return future;
        }

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(new RuntimeException("Erro ao traduzir o texto: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    try {
                        JSONArray jsonArray = new JSONArray(responseData);
                        JSONArray translations = jsonArray.getJSONArray(0);
                        StringBuilder translation = new StringBuilder();

                        for (int i = 0; i < translations.length(); i++) {
                            JSONArray item = translations.getJSONArray(i);
                            translation.append(item.getString(0));
                        }

                        future.complete(translation.toString());
                    } catch (Exception e) {
                        future.completeExceptionally(new RuntimeException("Erro ao processar a resposta: " + e.getMessage()));
                    }
                } else {
                    future.completeExceptionally(new RuntimeException("Resposta não foi bem-sucedida."));
                }
            }
        });

        return future;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "";
    }
}
