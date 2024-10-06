package com.wmods.wppenhacer.xposed.features.media;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.HKDF;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okio.BufferedSink;
import okio.Okio;


public class MediaPreview extends Feature {

    private static final String HTML_LOADING = "<!DOCTYPE html><html><head> <meta charset=\"UTF-8\"> <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"> <title>Loading</title> <style> body { display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background-color: #f0f0f0; font-family: Arial, sans-serif; } .loader { display: flex; align-items: center; } .spinner { width: 40px; height: 40px; border: 4px solid rgba(0, 0, 0, 0.1); border-top: 4px solid #000; border-radius: 50%; animation: spin 1s linear infinite; } @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } } .text { margin-left: 10px; font-size: 18px; } </style></head><body> <div class=\"loader\"> <div class=\"spinner\"></div> <div class=\"text\">$loading</div> </div></body></html>";
    private static final String HTML_VIDEO = "<!DOCTYPE html><html><head> <meta charset=\"UTF-8\"> <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"> <title>Player de Vídeo</title> <style> body { display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background-color: #f0f0f0; font-family: Arial, sans-serif; } .video-container { text-align: center; } video { width: 100%; height: auto; } </style></head><body> <div class=\"video-container\"> <video controls> <source src=\"$url\" type=\"video/mp4\"> Browser not supported. </video> </div></body></html>";
    private static final String HTML_IMAGE = "<!DOCTYPE html><html><head> <meta charset=\"UTF-8\"> <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"> <title>Image</title> <style> body { display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background-color: #f0f0f0; font-family: Arial, sans-serif; } .full-screen-image { width: 100%; height: auto;} </style></head><body> <img src=\"$url\" class=\"full-screen-image\"></body></html>";
    private File filePath;
    private AlertDialog dialog;

    static HashMap<String, byte[]> MEDIA_KEYS = new HashMap<>();

    static {
        MEDIA_KEYS.put("image", "WhatsApp Image Keys".getBytes());
        MEDIA_KEYS.put("video", "WhatsApp Video Keys".getBytes());
        MEDIA_KEYS.put("audio", "WhatsApp Audio Keys".getBytes());
        MEDIA_KEYS.put("document", "WhatsApp Document Keys".getBytes());
        MEDIA_KEYS.put("image/webp", "WhatsApp Image Keys".getBytes());
        MEDIA_KEYS.put("image/jpeg", "WhatsApp Image Keys".getBytes());
        MEDIA_KEYS.put("image/png", "WhatsApp Image Keys".getBytes());
        MEDIA_KEYS.put("video/mp4", "WhatsApp Video Keys".getBytes());
        MEDIA_KEYS.put("audio/aac", "WhatsApp Audio Keys".getBytes());
        MEDIA_KEYS.put("audio/ogg", "WhatsApp Audio Keys".getBytes());
        MEDIA_KEYS.put("audio/wav", "WhatsApp Audio Keys".getBytes());
    }

    public MediaPreview(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("media_preview", true)) return;

        var videoViewContainerClass = Unobfuscator.loadVideoViewContainerClass(classLoader);
        XposedBridge.hookAllConstructors(videoViewContainerClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length < 2) return;
                var view = (View) param.thisObject;
                var context = view.getContext();
                var surface = (ViewGroup) view.findViewById(Utils.getID("invisible_press_surface", "id"));
                var controlFrame = surface.getChildAt(0);
                surface.removeViewAt(0);
                var linearLayout = new LinearLayout(context);
                surface.addView(linearLayout);
                linearLayout.addView(controlFrame);
                var prevBtn = new ImageView(context);
                var layoutParams = new LinearLayout.LayoutParams(Utils.dipToPixels(42), Utils.dipToPixels(32));
                layoutParams.gravity = Gravity.CENTER;
                prevBtn.setLayoutParams(layoutParams);
                var drawable = context.getDrawable(ResId.drawable.preview_eye);
                drawable.setTint(Color.WHITE);
                prevBtn.setImageDrawable(drawable);
                prevBtn.setPadding(Utils.dipToPixels(4), Utils.dipToPixels(4), Utils.dipToPixels(4), Utils.dipToPixels(4));
                prevBtn.setBackground(DesignUtils.getDrawableByName("download_background"));
                prevBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
                linearLayout.addView(prevBtn);
                prevBtn.setOnClickListener((v) -> {
                    var objmessage = XposedHelpers.callMethod(param.thisObject, "getFMessage");
                    var id = new FMessageWpp(objmessage).getRowId();
                    var userJid = WppCore.getCurrentRawJID();
                    startPlayer(id, context, userJid != null && userJid.contains("@newsletter"));
                });
            }
        });

