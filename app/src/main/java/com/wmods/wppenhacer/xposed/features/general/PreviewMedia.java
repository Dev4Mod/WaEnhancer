package com.wmods.wppenhacer.xposed.features.general;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.DesignUtils;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.ResId;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.utils.HKDF;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

public class PreviewMedia extends Feature {

    private static final String HTML_LOADING = "<!DOCTYPE html><html><head> <meta charset=\"UTF-8\"> <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"> <title>Loading</title> <style> body { display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background-color: #f0f0f0; font-family: Arial, sans-serif; } .loader { display: flex; align-items: center; } .spinner { width: 40px; height: 40px; border: 4px solid rgba(0, 0, 0, 0.1); border-top: 4px solid #000; border-radius: 50%; animation: spin 1s linear infinite; } @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } } .text { margin-left: 10px; font-size: 18px; } </style></head><body> <div class=\"loader\"> <div class=\"spinner\"></div> <div class=\"text\">$loading</div> </div></body></html>";
    private static final String HTML_VIDEO = "<!DOCTYPE html><html><head> <meta charset=\"UTF-8\"> <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"> <title>Player de Vídeo</title> <style> body { display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background-color: #f0f0f0; font-family: Arial, sans-serif; } .video-container { text-align: center; } video { width: 100%; height: auto; } </style></head><body> <div class=\"video-container\"> <video controls> <source src=\"$url\" type=\"video/mp4\"> Browser not supported. </video> </div></body></html>";
    private static final String HTML_IMAGE = "<!DOCTYPE html><html><head> <meta charset=\"UTF-8\"> <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"> <title>Image</title> <style> body { display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background-color: #f0f0f0; font-family: Arial, sans-serif; } .full-screen-image { width: 100%; height: auto;} </style></head><body> <img src=\"$url\" class=\"full-screen-image\"></body></html>";
    private File filePath;
    private AlertDialog dialog;

    public PreviewMedia(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("media_preview", true)) return;

        var getFieldIdMessage = Unobfuscator.loadSetEditMessageField(loader);
        var videoViewContainerClass = Unobfuscator.loadVideoViewContainerClass(loader);
        XposedBridge.hookAllConstructors(videoViewContainerClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length < 2) return;
                var userJid = WppCore.getCurrentRawJID();
                if (userJid != null && userJid.contains("@newsletter")) return;
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
                    var id = (long) ReflectionUtils.getField(getFieldIdMessage, objmessage);
                    startPlayer(id, context);
                });
            }
        });

        var imageViewContainerClass = Unobfuscator.loadImageVewContainerClass(loader);
        XposedBridge.hookAllConstructors(imageViewContainerClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length < 2) return;
                var view = (View) param.thisObject;
                var context = view.getContext();
                var userJid = WppCore.getCurrentRawJID();
                if (userJid != null && userJid.contains("@newsletter")) return;

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
                    var id = (long) ReflectionUtils.getField(getFieldIdMessage, objmessage);
                    startPlayer(id, context);
                });

            }
        });


    }

    @SuppressLint("SetJavaScriptEnabled")
    private void startPlayer(long id, Context context) {
        var executor = Executors.newSingleThreadExecutor();
        try {
            Cursor cursor0 = MessageStore.database.getReadableDatabase().rawQuery(String.format(Locale.ENGLISH, "SELECT message_url,mime_type,hex(media_key) FROM message_media WHERE message_row_id =\"%d\"", id), null);
            if (cursor0 != null && cursor0.getCount() > 0) {
                cursor0.moveToFirst();
                String url = cursor0.getString(0);
                String mine_type = cursor0.getString(1);
                String media_key = cursor0.getString(2);
                cursor0.close();
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
                executor.execute(() -> decodeMedia(url, media_key, mine_type, executor, webView));
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

    private void decodeMedia(String url, String mediaKey, String mineType, ExecutorService executor, WebView webView) {
        try {
            String s = mineType.startsWith("image") ? ".jpg" : ".mp4";
            filePath = new File(Utils.getApplication().getCacheDir(), "mediapreview" + s);
            byte[] arr_b = new OkHttpClient.Builder().addInterceptor((chain) -> chain.proceed(chain.request().newBuilder().addHeader("User-Agent", "Chrome/117.0.5938.150").build())).build().newCall(new Request.Builder().url(url).build()).execute().body().source().readByteArray();
            if (filePath.exists()) {
                filePath.delete();
            }
            byte[] arr_b1 = decryptFile(arr_b, mediaKey, mineType);
            BufferedSink bufferedSink0 = Okio.buffer(Okio.sink(filePath));
            bufferedSink0.write(arr_b1);
            bufferedSink0.close();
            executor.shutdown();
            webView.post(() -> {
                if (mineType.contains("image")) {
                    webView.loadDataWithBaseURL(null, HTML_IMAGE.replace("$url", "file://" + filePath.getAbsolutePath()), "text/html", "UTF-8", null);
                } else {
                    webView.loadDataWithBaseURL(null, HTML_VIDEO.replace("$url", "file://" + filePath.getAbsolutePath()), "text/html", "UTF-8", null);
                }
            });
        } catch (Exception e) {
            Utils.showToast(e.getMessage(), Toast.LENGTH_LONG);
            if (dialog != null && dialog.isShowing())
                dialog.dismiss();
            if (!executor.isShutdown())
                executor.shutdownNow();
        }
    }

    static HashMap<String, byte[]> keys = new HashMap<>();

    static {
        keys.put("image", "WhatsApp Image Keys".getBytes());
        keys.put("video", "WhatsApp Video Keys".getBytes());
        keys.put("audio", "WhatsApp Audio Keys".getBytes());
        keys.put("document", "WhatsApp Document Keys".getBytes());
        keys.put("image/webp", "WhatsApp Image Keys".getBytes());
        keys.put("image/jpeg", "WhatsApp Image Keys".getBytes());
        keys.put("image/png", "WhatsApp Image Keys".getBytes());
        keys.put("video/mp4", "WhatsApp Video Keys".getBytes());
        keys.put("audio/aac", "WhatsApp Audio Keys".getBytes());
        keys.put("audio/ogg", "WhatsApp Audio Keys".getBytes());
        keys.put("audio/wav", "WhatsApp Audio Keys".getBytes());
    }


    private byte[] decryptFile(byte[] arr_b, String mediaKey, String mineType) throws Exception {
        int v = mediaKey.length();
        byte[] arr_b1 = new byte[v / 2];
        for (int v1 = 0; v1 < v; v1 += 2) {
            arr_b1[v1 / 2] = (byte) (Character.digit(mediaKey.charAt(v1 + 1), 16) + (Character.digit(mediaKey.charAt(v1), 16) << 4));
        }
        if (v / 2 != 0x20) {
            return null;
        }
        byte[] arr_b2 = keys.get(mineType);
        byte[] arr_b3 = HKDF.createFor(3).deriveSecrets(arr_b1, arr_b2, 0x70);
        byte[] arr_b4 = Arrays.copyOfRange(arr_b3, 0, 16);
        byte[] arr_b5 = Arrays.copyOfRange(arr_b3, 16, 0x30);
        byte[] arr_b6 = Arrays.copyOfRange(arr_b, 0, arr_b.length - 10);
        SecretKeySpec secretKeySpec0 = new SecretKeySpec(arr_b5, "AES");
        Cipher cipher0 = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher0.init(2, secretKeySpec0, new IvParameterSpec(arr_b4));
        return cipher0.doFinal(arr_b6);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Preview Media";
    }
}