        var imageViewContainerClass = Unobfuscator.loadImageVewContainerClass(classLoader);
        XposedBridge.hookAllConstructors(imageViewContainerClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length < 2) return;
                var view = (View) param.thisObject;
                var context = view.getContext();


                ViewGroup mediaContainer = view.findViewById(Utils.getID("media_container", "id"));
                ViewGroup controlFrame = view.findViewById(Utils.getID("control_frame", "id"));

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                ));
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                linearLayout.setBackground(DesignUtils.getDrawableByName("fragment_transparent_divider"));
                mediaContainer.removeView(controlFrame);
                linearLayout.addView(controlFrame);
                mediaContainer.addView(linearLayout);
                var prevBtn = new ImageView(context);
                var layoutParams2 = new LinearLayout.LayoutParams(Utils.dipToPixels(42), Utils.dipToPixels(32));
                layoutParams2.gravity = Gravity.CENTER;
                layoutParams2.topMargin = Utils.dipToPixels(8);
                prevBtn.setLayoutParams(layoutParams2);
                var drawable = context.getDrawable(ResId.drawable.preview_eye);
                drawable.setTint(Color.WHITE);
                prevBtn.setImageDrawable(drawable);
                prevBtn.setPadding(Utils.dipToPixels(4), Utils.dipToPixels(4), Utils.dipToPixels(4), Utils.dipToPixels(4));
                prevBtn.setBackground(DesignUtils.getDrawableByName("download_background"));
                prevBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
                linearLayout.addView(prevBtn);
                prevBtn.setVisibility(controlFrame.getVisibility());
                controlFrame.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                    if (prevBtn.getVisibility() != controlFrame.getVisibility())
                        prevBtn.setVisibility(controlFrame.getVisibility());
                });

                prevBtn.setOnClickListener((v) -> {
                    var objmessage = XposedHelpers.callMethod(param.thisObject, "getFMessage");
                    var id = new FMessageWpp(objmessage).getRowId();
                    var userJid = WppCore.getCurrentRawJID();
                    startPlayer(id, context, userJid != null && userJid.contains("@newsletter"));
                });

            }
        });


    }

    /**
     * @noinspection ResultOfMethodCallIgnored
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void startPlayer(long id, Context context, boolean isNewsletter) {
        var executor = Executors.newSingleThreadExecutor();
        try {
            Cursor cursor0 = MessageStore.getInstance().getDatabase().rawQuery(String.format(Locale.ENGLISH, "SELECT message_url,mime_type,hex(media_key),direct_path FROM message_media WHERE message_row_id =\"%d\"", id), null);
            if (cursor0 != null && cursor0.getCount() > 0) {
                cursor0.moveToFirst();
                AtomicReference<String> url = new AtomicReference<>(cursor0.getString(0));
                String mine_type = cursor0.getString(1);
                String media_key = cursor0.getString(2);
                String direct_path = cursor0.getString(3);
                cursor0.close();
                if (isNewsletter) {
                    url.set("https://mmg.whatsapp.net" + direct_path);
                }
                var alertDialog = new AlertDialog.Builder(context);
                FrameLayout frameLayout = new FrameLayout(context);
                var webView = new WebView(context);
                webView.getSettings().setAllowFileAccess(true);
                webView.getSettings().setSupportZoom(true);
                webView.getSettings().setBuiltInZoomControls(true);
                webView.getSettings().setDisplayZoomControls(false);
                webView.getSettings().setJavaScriptEnabled(true);
                webView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.loadDataWithBaseURL(null, HTML_LOADING.replace("$loading", context.getString(ResId.string.loading)), "text/html", "UTF-8", null);
                frameLayout.addView(webView);
                alertDialog.setView(frameLayout);
                alertDialog.setOnDismissListener(dialog1 -> {
                    if (filePath != null && filePath.exists()) {
                        filePath.delete();
                    }
                    if (!executor.isShutdown())
                        executor.shutdownNow();
                });
                dialog = alertDialog.create();
                dialog.show();
                executor.execute(() -> decodeMedia(url.get(), media_key, mine_type, executor, webView, isNewsletter));
            }
        } catch (Exception e) {
            logDebug(e);
            Utils.showToast(e.getMessage(), Toast.LENGTH_LONG);
            if (dialog != null && dialog.isShowing())
                dialog.dismiss();
            if (!executor.isShutdown())
                executor.shutdownNow();
        }
    }

    /**
     * Decodifica a mídia.
     *
     * @param url          A URL da mídia.
     * @param mediaKey     A chave de mídia.
     * @param mimeType     O tipo MIME da mídia.
     * @param executor     O executor de tarefas.
     * @param webView      A visualização da web.
     * @param isNewsletter Indica se a mensagem é de um boletim informativo.
     */
    private void decodeMedia(String url, String mediaKey, String mimeType, ExecutorService executor, WebView webView, boolean isNewsletter) {
        try {
            String fileExtension = mimeType.startsWith("image") ? ".jpg" : ".mp4";
            filePath = new File(Utils.getApplication().getCacheDir(), "mediapreview" + fileExtension);

            byte[] encryptedData = Objects.requireNonNull(new OkHttpClient.Builder()
                    .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                            .addHeader("User-Agent", "Chrome/117.0.5938.150")
                            .build()))
                    .build()
                    .newCall(new Request.Builder().url(url).build())
                    .execute()
                    .body()).source().readByteArray();

            if (filePath.exists()) {
                //noinspection ResultOfMethodCallIgnored
                filePath.delete();
            }

            byte[] decryptedData = isNewsletter ? encryptedData : decryptMedia(encryptedData, mediaKey, mimeType);
            assert decryptedData != null;

            try (BufferedSink bufferedSink = Okio.buffer(Okio.sink(filePath))) {
                bufferedSink.write(decryptedData);
            }

            webView.post(() -> {
                String fileUrl = "file://" + filePath.getAbsolutePath();
                if (mimeType.contains("image")) {
                    webView.loadDataWithBaseURL(null, HTML_IMAGE.replace("$url", fileUrl), "text/html", "UTF-8", null);
                } else {
                    webView.loadDataWithBaseURL(null, HTML_VIDEO.replace("$url", fileUrl), "text/html", "UTF-8", null);
                }
            });
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException) {
                logDebug(e.getCause());
                Utils.showToast(Objects.requireNonNull(e.getCause()).getMessage(), Toast.LENGTH_LONG);
            } else {
                logDebug(e);
                Utils.showToast(e.getMessage(), Toast.LENGTH_LONG);
            }
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        } finally {
            if (!executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
    }


    /**
     * Descriptografa a mídia.
     *
     * @param encryptedData Os dados criptografados.
     * @param mediaKey      A chave de mídia.
     * @param mimeType      O tipo MIME da mídia.
     * @return Os dados descriptografados.
     * @throws Exception Se ocorrer um erro durante a descriptografia.
     */
    private byte[] decryptMedia(byte[] encryptedData, String mediaKey, String mimeType) throws Exception {
        if (mediaKey.length() % 2 != 0 || mediaKey.length() != 64) {
            throw new IllegalArgumentException("Invalid media key.");
        }

        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 64; i += 2) {
            keyBytes[i / 2] = (byte) ((Character.digit(mediaKey.charAt(i), 16) << 4) + Character.digit(mediaKey.charAt(i + 1), 16));
        }

        byte[] typeKey = MEDIA_KEYS.getOrDefault(mimeType, MEDIA_KEYS.get("document"));
        byte[] derivedKey = HKDF.createFor(3).deriveSecrets(keyBytes, typeKey, 112);
        byte[] iv = Arrays.copyOfRange(derivedKey, 0, 16);
        byte[] aesKey = Arrays.copyOfRange(derivedKey, 16, 48);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(Arrays.copyOfRange(encryptedData, 0, encryptedData.length - 10));
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Media Preview";
    }
}
